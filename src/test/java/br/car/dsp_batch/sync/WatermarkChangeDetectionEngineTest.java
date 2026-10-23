package br.car.dsp_batch.sync;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WatermarkChangeDetectionEngineTest {

    private final WatermarkChangeDetectionEngine engine =
            new WatermarkChangeDetectionEngine(mock(SyncStateRepository.class));

    @Test
    void normalizeId_ConvertsNumberToString() {
        assertEquals("42", engine.normalizeId(42L));
    }

    @Test
    void shouldRunOrphanCheck_WhenNoState() {
        assertFalse(engine.shouldRunOrphanCheck(null));
    }

    @Test
    void shouldRunOrphanCheck_WhenWatermarkNotYetSet() {
        SyncState state = new SyncState(
                "admin_unit_level_1",
                "src.l1",
                null,
                null,
                null,
                null
        );
        assertFalse(engine.shouldRunOrphanCheck(state));
    }

    @Test
    void shouldRunOrphanCheck_WhenWatermarkExists() {
        SyncState state = new SyncState(
                "admin_unit_level_1",
                "src.l1",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                1L,
                Instant.now()
        );
        assertTrue(engine.shouldRunOrphanCheck(state));
    }
}
