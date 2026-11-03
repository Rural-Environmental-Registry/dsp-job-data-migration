package br.car.dsp_batch.reconciliation;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.metadata.LayerMetadataRegistry;
import br.car.dsp_batch.sync.WatermarkTableSpec;
import br.car.dsp_batch.sync.WatermarkTableSpecs;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Component
public class LayerReconciliationJobFactory {

    public static final String JOB_NAME_PREFIX = "layerReconciliationJob_";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource sourceDataSource;
    private final DataSource geoTargetDataSource;
    private final LayerMetadataRegistry metadataRegistry;
    private final OrphanReconciliationJobFactory jobFactory;

    public LayerReconciliationJobFactory(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource,
            LayerMetadataRegistry metadataRegistry,
            OrphanReconciliationJobFactory jobFactory) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.sourceDataSource = sourceDataSource;
        this.geoTargetDataSource = geoTargetDataSource;
        this.metadataRegistry = metadataRegistry;
        this.jobFactory = jobFactory;
    }

    public Job createJob(LayerConfig config) {
        String layerKey = config.resolveKey();
        var metadata = metadataRegistry.getRequired(layerKey);
        WatermarkTableSpec spec = WatermarkTableSpecs.fromLayerMetadata(metadata);
        String jobName = JOB_NAME_PREFIX + layerKey;
        return jobFactory.buildJob(
                jobRepository,
                transactionManager,
                sourceDataSource,
                null,
                geoTargetDataSource,
                jobName,
                "layer_" + layerKey + "_",
                spec);
    }
}
