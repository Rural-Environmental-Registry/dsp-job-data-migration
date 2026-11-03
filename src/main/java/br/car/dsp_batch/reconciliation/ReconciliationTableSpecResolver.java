package br.car.dsp_batch.reconciliation;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.batch.config.JobTableConfigValidator;
import br.car.dsp_batch.sync.WatermarkTableSpec;
import br.car.dsp_batch.sync.WatermarkTableSpecs;
import br.car.dsp_batch.temporal.BatchTemporalProperties;
import br.car.dsp_batch.temporal.TemporalColumnSpecs;
import br.car.dsp_batch.temporal.TemporalSchemaSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class ReconciliationTableSpecResolver {

    private final TemporalSchemaSupport temporalSchemaSupport;
    private final BatchTemporalProperties batchTemporalProperties;

    public ReconciliationTableSpecResolver(TemporalSchemaSupport temporalSchemaSupport,
                                           BatchTemporalProperties batchTemporalProperties) {
        this.temporalSchemaSupport = temporalSchemaSupport;
        this.batchTemporalProperties = batchTemporalProperties;
    }

    public WatermarkTableSpec fromJobTableConfig(JobTableConfig tableConfig, DataSource sourceDataSource) {
        JobTableConfigValidator.requireWatermarkFields(tableConfig);
        TemporalColumnSpecs temporalColumns = temporalSchemaSupport.resolveTemporalColumns(
                new JdbcTemplate(sourceDataSource),
                tableConfig.getSourceTable(),
                tableConfig.getCreationDateColumn(),
                tableConfig.getUpdatedAtColumn(),
                batchTemporalProperties.resolvePolicy(
                        tableConfig.getSourceTimezone(),
                        "batch.*.source-timezone for " + tableConfig.getSourceTable()));
        return WatermarkTableSpecs.fromJobTableConfig(tableConfig, temporalColumns);
    }
}
