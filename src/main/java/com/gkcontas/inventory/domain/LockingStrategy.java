package com.gkcontas.inventory.domain;

/**
 * The five ways this project takes units off a stock row.
 *
 * <p>They differ in where the race is resolved: in nobody's hands
 * ({@code NONE}), in a version column checked after the fact
 * ({@code OPTIMISTIC}), in the application's retry budget
 * ({@code OPTIMISTIC_RETRY}), in a row lock held by the database
 * ({@code PESSIMISTIC}), or inside a single statement that never reads the row
 * into the application at all ({@code ATOMIC}).
 */
public enum LockingStrategy {

    /** Read, subtract in Java, write. The lost update lives here. */
    NONE,

    /** {@code @Version} in the WHERE clause; the loser gets 409 and no retry. */
    OPTIMISTIC,

    /** Same check, with a bounded retry that re-reads before each attempt. */
    OPTIMISTIC_RETRY,

    /** {@code SELECT ... FOR UPDATE} in the same query that loads the row. */
    PESSIMISTIC,

    /** One conditional UPDATE; the database decides, the application never reads. */
    ATOMIC
}
