package br.car.dsp_batch.reconciliation.partitioner;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Splits a geo-target orphan scan into hash buckets. Destination PKs are varchar,
 * so hashtext is the single partition strategy (digits, UUID, or mixed codes).
 */
public class GeoTargetReconciliationPartitioner implements Partitioner {

    public static final String HASH_BUCKET = "reconciliationHashBucket";
    public static final String HASH_GRID_SIZE = "reconciliationHashGridSize";

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int size = Math.max(1, gridSize);
        Map<String, ExecutionContext> result = new HashMap<>();
        for (int bucket = 0; bucket < size; bucket++) {
            ExecutionContext ctx = new ExecutionContext();
            ctx.putInt(HASH_BUCKET, bucket);
            ctx.putInt(HASH_GRID_SIZE, size);
            result.put("partition" + bucket, ctx);
        }
        return result;
    }
}
