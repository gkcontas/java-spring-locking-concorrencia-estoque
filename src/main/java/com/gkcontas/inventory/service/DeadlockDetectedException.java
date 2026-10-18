package com.gkcontas.inventory.service;

/**
 * PostgreSQL 40P01. Two transactions each held what the other wanted, and the
 * server broke the cycle by killing one of them. The victim is not at fault and
 * cannot prevent it alone -- the fix is an agreed lock order on both sides.
 */
public class DeadlockDetectedException extends RuntimeException {

    public DeadlockDetectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
