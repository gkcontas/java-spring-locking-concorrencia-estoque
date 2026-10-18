package com.gkcontas.inventory.service;

import com.gkcontas.inventory.domain.LockingStrategy;

/**
 * @param attempts how many transactions it took; above 1 only for
 *                 {@code OPTIMISTIC_RETRY}
 */
public record WithdrawalOutcome(
        String sku,
        int requested,
        int remaining,
        LockingStrategy strategy,
        int attempts) {
}
