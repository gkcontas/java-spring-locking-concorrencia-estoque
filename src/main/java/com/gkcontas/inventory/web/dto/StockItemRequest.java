package com.gkcontas.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Every message is spelled out. The built-in defaults are resolved against the
 * JVM locale, which is pt-BR on this machine, and the contract is in English.
 */
public record StockItemRequest(
        @NotBlank(message = "sku is required")
        @Size(max = 32, message = "sku must be at most 32 characters")
        String sku,

        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @Min(value = 0, message = "quantity must be zero or greater")
        int quantity) {
}
