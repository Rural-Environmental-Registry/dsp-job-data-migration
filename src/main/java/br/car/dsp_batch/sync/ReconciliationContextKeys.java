package br.car.dsp_batch.sync;

/**
 * Job execution context keys for orphan reconciliation jobs.
 */
public final class ReconciliationContextKeys {

    public static final String TARGET_EMPTY = "reconciliationTargetEmpty";

    /**
     * Marks the job as orphan reconciliation so geo-file flags use only departed
     * territories — never the first-load “flag every row” watermark path.
     */
    public static final String ORPHAN_RECONCILIATION = "orphanReconciliation";

    private ReconciliationContextKeys() {
    }
}
