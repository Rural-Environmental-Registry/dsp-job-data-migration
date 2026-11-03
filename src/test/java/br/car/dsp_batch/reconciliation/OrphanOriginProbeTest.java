package br.car.dsp_batch.reconciliation;

import br.car.dsp_batch.sync.SyncKeys;
import br.car.dsp_batch.sync.WatermarkTableSpec;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrphanOriginProbeTest {

    @Test
    void probeExistingNormalizedIds_EmptyCandidatesSkipsQuery() {
        OrphanReconciliationService service = new OrphanReconciliationService(new ReconciliationProperties());
        OrphanOriginProbe probe = new OrphanOriginProbe(service);
        JdbcTemplate sourceJdbc = mock(JdbcTemplate.class);

        Set<Object> found = probe.probeExistingNormalizedIds(
                sourceJdbc, sampleSpec(), List.of());

        assertTrue(found.isEmpty());
        verify(sourceJdbc, never()).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    void probeExistingNormalizedIds_UsesInClauseWithPkList() {
        OrphanReconciliationService service = new OrphanReconciliationService(new ReconciliationProperties());
        OrphanOriginProbe probe = new OrphanOriginProbe(service);
        JdbcTemplate sourceJdbc = mock(JdbcTemplate.class);

        probe.probeExistingNormalizedIds(sourceJdbc, sampleSpec(), List.of(10L, 20L));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> bindCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(sourceJdbc).query(sqlCaptor.capture(), any(RowCallbackHandler.class), bindCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("TRIM(CAST(id AS text)) IN (?,?)"));
        assertTrue(sqlCaptor.getValue().contains("FROM src.l1"));
        assertEquals("10", bindCaptor.getValue()[0]);
        assertEquals("20", bindCaptor.getValue()[1]);
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
                4326,
                "layer",
                "dsp.l1",
                "id",
                "geom",
                null,
                null,
                null
        );
    }
}
