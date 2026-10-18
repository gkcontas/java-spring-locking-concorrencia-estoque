package com.gkcontas.inventory.scenario;

import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.service.ConcurrencyFailures;
import com.gkcontas.inventory.service.ReservationService;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Two reservations that each fit on their own and do not fit together.
 *
 * <p>The barrier makes both transactions read the item before either inserts,
 * which is the whole situation: both see the same free quantity, both conclude
 * their request fits, and neither writes to the item. Hibernate has no reason
 * to check a version on a row nobody changed, so the version check that the
 * team believes protects the invariant is never even performed.
 */
@Service
@RequiredArgsConstructor
public class ReservationGuardScenario {

    private final ScenarioSetup setup;
    private final ReservationService reservations;

    public ReservationGuardResult run(boolean forceParentIncrement, int workers,
                                      int initialQuantity, int quantityEach) {
        String mode = forceParentIncrement ? "force-increment" : "unguarded";
        StockItem item = setup.freshItem("SCN-RESERVE-" + mode.toUpperCase(java.util.Locale.ROOT),
                initialQuantity);
        CyclicBarrier allHaveRead = new CyclicBarrier(workers);

        ConcurrentExecution.Report report = ConcurrentExecution.run(workers, index -> {
            String orderRef = "%s-%d-%d".formatted(mode, System.nanoTime(), index);
            try {
                if (forceParentIncrement) {
                    reservations.reserveWithForceIncrement(item.getId(), orderRef, quantityEach,
                            waitFor(allHaveRead));
                } else {
                    reservations.reserveUnguarded(item.getId(), orderRef, quantityEach,
                            waitFor(allHaveRead));
                }
            } catch (RuntimeException failure) {
                throw ConcurrencyFailures.translate(failure, 1);
            }
        });

        long reservedUnits = setup.reservedUnits(item.getId());
        return new ReservationGuardResult(mode, workers, initialQuantity, quantityEach,
                report.successes(), report.failuresByKind(), reservedUnits,
                setup.reservationCount(item.getId()),
                Math.max(0, reservedUnits - initialQuantity),
                setup.versionOf(item.getId()));
    }

    private static Runnable waitFor(CyclicBarrier barrier) {
        return () -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException | TimeoutException e) {
                // A worker that failed before reading never arrives; the rest
                // should still finish rather than hang on it.
            }
        };
    }
}
