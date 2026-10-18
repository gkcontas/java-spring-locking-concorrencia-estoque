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
 * No barrier in this class, on purpose. A worker parked between its lock and
 * its write is holding the row every other worker needs to get there, so a
 * barrier would be a deadlock by construction -- the think time opens the same
 * window without it.
 */
class PessimisticLockIT extends IntegrationTestBase {

    @Autowired
    private WithdrawalService withdrawals;
    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private StockMovementRepository movements;

    @Test
    void everyWorkerSucceedsAndNothingConflicts() {
        StockItem item = fixtures.newItem("PESS", 1000);

        ConcurrentExecution.Report report = ConcurrentWithdrawals.withThinkTime(
                withdrawals, item, 16, 1, LockingStrategy.PESSIMISTIC, 2);

        assertThat(report.successes()).isEqualTo(16);
        assertThat(report.failuresByKind()).isEmpty();
        assertThat(movements.promisedUnits(item.getId())).isEqualTo(16);
        assertThat(fixtures.reload(item.getId()).getQuantity()).isEqualTo(984);
    }

    /**
     * Exactly one transaction per withdrawal: the queue replaces the retry, so
     * nothing is ever done twice.
     */
    @Test
    void noWorkIsRepeated() {
        StockItem item = fixtures.newItem("PESS", 1000);

        ConcurrentWithdrawals.withThinkTime(
                withdrawals, item, 16, 1, LockingStrategy.PESSIMISTIC, 2);

        assertThat(movements.totalAttempts(item.getId())).isEqualTo(16);
    }

    /**
     * The lock makes the version check unfalsifiable rather than unnecessary:
     * Hibernate still emits it and still bumps the column, because the entity
     * carries {@code @Version} and the UPDATE comes from the entity.
     */
    @Test
    void theVersionStillAdvancesUnderThePessimisticPath() {
        StockItem item = fixtures.newItem("PESS", 1000);

        ConcurrentWithdrawals.withThinkTime(
                withdrawals, item, 8, 1, LockingStrategy.PESSIMISTIC, 1);

        assertThat(fixtures.reload(item.getId()).getVersion()).isEqualTo(8);
    }

    /**
     * The serialization is real: the batch cannot finish faster than the sum of
     * the think times, because each one is paid while the row lock is held.
     */
    @Test
    void theBatchTakesAtLeastTheSumOfTheThinkTimes() {
        StockItem item = fixtures.newItem("PESS", 1000);

        ConcurrentExecution.Report report = ConcurrentWithdrawals.withThinkTime(
                withdrawals, item, 8, 1, LockingStrategy.PESSIMISTIC, 20);

        assertThat(report.elapsedMicros()).isGreaterThan(8 * 20 * 1_000L);
    }
}
