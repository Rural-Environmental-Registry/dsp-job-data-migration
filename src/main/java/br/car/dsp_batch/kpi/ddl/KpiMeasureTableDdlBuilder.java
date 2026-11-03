package br.car.dsp_batch.kpi.ddl;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * DDL for {@code dsp.kpi_measure} on the business database.
 */
@Component
public class KpiMeasureTableDdlBuilder {

    public static final String TABLE_NAME = "dsp.kpi_measure";

    public List<String> buildStatements() {
        List<String> statements = new ArrayList<>();
        statements.add("CREATE SCHEMA IF NOT EXISTS dsp");
        statements.add("""
                CREATE TABLE IF NOT EXISTS dsp.kpi_measure (
                    id                  BIGSERIAL PRIMARY KEY,
                    area_of_interest_id VARCHAR(255) NOT NULL
                        REFERENCES dsp.area_of_interest (id),
                    value               NUMERIC(18, 3) NOT NULL,
                    kpi_name            VARCHAR(255) NOT NULL,
                    CONSTRAINT uq_kpi_measure_aoi_name
                        UNIQUE (area_of_interest_id, kpi_name)
                )""");
        statements.add(
                "CREATE INDEX IF NOT EXISTS idx_kpi_measure_aoi_id ON dsp.kpi_measure (area_of_interest_id)");
        statements.add(
                "CREATE INDEX IF NOT EXISTS idx_kpi_measure_name ON dsp.kpi_measure (kpi_name)");
        return statements;
    }
}
