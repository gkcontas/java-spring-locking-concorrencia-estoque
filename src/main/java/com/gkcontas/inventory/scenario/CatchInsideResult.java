package com.gkcontas.inventory.scenario;

import java.util.Map;

/**
 * Proof that a {@code try/catch} around an optimistic write catches nothing.
 *
 * <p>The method under test wraps its whole body and would rethrow an
 * {@code IllegalStateException} if its catch block ever ran. It never does:
 * the conflict is raised by the commit, after the method returned. So the
 * breakdown below shows the untouched concurrency failure reaching the caller,
 * and never {@code other:IllegalStateException}.
 */
public record CatchInsideResult(
        int workers,
        long successes,
        Map<String, Long> failures,
        boolean innerCatchFired) {
}
