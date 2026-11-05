package br.car.dsp_batch.geofile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.layer.config.LayerConfig;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.RowCallbackHandler;

/**
 * Turns {@code requires_s3_file_regeneration} on for the territories a migration run touched,
 * so {@code rer-dsp-job-geo-file-generation} republishes their download files.
 *
 * <p>Set-based on purpose: the affected rows can be millions and the flag is idempotent,
 * so a single UPDATE over the watermark window costs less than carrying ids through the job.
 */
@Slf4j
@Repository
public class GeoFileRegenerationFlagRepository {

    private final JdbcTemplate targetJdbcTemplate;
    private final JdbcTemplate geoTargetJdbcTemplate;

    public GeoFileRegenerationFlagRepository(
            @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate,
            @Qualifier("geoTargetJdbcTemplate") JdbcTemplate geoTargetJdbcTemplate) {
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.geoTargetJdbcTemplate = geoTargetJdbcTemplate;
    }

    /**
     * Flags the rows of a territory table changed after {@code previousWatermark}.
     * A null watermark means first load — every row is flagged.
     */
    public int markChangedTerritories(String territoryTable, Instant previousWatermark) {
        if (previousWatermark == null) {
            return targetJdbcTemplate.update(
                    "UPDATE " + territoryTable + " SET requires_s3_file_regeneration = TRUE");
        }
        OffsetDateTime since = toOffsetDateTime(previousWatermark);
        return targetJdbcTemplate.update(
                "UPDATE " + territoryTable
                        + " SET requires_s3_file_regeneration = TRUE"
                        + " WHERE created_at > ? OR updated_at > ?",
                since,
                since);
    }

    /**
     * Flags the level 3 parents of the AOIs changed after {@code previousWatermark}, and the
     * level 2 parents of those level 3s — an AOI edit changes both territorial downloads.
     */
    public AreaOfInterestFlagResult markParentsOfChangedAreasOfInterest(
            String areaOfInterestTable,
            String territoryLevel3Column,
            String territoryLevel3Table,
            String territoryLevel2Table,
            Instant previousWatermark) {
        String changedLevel3Ids = "SELECT DISTINCT aoi." + territoryLevel3Column
                + " FROM " + areaOfInterestTable + " aoi"
                + " WHERE aoi." + territoryLevel3Column + " IS NOT NULL";
        Object[] level3Args;
        if (previousWatermark == null) {
            level3Args = new Object[0];
        } else {
            OffsetDateTime since = toOffsetDateTime(previousWatermark);
            changedLevel3Ids += " AND (aoi.created_at > ? OR aoi.updated_at > ?)";
            level3Args = new Object[]{since, since};
        }

        int level3Flagged = targetJdbcTemplate.update(
                "UPDATE " + territoryLevel3Table
                        + " SET requires_s3_file_regeneration = TRUE"
                        + " WHERE id IN (" + changedLevel3Ids + ")",
                level3Args);
        int level2Flagged = targetJdbcTemplate.update(
                "UPDATE " + territoryLevel2Table
                        + " SET requires_s3_file_regeneration = TRUE"
                        + " WHERE id IN ("
                        + "  SELECT DISTINCT t3.parent_id FROM " + territoryLevel3Table + " t3"
                        + "  WHERE t3.parent_id IS NOT NULL AND t3.id IN (" + changedLevel3Ids + ")"
                        + ")",
                level3Args);
        return new AreaOfInterestFlagResult(level2Flagged, level3Flagged);
    }

    /**
     * Flags explicit level 3 territories (e.g. those that lost AOIs) and their level 2 parents.
     */
    public DepartedTerritoryFlagResult markLevel3TerritoriesAndParents(
            Collection<String> level3Ids,
            String territoryLevel3Table,
            String territoryLevel2Table) {
        if (level3Ids == null || level3Ids.isEmpty()) {
            return new DepartedTerritoryFlagResult(0, 0);
        }

        String placeholders = level3Ids.stream().map(id -> "?").collect(Collectors.joining(","));
        Object[] args = level3Ids.toArray();

        int level3Flagged = targetJdbcTemplate.update(
                "UPDATE " + territoryLevel3Table
                        + " SET requires_s3_file_regeneration = TRUE"
                        + " WHERE id IN (" + placeholders + ")",
                args);
        int level2Flagged = targetJdbcTemplate.update(
                "UPDATE " + territoryLevel2Table
                        + " SET requires_s3_file_regeneration = TRUE"
                        + " WHERE id IN ("
                        + "  SELECT DISTINCT t3.parent_id FROM " + territoryLevel3Table + " t3"
                        + "  WHERE t3.parent_id IS NOT NULL AND t3.id IN (" + placeholders + ")"
                        + ")",
                args);
        return new DepartedTerritoryFlagResult(level2Flagged, level3Flagged);
    }

    /**
     * Flags level 3 (and their level 2 parents) whose linked layer features changed on the
     * geo-target since {@code previousWatermark}. Deletions are handled via
     * {@link #markLevel3TerritoriesAndParents} and {@code departedLevel3Ids} on the job context.
     */
    public DepartedTerritoryFlagResult markTerritoriesOfChangedLayerFeatures(
            String layerTable,
            String areaOfInterestTable,
            String territoryLevel3Table,
            String territoryLevel2Table,
            Instant previousWatermark) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT aoi." + AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN
                        + " FROM " + layerTable + " l"
                        + " INNER JOIN " + areaOfInterestTable + " aoi ON aoi."
                        + AreaOfInterestConfig.ID_COLUMN + " = l."
                        + LayerConfig.AREA_OF_INTEREST_ID_COLUMN
                        + " WHERE aoi." + AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN
                        + " IS NOT NULL");
        List<Object> args = new ArrayList<>();
        if (previousWatermark != null) {
            OffsetDateTime since = toOffsetDateTime(previousWatermark);
            sql.append(" AND (l.").append(LayerConfig.CREATED_AT_COLUMN).append(" > ?");
            args.add(since);
            sql.append(" OR l.").append(LayerConfig.UPDATED_AT_COLUMN).append(" > ?)");
            args.add(since);
        }

        Set<String> level3Ids = new HashSet<>();
        RowCallbackHandler handler = rs -> {
            Object value = rs.getObject(1);
            if (value != null) {
                level3Ids.add(value.toString().trim());
            }
        };
        if (args.isEmpty()) {
            geoTargetJdbcTemplate.query(sql.toString(), handler);
        } else {
            geoTargetJdbcTemplate.query(sql.toString(), handler, args.toArray());
        }
        return markLevel3TerritoriesAndParents(level3Ids, territoryLevel3Table, territoryLevel2Table);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /** Rows flagged per level by an AOI run. */
    public record AreaOfInterestFlagResult(int level2Flagged, int level3Flagged) {
    }

    /** Rows flagged for level 3 territories that lost AOIs during the run. */
    public record DepartedTerritoryFlagResult(int level2Flagged, int level3Flagged) {
    }
}
