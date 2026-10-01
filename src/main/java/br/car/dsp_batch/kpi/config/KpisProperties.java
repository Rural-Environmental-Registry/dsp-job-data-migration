package br.car.dsp_batch.kpi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * KPI calculation configuration ({@code kpis} block in application YAML).
 */
@ConfigurationProperties(prefix = "kpis")
@Getter
@Setter
public class KpisProperties {

    private int themeCount;
    private String areaUnitOfMeasurement = "m²";
    private List<ThemeKpiConfig> themes = new ArrayList<>();

    public List<ThemeKpiConfig> enabledThemes() {
        if (themes == null || themes.isEmpty()) {
            return List.of();
        }
        return themes.stream()
                .filter(theme -> theme != null && theme.getLayerName() != null
                        && !theme.getLayerName().isBlank())
                .toList();
    }

    public void validate() {
        List<ThemeKpiConfig> configured = enabledThemes();
        if (themeCount < 0) {
            throw new IllegalStateException("kpis.theme-count must be >= 0");
        }
        if (configured.size() != themeCount) {
            throw new IllegalStateException(
                    "kpis.theme-count is " + themeCount
                            + " but " + configured.size() + " theme(s) are configured");
        }
        for (ThemeKpiConfig theme : configured) {
            theme.validate();
        }
    }

    public String resolveAreaUnitOfMeasurement() {
        if (areaUnitOfMeasurement == null || areaUnitOfMeasurement.isBlank()) {
            return "m²";
        }
        return areaUnitOfMeasurement.trim();
    }
}
