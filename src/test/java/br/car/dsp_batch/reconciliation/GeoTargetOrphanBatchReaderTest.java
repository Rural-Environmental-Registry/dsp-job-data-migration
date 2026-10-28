package br.car.dsp_batch.reconciliation;

import br.car.dsp_batch.sync.SyncKeys;
import br.car.dsp_batch.sync.WatermarkTableSpec;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GeoTargetOrphanBatchReaderTest {

    @Test
    void fetchNextBatch_HashKeysetUsesPkTextExpressionNotAlias() {
        JdbcTemplate geoTargetJdbc = mock(JdbcTemplate.class);
        GeoTargetOrphanBatchReader reader = new GeoTargetOrphanBatchReader(
                geoTargetJdbc,
                sampleSpec(),
                0,
                4);

        reader.fetchNextBatch("abc", 500);

        String sql = capturedSql(geoTargetJdbc);
        assertTrue(sql.contains("AND id::text > ?"));
        assertFalse(sql.contains("AND pk_text > ?"));
        assertTrue(sql.contains("ORDER BY pk_text"));
        assertTrue(sql.contains("AND mod(abs(hashtext(id::text)), ?) = ?"));
    }

    @Test
    void fetchNextBatch_FirstPageOmitsKeysetPredicate() {
        JdbcTemplate geoTargetJdbc = mock(JdbcTemplate.class);
        GeoTargetOrphanBatchReader reader = new GeoTargetOrphanBatchReader(
                geoTargetJdbc,
                sampleSpec(),
                0,
                4);

        reader.fetchNextBatch(null, 500);

        String sql = capturedSql(geoTargetJdbc);
        assertFalse(sql.contains("AND id::text > ?"));
        assertTrue(sql.contains("AND mod(abs(hashtext(id::text)), ?) = ?"));
        assertTrue(sql.contains("ORDER BY pk_text"));
    }

    private static String capturedSql(JdbcTemplate geoTargetJdbc) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(geoTargetJdbc).query(sqlCaptor.capture(), any(RowCallbackHandler.class), any(Object[].class));
        return sqlCaptor.getValue();
    }

    private static WatermarkTableSpec sampleSpec() {
        return new WatermarkTableSpec(
                SyncKeys.ADMIN_UNIT_LEVEL_1,
                "src.l1",
                "id",
                "geom",
                TemporalTestFixtures.timestamptz("created_at"),
                TemporalTestFixtures.timestamptz("updated_at"),
                "1=1",
                3763,
                "territory-level-1",
                "dsp.territory_level_1",
                "id",
                "geom",
                null,
                null,
                null
        );
    }
}
