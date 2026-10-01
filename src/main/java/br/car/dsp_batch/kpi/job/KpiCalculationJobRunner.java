package br.car.dsp_batch.kpi.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Launches KPI calculation after AOI and layer migration jobs.
 */
@Slf4j
@Component
@Order(3)
public class KpiCalculationJobRunner implements CommandLineRunner {

    private final JobLauncher jobLauncher;
    private final KpiCalculationJobFactory jobFactory;

    @Value("${execution-jobs.kpi-job:false}")
    private boolean runKpiJob;

    public KpiCalculationJobRunner(JobLauncher jobLauncher, KpiCalculationJobFactory jobFactory) {
        this.jobLauncher = jobLauncher;
        this.jobFactory = jobFactory;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!runKpiJob) {
            log.info("KPI job is disabled (execution-jobs.kpi-job=false)");
            return;
        }

        Job job = jobFactory.createJob();
        log.info("Starting {}", job.getName());
        long startedAt = System.currentTimeMillis();

        JobExecution execution = jobLauncher.run(job,
                new org.springframework.batch.core.JobParametersBuilder()
                        .addLong("timestamp", System.currentTimeMillis())
                        .toJobParameters());

        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("Job {} finished with status={} in {} ms", job.getName(), execution.getStatus(), durationMs);

        if (!execution.getAllFailureExceptions().isEmpty()) {
            for (Throwable failure : execution.getAllFailureExceptions()) {
                log.error("KPI job failure", failure);
            }
            throw new IllegalStateException("KPI calculation job failed");
        }
    }
}
