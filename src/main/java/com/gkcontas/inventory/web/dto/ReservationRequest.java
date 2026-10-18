package com.gkcontas.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservationRequest(
        @NotBlank(message = "orderRef is required")
        @Size(max = 64, message = "orderRef must be at most 64 characters")
        String orderRef,

        @Min(value = 1, message = "quantity must be at least 1")
        int quantity,

        /** Whether to bump the parent's version even though nothing on it changes. */
        boolean forceParentIncrement) {
}
