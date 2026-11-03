package br.car.dsp_batch.kpi.ddl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KpiMeasureTableDdlBuilderTest {

    private final KpiMeasureTableDdlBuilder builder = new KpiMeasureTableDdlBuilder();

    @Test
    void buildStatements_CreatesTableWithoutCascade() {
        List<String> statements = builder.buildStatements();
        String createTable = statements.stream()
                .filter(sql -> sql.contains("CREATE TABLE IF NOT EXISTS dsp.kpi_measure"))
                .findFirst()
                .orElseThrow();

        assertTrue(createTable.contains("REFERENCES dsp.area_of_interest (id)"));
        assertFalse(createTable.toUpperCase().contains("ON DELETE CASCADE"));
        assertTrue(statements.stream().anyMatch(sql -> sql.contains("uq_kpi_measure_aoi_name")));
    }
}
