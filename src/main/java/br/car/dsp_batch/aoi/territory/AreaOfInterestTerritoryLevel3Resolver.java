package br.car.dsp_batch.aoi.territory;

import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.layer.introspection.SchemaIntrospectionService;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resolves {@code territory_level_3_id} from AOI geometry against level-3 polygons on the geo-target.
 */
@Slf4j
@Service
public class AreaOfInterestTerritoryLevel3Resolver {

    private static final QualifiedTable LEVEL_3_TABLE = new QualifiedTable("dsp", "territory_level_3");
    private static final String GEOM_COLUMN = "geom";
    private static final String ID_COLUMN = "id";

    private final JdbcTemplate geoTargetJdbcTemplate;
    private final SchemaIntrospectionService schemaIntrospectionService;

    public AreaOfInterestTerritoryLevel3Resolver(
            @Qualifier("geoTargetJdbcTemplate") JdbcTemplate geoTargetJdbcTemplate,
            SchemaIntrospectionService schemaIntrospectionService) {
        this.geoTargetJdbcTemplate = geoTargetJdbcTemplate;
        this.schemaIntrospectionService = schemaIntrospectionService;
    }

    /**
     * Picks the level-3 territory with the largest share of the AOI area (intersection / AOI area).
     * Reads the AOI polygon from {@code aoiTable} on the geo-target (column {@code geom}).
     */
    public Optional<String> resolve(String areaOfInterestId, QualifiedTable aoiTable) {
        if (areaOfInterestId == null || areaOfInterestId.isBlank()) {
            return Optional.empty();
        }
        if (!schemaIntrospectionService.tableExists(geoTargetJdbcTemplate, LEVEL_3_TABLE)) {
            throw new IllegalStateException(
                    "Geo-target table " + LEVEL_3_TABLE.qualified()
                            + " not found. Run admin-unit level-3 migration before AOI.");
        }
        if (!schemaIntrospectionService.tableExists(geoTargetJdbcTemplate, aoiTable)) {
            throw new IllegalStateException(
                    "Geo-target table " + aoiTable.qualified() + " not found for AOI territory resolution.");
        }

        String l3GeomExpr = "l3." + GEOM_COLUMN;
        String l3GeomInAoiSrid = "public.ST_Transform(" + l3GeomExpr + ", public.ST_SRID(aoi.geom))";
        String aoiValid = GeometrySql.validNonEmptyPredicate("a." + GEOM_COLUMN);
        String l3Valid = GeometrySql.validNonEmptyPredicate(l3GeomExpr);

        String sql = """
                WITH aoi AS (
                    SELECT a.%s AS geom,
                           public.ST_Area(a.%s) AS aoi_area
                    FROM %s a
                    WHERE a.%s = ?
                      AND %s
                )
                SELECT l3.%s AS territory_level_3_id
                FROM %s l3
                CROSS JOIN aoi
                WHERE aoi.aoi_area > 0
                  AND %s
                  AND public.ST_Intersects(aoi.geom, %s)
                ORDER BY (
                    public.ST_Area(public.ST_Intersection(aoi.geom, %s))
                    / NULLIF(aoi.aoi_area, 0)
                ) DESC NULLS LAST,
                l3.%s ASC
                LIMIT 1
                """.formatted(
                GEOM_COLUMN,
                GEOM_COLUMN,
                aoiTable.qualified(),
                ID_COLUMN,
                aoiValid,
                ID_COLUMN,
                LEVEL_3_TABLE.qualified(),
                l3Valid,
                l3GeomInAoiSrid,
                l3GeomInAoiSrid,
                ID_COLUMN
        );

        try {
            List<String> ids = geoTargetJdbcTemplate.query(
                    sql,
                    (rs, rowNum) -> rs.getString("territory_level_3_id"),
                    areaOfInterestId.trim());
            return ids.stream().findFirst();
        } catch (DataAccessException ex) {
            throw new IllegalStateException(
                    "Failed to resolve territory_level_3_id from geometry: " + ex.getMessage(),
                    ex);
        }
    }
}
