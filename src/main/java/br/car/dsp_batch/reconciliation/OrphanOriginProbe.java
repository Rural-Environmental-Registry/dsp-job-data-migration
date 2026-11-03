package br.car.dsp_batch.reconciliation;

import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.sync.WatermarkTableSpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only probe on the source for PKs that still exist and match eligibility rules.
 */
@Component
public class OrphanOriginProbe {

    private final OrphanReconciliationService reconciliationService;

    public OrphanOriginProbe(OrphanReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    public Set<Object> probeExistingNormalizedIds(JdbcTemplate sourceJdbc,
                                                  WatermarkTableSpec spec,
                                                  Collection<Object> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Set.of();
        }
        Set<Object> found = new HashSet<>();
        List<Object> list = List.copyOf(candidateIds);
        int chunkSize = Math.max(1, 500);
        for (int i = 0; i < list.size(); i += chunkSize) {
            List<Object> chunk = list.subList(i, Math.min(i + chunkSize, list.size()));
            found.addAll(queryChunk(sourceJdbc, spec, chunk));
        }
        return found;
    }

    private Set<Object> queryChunk(JdbcTemplate sourceJdbc,
                                   WatermarkTableSpec spec,
                                   List<Object> chunk) {
        String pk = spec.sourcePrimaryKey();
        String geom = spec.sourceGeometryColumn();
        String table = spec.sourceTable();
        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String configWhere = spec.whereClause();
        boolean hasConfigWhere = configWhere != null
                && !configWhere.isBlank()
                && !"1=1".equals(configWhere.trim());

        String pkAsText = ReconciliationPrimaryKeySql.asComparableText(pk);
        String placeholders = ReconciliationPrimaryKeySql.inPlaceholders(chunk.size());
        StringBuilder sql = new StringBuilder("SELECT ").append(pk)
                .append(" FROM ").append(table)
                .append(" WHERE ").append(pkAsText).append(" IN (").append(placeholders).append(")")
                .append(" AND ").append(validGeomFilter);
        if (hasConfigWhere) {
            sql.append(" AND (").append(configWhere).append(")");
        }

        Object[] bindIds = ReconciliationPrimaryKeySql.bindNormalized(
                chunk, reconciliationService::normalizeId);

        Set<Object> ids = new HashSet<>();
        sourceJdbc.query(sql.toString(), rs -> {
            ids.add(reconciliationService.normalizeId(rs.getObject(pk)));
        }, bindIds);
        return ids;
    }
}
