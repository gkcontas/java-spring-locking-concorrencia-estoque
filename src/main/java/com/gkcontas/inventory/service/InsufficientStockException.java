package com.gkcontas.inventory.service;

/**
 * The balance is genuinely short. Retrying will not help, which is why it is
 * kept apart from {@link ConcurrentUpdateException}: the caller needs to tell
 * "come back later" from "there is none left".
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(int available, int requested) {
        super("available quantity %d is below the requested %d".formatted(available, requested));
    }

    public InsufficientStockException(int requested) {
        super("the available quantity is below the requested %d".formatted(requested));
    }
}
