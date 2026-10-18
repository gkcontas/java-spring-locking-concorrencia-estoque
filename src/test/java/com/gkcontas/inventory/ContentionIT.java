package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.scenario.ContentionResult;
import com.gkcontas.inventory.scenario.ContentionScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spreading the same workers over more rows.
 *
 * <p>The assertions here are about correctness, not about timings. Throughput
 * on a shared machine is a measurement, not a contract, and a test that asserts
 * one strategy is faster than another is a test that fails on a busy laptop.
 * The numbers in the README come from a dedicated run against the packaged jar.
 */
class ContentionIT extends IntegrationTestBase {

    @Autowired
    private ContentionScenario scenario;

    @Test
    void aHotRowIsStillCorrectUnderTheAtomicUpdate() {
        ContentionResult result = scenario.run(LockingStrategy.ATOMIC, 32, 1, 1, 1_000, 0);

        assertThat(result.successes()).isEqualTo(32);
        assertThat(result.unitsLost()).isZero();
        assertThat(result.transactionsUsed()).isEqualTo(32);
    }

    @Test
    void spreadingTheWorkersRemovesTheRetriesFromTheOptimisticPath() {
        ContentionResult hot = scenario.run(LockingStrategy.OPTIMISTIC_RETRY, 32, 1, 1, 1_000, 0);
        ContentionResult spread = scenario.run(LockingStrategy.OPTIMISTIC_RETRY, 32, 32, 1, 1_000, 0);

        assertThat(hot.unitsLost()).isZero();
        assertThat(spread.unitsLost()).isZero();
        assertThat(spread.successes()).isEqualTo(32);
        assertThat(spread.transactionsUsed()).isEqualTo(32);
        assertThat(hot.transactionsUsed()).isGreaterThan(spread.transactionsUsed());
    }

    @Test
    void theUnguardedStrategyLosesUnitsOnAHotRowAndNoneWhenSpread() {
        ContentionResult hot = scenario.run(LockingStrategy.NONE, 32, 1, 1, 1_000, 5);
        ContentionResult spread = scenario.run(LockingStrategy.NONE, 32, 32, 1, 1_000, 5);

        assertThat(hot.unitsLost()).isPositive();
        assertThat(spread.unitsLost()).isZero();
    }
}
