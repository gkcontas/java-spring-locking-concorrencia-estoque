package com.gkcontas.inventory.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gkcontas.inventory.domain.LockingStrategy;
import java.util.Map;

/**
 * What one strategy did with N workers on one row.
 *
 * @param unitsPromised      the sum of the withdrawals the application accepted
 * @param unitsRemoved       how much the balance actually dropped
 * @param unitsLost          {@code unitsPromised - unitsRemoved}; anything above
 *                           zero is stock handed out twice, and the only number
 *                           in this record that is allowed to be non-zero for
 *                           exactly one strategy
 * @param transactionsUsed   the sum of the attempt counts; above the success
 *                           count only when something retried
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StrategyRunResult(
        LockingStrategy strategy,
        int workers,
        int quantityEach,
        int initialQuantity,
        long thinkTimeMillis,
        int finalQuantity,
        long unitsPromised,
        long unitsRemoved,
        long unitsLost,
        long successes,
        Map<String, Long> failures,
        long transactionsUsed,
        long elapsedMillis,
        double throughputPerSecond,
        long meanMicros,
        long p95Micros,
        long maxMicros) {
}
