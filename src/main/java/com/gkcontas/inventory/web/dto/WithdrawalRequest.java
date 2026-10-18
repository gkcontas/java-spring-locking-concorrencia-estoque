package com.gkcontas.inventory.web.dto;

import com.gkcontas.inventory.domain.LockingStrategy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WithdrawalRequest(
        @Min(value = 1, message = "quantity must be at least 1")
        int quantity,

        @NotNull(message = "strategy is required: NONE, OPTIMISTIC, OPTIMISTIC_RETRY, PESSIMISTIC or ATOMIC")
        LockingStrategy strategy) {
}
