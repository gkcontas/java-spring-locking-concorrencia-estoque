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
 * The failing test. Without protection the balance ends up on a value that is
 * legal, positive, and one unit short per worker that lost.
 */
class LostUpdateIT extends IntegrationTestBase {

    @Autowired
    private WithdrawalService withdrawals;
    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private StockMovementRepository movements;

    @Test
    void twoWorkersBothSucceedAndOneUnitIsHandedOutTwice() {
        StockItem item = fixtures.newItem("LOST", 100);

        ConcurrentExecution.Report report = ConcurrentWithdrawals.allReadThenWrite(
                withdrawals, item, 2, 1, LockingStrategy.NONE);

        assertThat(report.successes()).isEqualTo(2);
        assertThat(report.failuresByKind()).isEmpty();

        // Two units were promised to callers and only one left the shelf.
        assertThat(movements.promisedUnits(item.getId())).isEqualTo(2);
        assertThat(fixtures.reload(item.getId()).getQuantity()).isEqualTo(99);
    }

    @Test
    void sixteenWorkersLoseFifteenUnits() {
        StockItem item = fixtures.newItem("LOST", 1000);

        ConcurrentExecution.Report report = ConcurrentWithdrawals.allReadThenWrite(
                withdrawals, item, 16, 1, LockingStrategy.NONE);

        assertThat(report.successes()).isEqualTo(16);
        assertThat(movements.promisedUnits(item.getId())).isEqualTo(16);
        assertThat(fixtures.reload(item.getId()).getQuantity()).isEqualTo(999);
    }

    /**
     * The version column exists on this row and still does nothing, because the
     * unguarded write never puts it in the WHERE clause. An optimistic check is
     * a property of the statement, not of the schema.
     */
    @Test
    void theVersionColumnIsNotTouchedByAnUnguardedWrite() {
        StockItem item = fixtures.newItem("LOST", 100);

        ConcurrentWithdrawals.allReadThenWrite(withdrawals, item, 4, 1, LockingStrategy.NONE);

        assertThat(fixtures.reload(item.getId()).getVersion()).isZero();
    }
}
