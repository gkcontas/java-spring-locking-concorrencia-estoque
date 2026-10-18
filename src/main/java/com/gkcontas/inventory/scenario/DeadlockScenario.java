package com.gkcontas.inventory.scenario;

import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.service.ConcurrencyFailures;
import com.gkcontas.inventory.service.CrossLockOperations;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Two transactions, two rows, and a barrier between the first lock and the
 * second so the cycle is guaranteed rather than lucky.
 *
 * <p>Both transactions take their first lock and then wait for each other. Only
 * when both hold one row does either ask for the other's, which is the moment
 * the cycle closes. Without the barrier the first transaction usually finishes
 * before the second starts and nothing happens at all.
 */
@Service
@RequiredArgsConstructor
public class DeadlockScenario {

    private final ScenarioSetup setup;
    private final CrossLockOperations crossLocks;

    public DeadlockResult crossedOrder() {
        StockItem first = setup.freshItem("SCN-DEADLOCK-A", 100);
        StockItem second = setup.freshItem("SCN-DEADLOCK-B", 100);
        CyclicBarrier bothHoldOneRow = new CyclicBarrier(2);

        ConcurrentExecution.Report report = ConcurrentExecution.run(2, index -> {
            Long firstId = index == 0 ? first.getId() : second.getId();
            Long secondId = index == 0 ? second.getId() : first.getId();
            try {
                crossLocks.lockInGivenOrder(firstId, secondId, waitFor(bothHoldOneRow));
            } catch (RuntimeException failure) {
                throw ConcurrencyFailures.translate(failure, 1);
            }
        });

        return new DeadlockResult("crossed", 2, report.successes(),
                report.count("deadlock"), report.failuresByKind(),
                report.elapsedMicros() / 1_000);
    }

    public DeadlockResult agreedOrder() {
        StockItem first = setup.freshItem("SCN-DEADLOCK-A", 100);
        StockItem second = setup.freshItem("SCN-DEADLOCK-B", 100);
        List<Long> ids = List.of(first.getId(), second.getId());
        List<Long> reversed = List.of(second.getId(), first.getId());

        // No barrier here, and that is not a shortcut. Both transactions take
        // both locks in a single ordered statement, so there is no point
        // between the two locks to synchronise on -- which is exactly why the
        // cycle cannot form.
        ConcurrentExecution.Report report = ConcurrentExecution.run(2, index -> {
            try {
                crossLocks.lockInAgreedOrder(index == 0 ? ids : reversed, () -> {
                });
            } catch (RuntimeException failure) {
                throw ConcurrencyFailures.translate(failure, 1);
            }
        });

        return new DeadlockResult("agreed", 2, report.successes(),
                report.count("deadlock"), report.failuresByKind(),
                report.elapsedMicros() / 1_000);
    }

    /**
     * A barrier that gives up instead of hanging. The deadlock victim is killed
     * while blocked on its second lock, so its partner may never arrive -- and a
     * barrier waiting forever would turn a demonstration into a stuck request.
     */
    private static Runnable waitFor(CyclicBarrier barrier) {
        return () -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException | TimeoutException e) {
                // The other side is already gone; carrying on is correct.
            }
        };
    }
}
