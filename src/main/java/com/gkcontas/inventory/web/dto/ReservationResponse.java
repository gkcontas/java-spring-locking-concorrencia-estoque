package com.gkcontas.inventory.web.dto;

public record ReservationResponse(
        String sku,
        String orderRef,
        int quantity,
        long freeAfter,
        boolean parentVersionForced) {
}
