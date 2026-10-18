package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.inventory.scenario.CatchInsideResult;
import com.gkcontas.inventory.scenario.CatchInsideScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * An optimistic conflict cannot be handled inside the transaction that loses
 * it, and this is the test that says so out loud.
 */
class CatchInsideTransactionIT extends IntegrationTestBase {

    @Autowired
    private CatchInsideScenario scenario;

    @Test
    void theCatchBlockAroundTheWriteNeverRuns() {
        CatchInsideResult result = scenario.run(4);

        assertThat(result.successes()).isOne();
        assertThat(result.innerCatchFired()).isFalse();
        assertThat(result.failures()).containsEntry("conflict", 3L);
    }
}
