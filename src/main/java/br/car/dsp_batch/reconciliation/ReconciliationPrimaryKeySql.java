package br.car.dsp_batch.reconciliation;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

/**
 * SQL helpers so PK comparisons work when source and geo-target use different JDBC types
 * (e.g. integer on source, varchar on DSP targets).
 */
public final class ReconciliationPrimaryKeySql {

    private ReconciliationPrimaryKeySql() {
    }

    /**
     * Expression for comparing a source column to normalized text bind values.
     */
    public static String asComparableText(String columnExpression) {
        return "TRIM(CAST(" + columnExpression + " AS text))";
    }

    public static String inPlaceholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    public static Object[] bindNormalized(Collection<?> ids, Function<Object, Object> normalizeId) {
        return ids.stream().map(normalizeId).toArray();
    }
}
