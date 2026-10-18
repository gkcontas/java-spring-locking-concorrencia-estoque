package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.repository.StockMovementRepository;
import com.gkcontas.inventory.scenario.ConcurrentExecution;
import com.gkcontas.inventory.service.InsufficientStockException;
import com.gkcontas.inventory.service.WithdrawalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AtomicUpdateIT extends IntegrationTestBase {

    @Autowired
    private WithdrawalService withdrawals;
    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private StockMovementRepository movements;

    @Test
    void everyWorkerSucceedsWithoutConflictOrRetry() {
        StockItem item = fixtures.newItem("ATOMIC", 1000);

        ConcurrentExecution.Report report = ConcurrentWithdrawals.allReadThenWrite(
                withdrawals, item, 16, 1, LockingStrategy.ATOMIC);

        assertThat(report.successes()).isEqualTo(16);
        assertThat(report.failuresByKind()).isEmpty();
        assertThat(movements.promisedUnits(item.getId())).isEqualTo(16);
        assertThat(fixtures.reload(item.getId()).getQuantity()).isEqualTo(984);
    }

    /**
     * The condition in the WHERE clause is the business rule, so the last
     * workers to arrive at an almost empty row are simply told there is not
     * enough -- without anybody reading the balance first.
     */
    @Test
    void theWhereClauseStopsTheBatchExactlyAtZero() {
        StockItem item = fixtures.newItem("ATOMIC", 10);

        ConcurrentExecution.Report report = ConcurrentWithdrawals.allReadThenWrite(
                withdrawals, item, 16, 1, LockingStrategy.ATOMIC);

        assertThat(report.successes()).isEqualTo(10);
        assertThat(report.failuresByKind()).containsEntry("insufficient", 6L);
        assertThat(fixtures.reload(item.getId()).getQuantity()).isZero();
    }

    @Test
    void aSingleShortRequestIsRejectedWithoutTouchingTheRow() {
        StockItem item = fixtures.newItem("ATOMIC", 3);

        assertThatThrownBy(() -> withdrawals.withdraw(item.getSku(), 4, LockingStrategy.ATOMIC))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(fixtures.reload(item.getId()).getQuantity()).isEqualTo(3);
    }

    /**
     * The trap worth remembering: the conditional UPDATE does not touch the
     * version column. A row written both this way and through the entity has an
     * optimistic check that quietly stops protecting anything, because the
     * version it compares no longer moves with every change.
     */
    @Test
    void theVersionIsLeftBehind() {
        StockItem item = fixtures.newItem("ATOMIC", 1000);

        ConcurrentWithdrawals.allReadThenWrite(withdrawals, item, 8, 1, LockingStrategy.ATOMIC);

        StockItem reloaded = fixtures.reload(item.getId());
        assertThat(reloaded.getQuantity()).isEqualTo(992);
        assertThat(reloaded.getVersion()).isZero();
    }
}
