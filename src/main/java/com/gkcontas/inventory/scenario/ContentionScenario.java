package com.gkcontas.inventory.scenario;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.service.WithdrawalService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The same workers, the same strategy, spread over a varying number of rows.
 *
 * <p>With one row every worker is a competitor and the measurement is the price
 * of contention. With as many rows as workers nobody competes and what is left
 * is the strategy's own overhead -- a conditional UPDATE is one statement, an
 * optimistic write is a SELECT plus an UPDATE, a pessimistic write is a locking
 * SELECT plus an UPDATE. Comparing the two runs separates "this strategy is
 * slow" from "this row is hot", which are different problems with different
 * fixes.
 */
@Service
@RequiredArgsConstructor
public class ContentionScenario {

    private final ScenarioSetup setup;
    private final WithdrawalService withdrawals;

    public ContentionResult run(LockingStrategy strategy, int workers, int rows,
                                int quantityEach, int initialQuantity, long thinkTimeMillis) {
        List<StockItem> items = setup.freshItems(
                "SCN-CONT-%s-%d".formatted(strategy.name(), rows), rows, initialQuantity);
        Runnable pause = ConcurrentExecution.thinkFor(thinkTimeMillis);

        ConcurrentExecution.Report report = ConcurrentExecution.run(workers, index -> {
            StockItem target = items.get(index % rows);
            withdrawals.withdraw(target.getId(), target.getSku(), quantityEach, strategy, pause);
        });

        long unitsLost = 0;
        long transactions = 0;
        for (StockItem item : items) {
            long promised = setup.promisedUnits(item.getId());
            long removed = (long) initialQuantity - setup.quantityOf(item.getId());
            unitsLost += promised - removed;
            transactions += setup.totalAttempts(item.getId());
        }

        return new ContentionResult(strategy, workers, rows, report.successes(),
                report.failuresByKind(), unitsLost, transactions,
                report.elapsedMicros() / 1_000,
                Math.round(report.throughputPerSecond() * 10) / 10.0,
                report.meanMicros(), report.percentileMicros(95), report.maxMicros());
    }
}
