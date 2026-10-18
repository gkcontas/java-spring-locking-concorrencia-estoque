package com.gkcontas.inventory;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.scenario.ConcurrentExecution;
import com.gkcontas.inventory.service.WithdrawalService;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Helpers for the tests that need a race to be certain rather than likely.
 *
 * <p>A barrier between the read and the write makes every worker read before
 * any worker writes, which is the shape every lost update has. It only works
 * for strategies that do not hold a row lock across that point: with
 * {@code PESSIMISTIC} the first worker would be parked on the barrier holding
 * the lock the others need to reach it, so those tests use a think time
 * instead.
 */
final class ConcurrentWithdrawals {

    private ConcurrentWithdrawals() {
    }

    static ConcurrentExecution.Report allReadThenWrite(WithdrawalService withdrawals,
                                                       StockItem item, int workers,
                                                       int quantityEach,
                                                       LockingStrategy strategy) {
        CyclicBarrier allHaveRead = new CyclicBarrier(workers);
        return ConcurrentExecution.run(workers, index -> withdrawals.withdraw(
                item.getId(), item.getSku(), quantityEach, strategy, barrier(allHaveRead)));
    }

    static ConcurrentExecution.Report withThinkTime(WithdrawalService withdrawals, StockItem item,
                                                    int workers, int quantityEach,
                                                    LockingStrategy strategy, long thinkMillis) {
        return ConcurrentExecution.run(workers, index -> withdrawals.withdraw(
                item.getId(), item.getSku(), quantityEach, strategy,
                ConcurrentExecution.thinkFor(thinkMillis)));
    }

    static Runnable barrier(CyclicBarrier barrier) {
        return () -> {
            try {
                barrier.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException | TimeoutException e) {
                // A worker that failed before the barrier never arrives; the
                // others still have to finish so the test can assert.
            }
        };
    }
}
