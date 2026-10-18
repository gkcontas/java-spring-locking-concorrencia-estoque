package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.repository.StockMovementRepository;
import com.gkcontas.inventory.scenario.ConcurrentExecution;
import com.gkcontas.inventory.service.WithdrawalService;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The same unprotected code under a stricter isolation level.
 *
 * <p>PostgreSQL's REPEATABLE READ is snapshot isolation: a transaction that
 * writes a row changed since its snapshot is aborted with 40001 rather than
 * allowed to overwrite. So the level does close the hole -- and it closes it by
 * handing the caller an error to retry, which is the same obligation the
 * optimistic version creates. Nothing was made free, the cost moved.
 */
class IsolationLevelIT extends IntegrationTestBase {

    @Autowired
    private WithdrawalService withdrawals;
    @Autowired
    private InventoryFixtures fixtures;
    @Autowired
    private StockMovementRepository movements;

    @Test
    void repeatableReadRefusesTheSecondWriterInsteadOfLosingTheUpdate() {
        StockItem item = fixtures.newItem("ISO", 100);
        CyclicBarrier bothHaveRead = new CyclicBarrier(2);

        ConcurrentExecution.Report report = ConcurrentExecution.run(2, index ->
                withdrawals.withdrawUnderRepeatableRead(item.getId(), 1,
                        ConcurrentWithdrawals.barrier(bothHaveRead)));

        assertThat(report.successes()).isOne();
        assertThat(report.failuresByKind()).containsEntry("conflict", 1L);

        // Nothing lost, which is the difference from READ COMMITTED.
        assertThat(movements.promisedUnits(item.getId())).isOne();
        assertThat(fixtures.reload(item.getId()).getQuantity()).isEqualTo(99);
    }

    @Test
    void underContentionMostWorkersAreAbortedRatherThanQueued() {
        StockItem item = fixtures.newItem("ISO", 1000);
        CyclicBarrier allHaveRead = new CyclicBarrier(8);

        ConcurrentExecution.Report report = ConcurrentExecution.run(8, index ->
                withdrawals.withdrawUnderRepeatableRead(item.getId(), 1,
                        ConcurrentWithdrawals.barrier(allHaveRead)));

        assertThat(report.successes()).isOne();
        assertThat(report.count("conflict")).isEqualTo(7);
        assertThat(movements.promisedUnits(item.getId()))
                .isEqualTo(1000 - fixtures.reload(item.getId()).getQuantity());
    }
}
