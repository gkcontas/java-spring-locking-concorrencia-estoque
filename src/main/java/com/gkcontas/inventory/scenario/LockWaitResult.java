package com.gkcontas.inventory.scenario;

/**
 * How a pessimistic lock behaves while somebody else holds the row.
 *
 * @param holdMillis         how long the holder kept the lock
 * @param waitingOutcome     "ok" if the waiter eventually got the lock, "locked"
 *                           if the server's {@code lock_timeout} cut it off
 * @param waitingMillis      how long the waiter actually blocked
 * @param noWaitOutcome      always "locked" while the row is held: NOWAIT does
 *                           not queue
 * @param noWaitMillis       how long NOWAIT took to give up
 */
public record LockWaitResult(
        long holdMillis,
        long serverLockTimeoutMillis,
        String waitingOutcome,
        long waitingMillis,
        String noWaitOutcome,
        long noWaitMillis) {
}
