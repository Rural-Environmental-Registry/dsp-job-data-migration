package br.car.dsp_batch.reconciliation;

import br.car.dsp_batch.aoi.metadata.AreaOfInterestMetadataRegistry;
import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.config.LayersProperties;
import br.car.dsp_batch.sync.WatermarkTableSpec;
import br.car.dsp_batch.sync.WatermarkTableSpecs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Runs orphan reconciliation jobs after incremental migration and before KPI calculation.
 */
@Slf4j
@Component
@Order(3)
public class ReconciliationJobRunner implements CommandLineRunner {

    public static final String ADMIN_UNIT_LEVEL_1_RECONCILIATION_JOB = "adminUnitLevel1ReconciliationJob";
    public static final String ADMIN_UNIT_LEVEL_2_RECONCILIATION_JOB = "adminUnitLevel2ReconciliationJob";
    public static final String ADMIN_UNIT_LEVEL_3_RECONCILIATION_JOB = "adminUnitLevel3ReconciliationJob";
    public static final String AREA_OF_INTEREST_RECONCILIATION_JOB = "areaOfInterestReconciliationJob";

    private final JobLauncher jobLauncher;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource sourceDataSource;
    private final DataSource businessTargetDataSource;
    private final DataSource geoTargetDataSource;
    private final OrphanReconciliationJobFactory jobFactory;
    private final ReconciliationTableSpecResolver specResolver;
    private final LayerReconciliationJobFactory layerReconciliationJobFactory;
    private final LayersProperties layersProperties;
    private final AreaOfInterestMetadataRegistry areaOfInterestMetadataRegistry;
    private final JobTableConfig adminUnitLevel1TableConfig;
    private final JobTableConfig adminUnitLevel2TableConfig;
    private final JobTableConfig adminUnitLevel3TableConfig;

    @Value("${execution-jobs.reconciliation-jobs:false}")
    private boolean runReconciliationJobs;

    public ReconciliationJobRunner(
            JobLauncher jobLauncher,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource businessTargetDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource,
            OrphanReconciliationJobFactory jobFactory,
            ReconciliationTableSpecResolver specResolver,
            LayerReconciliationJobFactory layerReconciliationJobFactory,
            LayersProperties layersProperties,
            AreaOfInterestMetadataRegistry areaOfInterestMetadataRegistry,
            @Qualifier("adminUnitLevel1TableConfig") JobTableConfig adminUnitLevel1TableConfig,
            @Qualifier("adminUnitLevel2TableConfig") JobTableConfig adminUnitLevel2TableConfig,
            @Qualifier("adminUnitLevel3TableConfig") JobTableConfig adminUnitLevel3TableConfig) {
        this.jobLauncher = jobLauncher;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.sourceDataSource = sourceDataSource;
        this.businessTargetDataSource = businessTargetDataSource;
        this.geoTargetDataSource = geoTargetDataSource;
        this.jobFactory = jobFactory;
        this.specResolver = specResolver;
        this.layerReconciliationJobFactory = layerReconciliationJobFactory;
        this.layersProperties = layersProperties;
        this.areaOfInterestMetadataRegistry = areaOfInterestMetadataRegistry;
        this.adminUnitLevel1TableConfig = adminUnitLevel1TableConfig;
        this.adminUnitLevel2TableConfig = adminUnitLevel2TableConfig;
        this.adminUnitLevel3TableConfig = adminUnitLevel3TableConfig;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!runReconciliationJobs) {
            log.info("Reconciliation jobs are disabled (execution-jobs.reconciliation-jobs=false)");
            return;
        }

        log.info("Starting orphan reconciliation jobs (L1 → L2 → L3 → AOI → layers)");
        runAdminUnitJob(ADMIN_UNIT_LEVEL_1_RECONCILIATION_JOB, "adminUnitLevel1", adminUnitLevel1TableConfig);
        runAdminUnitJob(ADMIN_UNIT_LEVEL_2_RECONCILIATION_JOB, "adminUnitLevel2", adminUnitLevel2TableConfig);
        runAdminUnitJob(ADMIN_UNIT_LEVEL_3_RECONCILIATION_JOB, "adminUnitLevel3", adminUnitLevel3TableConfig);
        runAreaOfInterestJob();
        runLayerJobs();
    }

    private void runAdminUnitJob(String jobName, String stepPrefix, JobTableConfig tableConfig) throws Exception {
        WatermarkTableSpec spec = specResolver.fromJobTableConfig(tableConfig, sourceDataSource);
        Job job = jobFactory.buildJob(
                jobRepository,
                transactionManager,
                sourceDataSource,
                businessTargetDataSource,
                geoTargetDataSource,
                jobName,
                stepPrefix,
                spec);
        runJob(job);
    }

    private void runAreaOfInterestJob() throws Exception {
        var metadata = areaOfInterestMetadataRegistry.getRequired(
                br.car.dsp_batch.sync.SyncKeys.AREA_OF_INTEREST);
        WatermarkTableSpec spec = WatermarkTableSpecs.fromAreaOfInterestMetadata(metadata);
        Job job = jobFactory.buildJob(
                jobRepository,
                transactionManager,
                sourceDataSource,
                businessTargetDataSource,
                geoTargetDataSource,
                AREA_OF_INTEREST_RECONCILIATION_JOB,
                "areaOfInterest",
                spec);
        runJob(job);
    }

    private void runLayerJobs() throws Exception {
        for (LayerConfig config : layersProperties.enabledLayers()) {
            Job job = layerReconciliationJobFactory.createJob(config);
            runJob(job);
        }
    }

    private void runJob(Job job) throws Exception {
        log.info("Starting reconciliation job {}", job.getName());
        long startedAt = System.currentTimeMillis();
        JobExecution execution = jobLauncher.run(job,
                new org.springframework.batch.core.JobParametersBuilder()
                        .addLong("timestamp", System.currentTimeMillis())
                        .toJobParameters());
        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("Job {} finished with status={} in {} ms", job.getName(), execution.getStatus(), durationMs);

        if (!execution.getAllFailureExceptions().isEmpty()) {
            for (Throwable failure : execution.getAllFailureExceptions()) {
                log.error("Reconciliation job {} failure: {}", job.getName(), failure.getMessage(), failure);
            }
            throw new IllegalStateException("Reconciliation job failed: " + job.getName());
        }

        for (StepExecution step : execution.getStepExecutions()) {
            log.info(
                    "  Step {}: read={} write={} skip={} commit={}",
                    step.getStepName(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    step.getSkipCount(),
                    step.getCommitCount());
        }
    }
}
