package com.gkcontas.inventory.scenario;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.service.WithdrawalService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The central measurement: N workers take {@code quantityEach} off the same
 * row at the same time, under one strategy, and the row is then asked what
 * really happened.
 *
 * <p>The think time is the knob that matters. It sits between the read and the
 * write and stands in for whatever a real service does there -- validating,
 * calling another system, rendering a response. At zero the window is a few
 * microseconds wide and a lost update is a coin toss; at a handful of
 * milliseconds it is certain. The same knob is what makes the pessimistic
 * strategy expensive, because it is time a row lock is held, so one run with
 * think time and one without tell two different and equally true stories.
 */
@Service
@RequiredArgsConstructor
public class StrategyScenario {

    private final ScenarioSetup setup;
    private final WithdrawalService withdrawals;

    public StrategyRunResult run(LockingStrategy strategy, int workers, int quantityEach,
                                 int initialQuantity, long thinkTimeMillis) {
        StockItem item = setup.freshItem("SCN-" + strategy.name(), initialQuantity);
        Runnable pause = ConcurrentExecution.thinkFor(thinkTimeMillis);

        ConcurrentExecution.Report report = ConcurrentExecution.run(workers, index ->
                withdrawals.withdraw(item.getId(), item.getSku(), quantityEach, strategy, pause));

        return summarise(strategy, workers, quantityEach, initialQuantity, thinkTimeMillis,
                item.getId(), report);
    }

    /**
     * The same batch under REPEATABLE READ instead of the default READ
     * COMMITTED, with no application-level protection at all. PostgreSQL turns
     * the lost update into a 40001 the caller has to handle.
     */
    public StrategyRunResult runUnderRepeatableRead(int workers, int quantityEach,
                                                    int initialQuantity, long thinkTimeMillis) {
        StockItem item = setup.freshItem("SCN-REPEATABLE-READ", initialQuantity);
        Runnable pause = ConcurrentExecution.thinkFor(thinkTimeMillis);

        ConcurrentExecution.Report report = ConcurrentExecution.run(workers, index ->
                withdrawals.withdrawUnderRepeatableRead(item.getId(), quantityEach, pause));

        return summarise(LockingStrategy.NONE, workers, quantityEach, initialQuantity,
                thinkTimeMillis, item.getId(), report);
    }

    /**
     * Pessimistic with {@code NOWAIT}: the queue becomes a pile of failures.
     * Same safety, completely different shape for the caller -- nobody waits
     * and almost nobody succeeds.
     */
    public StrategyRunResult runFailFast(int workers, int quantityEach, int initialQuantity,
                                         long thinkTimeMillis) {
        StockItem item = setup.freshItem("SCN-NOWAIT", initialQuantity);
        Runnable pause = ConcurrentExecution.thinkFor(thinkTimeMillis);

        ConcurrentExecution.Report report = ConcurrentExecution.run(workers, index ->
                withdrawals.withdrawOrFailFast(item.getId(), quantityEach, pause));

        return summarise(LockingStrategy.PESSIMISTIC, workers, quantityEach, initialQuantity,
                thinkTimeMillis, item.getId(), report);
    }

    public List<StrategyRunResult> compareAll(int workers, int quantityEach, int initialQuantity,
                                              long thinkTimeMillis) {
        List<StrategyRunResult> results = new ArrayList<>();
        for (LockingStrategy strategy : LockingStrategy.values()) {
            results.add(run(strategy, workers, quantityEach, initialQuantity, thinkTimeMillis));
        }
        return results;
    }

    private StrategyRunResult summarise(LockingStrategy strategy, int workers, int quantityEach,
                                        int initialQuantity, long thinkTimeMillis, Long itemId,
                                        ConcurrentExecution.Report report) {
        int finalQuantity = setup.quantityOf(itemId);
        long promised = setup.promisedUnits(itemId);
        long removed = (long) initialQuantity - finalQuantity;

        return new StrategyRunResult(
                strategy, workers, quantityEach, initialQuantity, thinkTimeMillis,
                finalQuantity, promised, removed, promised - removed,
                report.successes(), report.failuresByKind(), setup.totalAttempts(itemId),
                report.elapsedMicros() / 1_000,
                Math.round(report.throughputPerSecond() * 10) / 10.0,
                report.meanMicros(), report.percentileMicros(95), report.maxMicros());
    }
}
