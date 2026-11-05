package br.car.dsp_batch.reconciliation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "batch.reconciliation")
public class ReconciliationProperties {

    private int batchSize = 5000;
    private int deleteBatchSize = 1000;
    private int gridSize = 4;
    private int threadPoolSize = 4;
    private boolean skipWhenTargetEmpty = true;
}
