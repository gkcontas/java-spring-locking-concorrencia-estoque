package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.inventory.scenario.DeadlockResult;
import com.gkcontas.inventory.scenario.DeadlockScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A deadlock is not a property of pessimistic locking. It is a property of
 * taking more than one lock in an order nobody agreed on.
 */
class DeadlockIT extends IntegrationTestBase {

    @Autowired
    private DeadlockScenario scenario;

    @Test
    void crossingTheLockOrderGetsOneTransactionKilled() {
        DeadlockResult result = scenario.crossedOrder();

        assertThat(result.deadlocks()).isOne();
        assertThat(result.successes()).isOne();
    }

    /**
     * Same rows, same two transactions, same contention -- and nothing fails,
     * because both sides sort by primary key and there is no order left to
     * disagree about. No retry was added.
     */
    @Test
    void agreeingOnTheOrderRemovesTheDeadlockEntirely() {
        DeadlockResult result = scenario.agreedOrder();

        assertThat(result.deadlocks()).isZero();
        assertThat(result.successes()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
    }
}
