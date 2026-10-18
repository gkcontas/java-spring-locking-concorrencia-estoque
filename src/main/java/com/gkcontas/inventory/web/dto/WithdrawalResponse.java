package com.gkcontas.inventory.web.dto;

import com.gkcontas.inventory.domain.LockingStrategy;

public record WithdrawalResponse(
        String sku,
        int requested,
        int remaining,
        LockingStrategy strategy,
        int attempts) {
}
