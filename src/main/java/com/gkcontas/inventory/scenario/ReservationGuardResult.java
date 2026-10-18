package com.gkcontas.inventory.scenario;

import java.util.Map;

/**
 * The trap that an optimistic version guards one row, not an invariant.
 *
 * @param mode             "unguarded" or "force-increment"
 * @param reservedUnits    the sum of the reservations that committed
 * @param oversoldUnits    {@code reservedUnits - initialQuantity} when positive:
 *                         units promised twice with the version check passing
 *                         on both transactions
 * @param finalVersion     unchanged under "unguarded", which is the whole
 *                         explanation
 */
public record ReservationGuardResult(
        String mode,
        int workers,
        int initialQuantity,
        int quantityEach,
        long successes,
        Map<String, Long> failures,
        long reservedUnits,
        long reservationCount,
        long oversoldUnits,
        long finalVersion) {
}
