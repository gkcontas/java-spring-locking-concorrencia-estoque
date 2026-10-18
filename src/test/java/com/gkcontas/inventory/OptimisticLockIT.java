package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.repository.StockMovementRepository;
import com.gkcontas.inventory.scenario.ConcurrentExecution;
import com.gkcontas.inventory.service.ConcurrentUpdateException;
import com.gkcontas.inventory.service.InsufficientStockException;
import com.gkcontas.inventory.service.WithdrawalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OptimisticLockIT extends IntegrationTestBase {

    @Autowired
    private WithdrawalService withdrawals;
    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private StockMovementRepository movements;

    @Test
    void oneWorkerWinsAndTheOtherIsToldToTryAgain() {
        StockItem item = fixtures.newItem("OPT", 100);

        ConcurrentExecution.Report report = ConcurrentWithdrawals.allReadThenWrite(
                withdrawals, item, 2, 1, LockingStrategy.OPTIMISTIC);

        assertThat(report.successes()).isOne();
        assertThat(report.failuresByKind()).containsEntry("conflict", 1L);
        assertThat(fixtures.reload(item.getId()).getQuantity()).isEqualTo(99);
        assertThat(movements.promisedUnits(item.getId())).isOne();
    }

    /**
     * The loser's transaction is rolled back whole. Its movement row was
     * inserted before the version check failed and it is not in the table --
     * the units promised and the units removed stay equal, which is the only
     * invariant that matters.
     */
    @Test
    void theLosersMovementRowIsRolledBackWithIt() {
        StockItem item = fixtures.newItem("OPT", 100);

        ConcurrentWithdrawals.allReadThenWrite(withdrawals, item, 8, 1, LockingStrategy.OPTIMISTIC);

        long promised = movements.promisedUnits(item.getId());
        long removed = 100 - fixtures.reload(item.getId()).getQuantity();
        assertThat(promised).isEqualTo(removed);
        assertThat(movements.countByStockItemId(item.getId())).isEqualTo(promised);
    }

    @Test
    void theVersionAdvancesOncePerSuccessfulWrite() {
        StockItem item = fixtures.newItem("OPT", 100);

        ConcurrentExecution.Report report = ConcurrentWithdrawals.allReadThenWrite(
                withdrawals, item, 8, 1, LockingStrategy.OPTIMISTIC);

        assertThat(fixtures.reload(item.getId()).getVersion()).isEqualTo(report.successes());
    }

    @Test
    void aShortBalanceIsNotAConflict() {
        StockItem item = fixtures.newItem("OPT", 3);

        assertThatThrownBy(() -> withdrawals.withdraw(item.getSku(), 10, LockingStrategy.OPTIMISTIC))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(fixtures.reload(item.getId()).getQuantity()).isEqualTo(3);
    }

    @Test
    void aConflictCarriesTheAttemptCount() {
        StockItem item = fixtures.newItem("OPT", 100);
        java.util.concurrent.CyclicBarrier bothHaveRead = new java.util.concurrent.CyclicBarrier(2);

        ConcurrentExecution.Report report = ConcurrentExecution.run(2, index -> {
            try {
                withdrawals.withdraw(item.getId(), item.getSku(), 1, LockingStrategy.OPTIMISTIC,
                        ConcurrentWithdrawals.barrier(bothHaveRead));
            } catch (ConcurrentUpdateException e) {
                assertThat(e.getAttempts()).isOne();
                throw e;
            }
        });

        assertThat(report.count("conflict")).isOne();
    }
}
