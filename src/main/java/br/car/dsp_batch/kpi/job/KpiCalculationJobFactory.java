package br.car.dsp_batch.kpi.job;

import br.car.dsp_batch.kpi.config.KpisProperties;
import br.car.dsp_batch.kpi.service.KpiCalculationService;
import br.car.dsp_batch.kpi.tasklet.KpiCalculationTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Builds the KPI calculation Spring Batch job.
 */
@Component
public class KpiCalculationJobFactory {

    public static final String JOB_NAME = "kpiCalculationJob";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final KpiCalculationService calculationService;
    private final KpisProperties properties;

    public KpiCalculationJobFactory(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            KpiCalculationService calculationService,
            KpisProperties properties) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.calculationService = calculationService;
        this.properties = properties;
    }

    public Job createJob() {
        Step calculationStep = new StepBuilder("kpiCalculationStep", jobRepository)
                .tasklet(new KpiCalculationTasklet(calculationService, properties), transactionManager)
                .build();

        return new JobBuilder(JOB_NAME, jobRepository)
                .start(calculationStep)
                .build();
    }
}
