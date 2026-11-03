package br.car.dsp_batch.reconciliation.tasklet;

import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.reconciliation.ReconciliationProperties;
import br.car.dsp_batch.sync.ReconciliationContextKeys;
import br.car.dsp_batch.sync.WatermarkContextKeys;
import br.car.dsp_batch.sync.WatermarkTableSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Light validation and geo-target empty check before orphan reconciliation.
 */
@Slf4j
public class ReconciliationSetupTasklet implements Tasklet {

    private final JdbcTemplate geoTargetJdbc;
    private final WatermarkTableSpec spec;
    private final ReconciliationProperties properties;

    public ReconciliationSetupTasklet(JdbcTemplate geoTargetJdbc,
                                      WatermarkTableSpec spec,
                                      ReconciliationProperties properties) {
        this.geoTargetJdbc = geoTargetJdbc;
        this.spec = spec;
        this.properties = properties;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (spec.syncKey() == null || spec.syncKey().isBlank()) {
            throw new IllegalArgumentException("Reconciliation requires sync-key for " + spec.sourceTable());
        }

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        jobContext.putString(WatermarkContextKeys.SYNC_KEY, spec.syncKey());
        jobContext.putString(WatermarkContextKeys.SOURCE_TABLE, spec.sourceTable());
        jobContext.put(ReconciliationContextKeys.ORPHAN_RECONCILIATION, true);
        if (spec.layerName() != null) {
            jobContext.putString(WatermarkContextKeys.LAYER_NAME, spec.layerName());
        }

        long rowCount = countGeoTargetRows();
        log.info("Reconciliation setup for syncKey={} geo-target rows with valid geom={}",
                spec.syncKey(), rowCount);

        boolean targetEmpty = rowCount == 0;
        jobContext.put(ReconciliationContextKeys.TARGET_EMPTY, targetEmpty);
        if (targetEmpty && properties.isSkipWhenTargetEmpty()) {
            log.info("Geo-target empty — skipping orphan reconciliation for {}", spec.syncKey());
        }
        return RepeatStatus.FINISHED;
    }

    private long countGeoTargetRows() {
        String geom = spec.geoTargetGeometryColumn();
        String validGeom = GeometrySql.validNonEmptyPredicate(geom);
        String sql = String.format(
                "SELECT COUNT(*) FROM %s WHERE %s",
                spec.geoTargetTable(),
                validGeom);
        Long count = geoTargetJdbc.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
