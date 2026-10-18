package com.gkcontas.inventory.web.dto;

/**
 * @param withdrawnUnits units the application told callers it had taken; the
 *                       gap between this and the drop in {@code quantity} is
 *                       stock handed out twice
 */
public record StockItemResponse(
        Long id,
        String sku,
        String name,
        int quantity,
        Long version,
        long withdrawnUnits,
        long reservedUnits) {
}
