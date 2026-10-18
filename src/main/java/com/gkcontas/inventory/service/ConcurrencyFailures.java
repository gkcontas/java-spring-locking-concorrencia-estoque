package com.gkcontas.inventory.service;

import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Turns the database's own answer about a failed write into something the API
 * can map to a status code.
 *
 * <p>Spring's exception translation is deliberately not trusted to be specific
 * here. A serialization failure, a deadlock victim and a lock timeout all land
 * on subclasses of {@code ConcurrencyFailureException}, and which subclass
 * depends on the translator in use and the driver's error metadata. The SQL
 * state does not: PostgreSQL always says 40001, 40P01 or 55P03, and that is
 * what this class reads.
 */
public final class ConcurrencyFailures {

    /** could not serialize access due to concurrent update */
    public static final String SERIALIZATION_FAILURE = "40001";
    /** deadlock detected */
    public static final String DEADLOCK_DETECTED = "40P01";
    /** could not obtain lock on row / lock timeout */
    public static final String LOCK_NOT_AVAILABLE = "55P03";
    /** check constraint violated -- here, quantity would have gone negative */
    public static final String CHECK_VIOLATION = "23514";

    private ConcurrencyFailures() {
    }

    /**
     * Rethrows the domain exception that matches the failure, or the original
     * if it is not a concurrency problem at all.
     */
    public static RuntimeException translate(RuntimeException original, int attempts) {
        if (original instanceof OptimisticLockingFailureException e) {
            // No SQL state to read: the statement itself succeeded, it just
            // matched zero rows, and Hibernate turned the zero into this.
            return new ConcurrentUpdateException(
                    "the row was changed by another transaction between the read and the write",
                    attempts, e);
        }
        String state = sqlState(original);
        if (state == null) {
            return original;
        }
        return switch (state) {
            case SERIALIZATION_FAILURE -> new ConcurrentUpdateException(
                    "the snapshot this transaction read was invalidated by a concurrent commit",
                    attempts, original);
            case DEADLOCK_DETECTED -> new DeadlockDetectedException(
                    "chosen as the deadlock victim; the locks were taken in conflicting order",
                    original);
            case LOCK_NOT_AVAILABLE -> new RowLockTimeoutException(
                    "the row is locked by another transaction and the wait ran out", original);
            case CHECK_VIOLATION -> new InsufficientStockException(0);
            default -> original;
        };
    }

    public static String sqlState(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    public static boolean isConcurrencyFailure(RuntimeException e) {
        if (e instanceof OptimisticLockingFailureException) {
            return true;
        }
        if (!(e instanceof DataAccessException)) {
            return false;
        }
        String state = sqlState(e);
        return SERIALIZATION_FAILURE.equals(state)
                || DEADLOCK_DETECTED.equals(state)
                || LOCK_NOT_AVAILABLE.equals(state);
    }
}
