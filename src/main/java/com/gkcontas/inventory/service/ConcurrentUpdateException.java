package com.gkcontas.inventory.service;

/**
 * Somebody else wrote the row first. The work was not done and the transaction
 * is gone, but the request is perfectly valid -- it only needs to be made again
 * against a fresh read.
 */
public class ConcurrentUpdateException extends RuntimeException {

    private final int attempts;

    public ConcurrentUpdateException(String message, int attempts, Throwable cause) {
        super(message, cause);
        this.attempts = attempts;
    }

    public int getAttempts() {
        return attempts;
    }
}
