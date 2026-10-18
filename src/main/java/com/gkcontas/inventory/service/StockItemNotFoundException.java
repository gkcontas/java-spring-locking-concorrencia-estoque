package com.gkcontas.inventory.service;

public class StockItemNotFoundException extends RuntimeException {

    public StockItemNotFoundException(String reference) {
        super("stock item not found: " + reference);
    }
}
