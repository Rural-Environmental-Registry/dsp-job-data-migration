package br.car.dsp_batch.kpi.tasklet;

import br.car.dsp_batch.kpi.config.KpisProperties;
import br.car.dsp_batch.kpi.service.KpiCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Calculates AOI area and theme KPI measures after layer migration.
 */
@Slf4j
public class KpiCalculationTasklet implements Tasklet {

    private final KpiCalculationService calculationService;
    private final KpisProperties properties;

    public KpiCalculationTasklet(KpiCalculationService calculationService, KpisProperties properties) {
        this.calculationService = calculationService;
        this.properties = properties;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        properties.validate();
        log.info(
                "Starting KPI calculation (themes={}, areaUnit={})",
                properties.getThemeCount(),
                properties.resolveAreaUnitOfMeasurement()
        );
        calculationService.calculateAndPersist(
                properties.resolveAreaUnitOfMeasurement(),
                properties.enabledThemes()
        );
        log.info("KPI calculation finished");
        return RepeatStatus.FINISHED;
    }
}
