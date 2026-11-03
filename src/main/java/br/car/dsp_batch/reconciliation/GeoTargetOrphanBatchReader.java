package br.car.dsp_batch.reconciliation;

import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.sync.WatermarkTableSpec;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyset pagination over one hash bucket of geo-target rows with valid geometry.
 */
public class GeoTargetOrphanBatchReader {

    public record TargetRow(Object rawId, String bbox) {
    }

    private final JdbcTemplate geoTargetJdbc;
    private final WatermarkTableSpec spec;
    private final Integer hashBucket;
    private final Integer hashGridSize;

    public GeoTargetOrphanBatchReader(JdbcTemplate geoTargetJdbc,
                                      WatermarkTableSpec spec,
                                      Integer hashBucket,
                                      Integer hashGridSize) {
        this.geoTargetJdbc = geoTargetJdbc;
        this.spec = spec;
        this.hashBucket = hashBucket;
        this.hashGridSize = hashGridSize;
    }

    public List<TargetRow> fetchNextBatch(Object lastRawKey, int limit) {
        String pk = spec.geoTargetPrimaryKey();
        String geom = spec.geoTargetGeometryColumn();
        String table = spec.geoTargetTable();
        int srid = spec.srid();
        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String geom2d = GeometrySql.force2d(geom);

        StringBuilder partitionFilter = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (hashBucket != null && hashGridSize != null) {
            partitionFilter.append(" AND mod(abs(hashtext(").append(pk).append("::text)), ?) = ?");
            params.add(hashGridSize);
            params.add(hashBucket);
        }
        if (lastRawKey != null) {
            partitionFilter.append(" AND ").append(pk).append("::text > ?");
            params.add(lastRawKey.toString());
        }

        String innerSelect = String.format(
                "SELECT %s AS raw_pk, %s::text AS pk_text, "
                        + "ST_Transform(ST_Envelope(ST_SetSRID(%s, %d)), 3857) AS env3857 "
                        + "FROM %s WHERE %s%s",
                pk, pk, geom2d, srid, table, validGeomFilter, partitionFilter);

        String sql = String.format(
                "SELECT raw_pk, "
                        + "ST_XMin(env3857) AS minx, ST_YMin(env3857) AS miny, "
                        + "ST_XMax(env3857) AS maxx, ST_YMax(env3857) AS maxy "
                        + "FROM (%s) inner_q ORDER BY pk_text LIMIT ?",
                innerSelect);

        params.add(limit);

        List<TargetRow> rows = new ArrayList<>();
        geoTargetJdbc.query(sql, rs -> {
            Object rawId = rs.getObject("raw_pk");
            String bbox = OrphanReconciliationService.formatBbox(
                    rs.getDouble("minx"),
                    rs.getDouble("miny"),
                    rs.getDouble("maxx"),
                    rs.getDouble("maxy"));
            rows.add(new TargetRow(rawId, bbox));
        }, params.toArray());
        return rows;
    }
}
