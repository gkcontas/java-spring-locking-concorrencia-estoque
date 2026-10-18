package com.gkcontas.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.inventory.scenario.ReservationGuardResult;
import com.gkcontas.inventory.scenario.ReservationGuardScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * An optimistic version guards the row it lives on, not the invariant the team
 * thinks it is guarding.
 */
class ReservationVersionIT extends IntegrationTestBase {

    @Autowired
    private ReservationGuardScenario scenario;

    @Test
    void twoReservationsThatDoNotFitTogetherBothCommit() {
        ReservationGuardResult result = scenario.run(false, 2, 10, 6);

        assertThat(result.successes()).isEqualTo(2);
        assertThat(result.reservedUnits()).isEqualTo(12);
        assertThat(result.oversoldUnits()).isEqualTo(2);

        // The explanation, in one assertion: nobody wrote to the parent, so
        // its version never moved, so the check never happened.
        assertThat(result.finalVersion()).isZero();
    }

    @Test
    void forcingTheParentIncrementSerializesTheChildWrites() {
        ReservationGuardResult result = scenario.run(true, 2, 10, 6);

        assertThat(result.successes()).isOne();
        assertThat(result.failures()).containsEntry("conflict", 1L);
        assertThat(result.reservedUnits()).isEqualTo(6);
        assertThat(result.oversoldUnits()).isZero();
        assertThat(result.finalVersion()).isOne();
    }

    @Test
    void theForcedIncrementHoldsWithMoreWorkers() {
        ReservationGuardResult result = scenario.run(true, 8, 10, 6);

        assertThat(result.successes()).isOne();
        assertThat(result.reservationCount()).isOne();
        assertThat(result.oversoldUnits()).isZero();
    }
}
