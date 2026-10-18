package com.gkcontas.inventory.scenario;

import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.service.ConcurrencyFailures;
import com.gkcontas.inventory.service.CrossLockOperations;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * What a pessimistic lock does to the caller that arrives second.
 *
 * <p>Three things are worth separating. A plain {@code FOR UPDATE} queues, and
 * without a {@code lock_timeout} on the server it queues forever -- the request
 * does not fail, it hangs, which is far harder to diagnose than an error.
 * {@code NOWAIT} refuses to queue and fails on contact. And a positive
 * {@code jakarta.persistence.lock.timeout} buys nothing on PostgreSQL: there is
 * no {@code FOR UPDATE WAIT n} in the grammar, so the hint is dropped and the
 * server setting is what actually governs the wait.
 */
@Service
@RequiredArgsConstructor
public class LockWaitScenario {

    private final ScenarioSetup setup;
    private final CrossLockOperations crossLocks;

    public LockWaitResult run(long holdMillis, long serverLockTimeoutMillis) {
        StockItem item = setup.freshItem("SCN-LOCK-WAIT", 100);

        Thread holder = new Thread(() -> crossLocks.holdLock(item.getId(), holdMillis),
                "lock-holder");
        holder.start();

        // There is no callback for "the SELECT ... FOR UPDATE came back", so the
        // measurement gives the holder a head start instead of pretending to be
        // precise about it. Everything timed below happens while it still holds
        // the row.
        sleepQuietly(150);

        long noWaitStart = System.nanoTime();
        String noWaitOutcome = attempt(() -> crossLocks.takeLockOrFail(item.getId()));
        long noWaitMillis = (System.nanoTime() - noWaitStart) / 1_000_000;

        long waitingStart = System.nanoTime();
        String waitingOutcome = attempt(() -> crossLocks.takeLockWaiting(item.getId()));
        long waitingMillis = (System.nanoTime() - waitingStart) / 1_000_000;

        joinQuietly(holder);

        return new LockWaitResult(holdMillis, serverLockTimeoutMillis,
                waitingOutcome, waitingMillis, noWaitOutcome, noWaitMillis);
    }

    private String attempt(Runnable action) {
        try {
            action.run();
            return "ok";
        } catch (RuntimeException failure) {
            return ConcurrentExecution.classify(ConcurrencyFailures.translate(failure, 1));
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(30));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
