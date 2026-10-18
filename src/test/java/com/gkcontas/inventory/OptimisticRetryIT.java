package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.repository.StockMovementRepository;
import com.gkcontas.inventory.scenario.ConcurrentExecution;
import com.gkcontas.inventory.service.WithdrawalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The retry turns the conflict from the caller's problem into a cost paid in
 * transactions, and the movement rows record how many it took.
 */
class OptimisticRetryIT extends IntegrationTestBase {

    @Autowired
    private WithdrawalService withdrawals;
    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private StockMovementRepository movements;

    @Test
    void everyWorkerEventuallySucceedsAndNothingIsLost() {
        StockItem item = fixtures.newItem("RETRY", 1000);

        ConcurrentExecution.Report report = ConcurrentWithdrawals.allReadThenWrite(
                withdrawals, item, 8, 1, LockingStrategy.OPTIMISTIC_RETRY);

        assertThat(report.successes()).isEqualTo(8);
        assertThat(report.failuresByKind()).isEmpty();
        assertThat(movements.promisedUnits(item.getId())).isEqualTo(8);
        assertThat(fixtures.reload(item.getId()).getQuantity()).isEqualTo(992);
    }

    /**
     * The whole point of the retry, stated as a number: eight successful
     * withdrawals cost more than eight transactions, because seven of them
     * started by losing.
     */
    @Test
    void theRetryCostsMoreTransactionsThanThereAreWithdrawals() {
        StockItem item = fixtures.newItem("RETRY", 1000);

        ConcurrentWithdrawals.allReadThenWrite(
                withdrawals, item, 8, 1, LockingStrategy.OPTIMISTIC_RETRY);

        assertThat(movements.totalAttempts(item.getId())).isGreaterThan(8);
    }

    /**
     * Each attempt is a new transaction with a new read, so the version the
     * retry writes against is the current one. Reusing the entity from the
     * failed attempt would write the same stale version forever.
     */
    @Test
    void theVersionEndsOnTheNumberOfSuccessfulWrites() {
        StockItem item = fixtures.newItem("RETRY", 1000);

        ConcurrentWithdrawals.allReadThenWrite(
                withdrawals, item, 8, 1, LockingStrategy.OPTIMISTIC_RETRY);

        assertThat(fixtures.reload(item.getId()).getVersion()).isEqualTo(8);
    }
}
