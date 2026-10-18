package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.inventory.scenario.LockWaitResult;
import com.gkcontas.inventory.scenario.LockWaitScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * What the second caller experiences while somebody else holds the row.
 */
class LockWaitIT extends IntegrationTestBase {

    private static final long SERVER_LOCK_TIMEOUT_MILLIS = 2_000;

    @Autowired
    private LockWaitScenario scenario;

    @Test
    void noWaitFailsOnContactWhileAPlainLockQueues() {
        LockWaitResult result = scenario.run(600, SERVER_LOCK_TIMEOUT_MILLIS);

        assertThat(result.noWaitOutcome()).isEqualTo("locked");
        assertThat(result.noWaitMillis()).isLessThan(200);

        assertThat(result.waitingOutcome()).isEqualTo("ok");
        assertThat(result.waitingMillis()).isGreaterThan(50);
    }

    /**
     * And the reason {@code lock_timeout} belongs in the server configuration:
     * without it this wait has no end. The request does not fail, it hangs,
     * which is considerably harder to find than an error.
     */
    @Test
    void aWaitLongerThanTheServerTimeoutBecomesAnError() {
        LockWaitResult result = scenario.run(5_000, SERVER_LOCK_TIMEOUT_MILLIS);

        assertThat(result.waitingOutcome()).isEqualTo("locked");
        assertThat(result.waitingMillis())
                .isGreaterThan(SERVER_LOCK_TIMEOUT_MILLIS - 400)
                .isLessThan(SERVER_LOCK_TIMEOUT_MILLIS + 1_500);
    }
}
