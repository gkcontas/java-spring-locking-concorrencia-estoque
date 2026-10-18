package com.gkcontas.inventory.scenario;

import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.service.ConcurrencyFailures;
import com.gkcontas.inventory.service.TransactionalWithdrawals;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The single most common mistake with optimistic locking, demonstrated rather
 * than asserted.
 *
 * <p>{@code optimisticCaughtInside} wraps its entire body in a try/catch and
 * would rethrow {@code IllegalStateException} if that catch ever ran. Two
 * workers race, one loses, and the loser's failure arrives at the caller as a
 * plain concurrency failure -- never as the exception the inner handler would
 * have produced. The conflict is raised by the commit, which happens after the
 * method returns, so there is no code inside the transaction that can see it.
 */
@Service
@RequiredArgsConstructor
public class CatchInsideScenario {

    private final ScenarioSetup setup;
    private final TransactionalWithdrawals transactional;

    public CatchInsideResult run(int workers) {
        StockItem item = setup.freshItem("SCN-CATCH-INSIDE", 100);
        CyclicBarrier allHaveRead = new CyclicBarrier(workers);

        ConcurrentExecution.Report report = ConcurrentExecution.run(workers, index -> {
            try {
                transactional.optimisticCaughtInside(item.getId(), 1, waitFor(allHaveRead));
            } catch (RuntimeException failure) {
                throw ConcurrencyFailures.translate(failure, 1);
            }
        });

        Map<String, Long> failures = report.failuresByKind();
        boolean innerCatchFired = failures.keySet().stream()
                .anyMatch(kind -> kind.contains("IllegalStateException"));
        return new CatchInsideResult(workers, report.successes(), failures, innerCatchFired);
    }

    private static Runnable waitFor(CyclicBarrier barrier) {
        return () -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException | TimeoutException e) {
                // Nothing to recover: the point is what the caller sees.
            }
        };
    }
}
