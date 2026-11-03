package br.car.dsp_batch.reconciliation;

import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.WatermarkContextKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReconciliationCommitListener implements JobExecutionListener {

    private final SyncStateRepository syncStateRepository;

    public ReconciliationCommitListener(SyncStateRepository syncStateRepository) {
        this.syncStateRepository = syncStateRepository;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            log.warn(
                    "Reconciliation job {} finished with status={} — last_orphan_check_at not updated",
                    jobExecution.getJobInstance().getJobName(),
                    jobExecution.getStatus());
            return;
        }

        var context = jobExecution.getExecutionContext();
        String syncKey = context.getString(WatermarkContextKeys.SYNC_KEY, null);
        if (syncKey == null || syncKey.isBlank()) {
            return;
        }
        String sourceTable = context.getString(
                WatermarkContextKeys.SOURCE_TABLE,
                syncKey);
        syncStateRepository.markOrphanCheck(syncKey, sourceTable);
        log.info("Orphan check timestamp updated for syncKey={}", syncKey);
    }
}
