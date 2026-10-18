package com.gkcontas.inventory.scenario;

import java.util.Map;

/**
 * @param lockOrder  "crossed" when the two transactions ask for the rows in
 *                   opposite order, "agreed" when both sort by primary key
 * @param deadlocks  how many transactions PostgreSQL chose as the victim
 */
public record DeadlockResult(
        String lockOrder,
        int workers,
        long successes,
        long deadlocks,
        Map<String, Long> failures,
        long elapsedMillis) {
}
