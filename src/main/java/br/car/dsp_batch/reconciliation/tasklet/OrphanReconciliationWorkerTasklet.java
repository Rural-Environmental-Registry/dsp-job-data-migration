package br.car.dsp_batch.reconciliation.tasklet;

import br.car.dsp_batch.reconciliation.GeoTargetOrphanBatchReader;
import br.car.dsp_batch.reconciliation.OrphanOriginProbe;
import br.car.dsp_batch.reconciliation.OrphanReconciliationService;
import br.car.dsp_batch.reconciliation.ReconciliationProperties;
import br.car.dsp_batch.reconciliation.partitioner.GeoTargetReconciliationPartitioner;
import br.car.dsp_batch.sync.WatermarkTableSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class OrphanReconciliationWorkerTasklet implements Tasklet {

    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate businessTargetJdbc;
    private final JdbcTemplate geoTargetJdbc;
    private final WatermarkTableSpec spec;
    private final OrphanReconciliationService reconciliationService;
    private final OrphanOriginProbe originProbe;
    private final ReconciliationProperties properties;
    private final Integer hashBucket;
    private final Integer hashGridSize;

    public OrphanReconciliationWorkerTasklet(JdbcTemplate sourceJdbc,
                                             JdbcTemplate businessTargetJdbc,
                                             JdbcTemplate geoTargetJdbc,
                                             WatermarkTableSpec spec,
                                             OrphanReconciliationService reconciliationService,
                                             OrphanOriginProbe originProbe,
                                             ReconciliationProperties properties,
                                             Integer hashBucket,
                                             Integer hashGridSize) {
        this.sourceJdbc = sourceJdbc;
        this.businessTargetJdbc = businessTargetJdbc;
        this.geoTargetJdbc = geoTargetJdbc;
        this.spec = spec;
        this.reconciliationService = reconciliationService;
        this.originProbe = originProbe;
        this.properties = properties;
        this.hashBucket = hashBucket;
        this.hashGridSize = hashGridSize;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        ExecutionContext jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        GeoTargetOrphanBatchReader reader = new GeoTargetOrphanBatchReader(
                geoTargetJdbc, spec, hashBucket, hashGridSize);

        Object lastKey = null;
        int batchSize = properties.getBatchSize();
        int totalOrphans = 0;

        while (true) {
            List<GeoTargetOrphanBatchReader.TargetRow> batch = reader.fetchNextBatch(lastKey, batchSize);
            if (batch.isEmpty()) {
                break;
            }

            List<Object> candidateIds = batch.stream()
                    .map(GeoTargetOrphanBatchReader.TargetRow::rawId)
                    .toList();
            Set<Object> existingOnSource = originProbe.probeExistingNormalizedIds(
                    sourceJdbc, spec, candidateIds);

            List<Object> orphanIds = new ArrayList<>();
            Map<Object, String> bboxesById = new HashMap<>();
            for (GeoTargetOrphanBatchReader.TargetRow row : batch) {
                Object normalized = reconciliationService.normalizeId(row.rawId());
                if (!existingOnSource.contains(normalized)) {
                    orphanIds.add(row.rawId());
                    bboxesById.put(normalized, row.bbox());
                }
            }

            if (!orphanIds.isEmpty()) {
                reconciliationService.reconcileOrphans(
                        geoTargetJdbc,
                        businessTargetJdbc,
                        spec,
                        orphanIds,
                        bboxesById,
                        jobContext);
                totalOrphans += orphanIds.size();
            }

            lastKey = batch.get(batch.size() - 1).rawId();
        }

        log.info("Reconciliation worker for syncKey={} removed {} orphan(s)", spec.syncKey(), totalOrphans);
        return RepeatStatus.FINISHED;
    }

    public static Integer hashBucket(ExecutionContext stepContext) {
        return stepContext.containsKey(GeoTargetReconciliationPartitioner.HASH_BUCKET)
                ? stepContext.getInt(GeoTargetReconciliationPartitioner.HASH_BUCKET)
                : null;
    }

    public static Integer hashGridSize(ExecutionContext stepContext) {
        return stepContext.containsKey(GeoTargetReconciliationPartitioner.HASH_GRID_SIZE)
                ? stepContext.getInt(GeoTargetReconciliationPartitioner.HASH_GRID_SIZE)
                : null;
    }
}
