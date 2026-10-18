package com.gkcontas.inventory.service;

/**
 * A pessimistic lock could not be taken: either the row was held and the
 * statement said {@code NOWAIT}, or the wait ran past the server's
 * {@code lock_timeout}. Both arrive as PostgreSQL 55P03.
 */
public class RowLockTimeoutException extends RuntimeException {

    public RowLockTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
