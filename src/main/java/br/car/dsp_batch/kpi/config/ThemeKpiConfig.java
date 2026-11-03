package br.car.dsp_batch.kpi.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Theme KPI entry from {@code kpis.themes[]} in application YAML.
 */
@Getter
@Setter
public class ThemeKpiConfig {

    private int slot;
    private String layerName;
    private String unitOfMeasurement;
    private String label;

    public void validate() {
        if (layerName == null || layerName.isBlank()) {
            throw new IllegalStateException("kpis.themes: 'layer-name' is required");
        }
        if (unitOfMeasurement == null || unitOfMeasurement.isBlank()) {
            throw new IllegalStateException(
                    "kpis.themes: 'unit-of-measurement' is required for layer '"
                            + layerName.trim() + "'");
        }
    }

    public String resolveLayerName() {
        return layerName.trim();
    }

    public String resolveUnitOfMeasurement() {
        return unitOfMeasurement.trim();
    }
}
