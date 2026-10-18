package com.gkcontas.inventory.scenario;

import com.gkcontas.inventory.domain.LockingStrategy;
import java.util.Map;

/**
 * The same number of workers spread over a varying number of rows.
 *
 * <p>With one row every worker is a competitor; with as many rows as workers
 * there is no contention left and what remains is the strategy's own overhead.
 * The gap between the two is the price of the hot row, and it is not the same
 * price for every strategy.
 */
public record ContentionResult(
        LockingStrategy strategy,
        int workers,
        int rows,
        long successes,
        Map<String, Long> failures,
        long unitsLost,
        long transactionsUsed,
        long elapsedMillis,
        double throughputPerSecond,
        long meanMicros,
        long p95Micros,
        long maxMicros) {
}
