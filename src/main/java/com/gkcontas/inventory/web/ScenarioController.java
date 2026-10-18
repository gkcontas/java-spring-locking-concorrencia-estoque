package com.gkcontas.inventory.web;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.scenario.CatchInsideResult;
import com.gkcontas.inventory.scenario.CatchInsideScenario;
import com.gkcontas.inventory.scenario.ContentionResult;
import com.gkcontas.inventory.scenario.ContentionScenario;
import com.gkcontas.inventory.scenario.DeadlockResult;
import com.gkcontas.inventory.scenario.DeadlockScenario;
import com.gkcontas.inventory.scenario.LockWaitResult;
import com.gkcontas.inventory.scenario.LockWaitScenario;
import com.gkcontas.inventory.scenario.ReservationGuardResult;
import com.gkcontas.inventory.scenario.ReservationGuardScenario;
import com.gkcontas.inventory.scenario.StrategyRunResult;
import com.gkcontas.inventory.scenario.StrategyScenario;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The scenarios behind the numbers in the README, each reachable on its own so
 * a measurement can be reproduced instead of taken on trust.
 *
 * <p>All of them reset their own row first and report what the database says
 * afterwards, not what the application believed while it was running.
 */
@RestController
@RequestMapping("/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final StrategyScenario strategies;
    private final ContentionScenario contention;
    private final DeadlockScenario deadlocks;
    private final LockWaitScenario lockWaits;
    private final ReservationGuardScenario reservationGuards;
    private final CatchInsideScenario catchInside;

    /** One strategy, N workers, one row. */
    @PostMapping("/strategies/{strategy}")
    public StrategyRunResult strategy(@PathVariable LockingStrategy strategy,
                                      @RequestParam(defaultValue = "32") int workers,
                                      @RequestParam(defaultValue = "1") int quantityEach,
                                      @RequestParam(defaultValue = "1000") int initialQuantity,
                                      @RequestParam(defaultValue = "5") long thinkTimeMillis) {
        return strategies.run(strategy, workers, quantityEach, initialQuantity, thinkTimeMillis);
    }

    /** All five, back to back, on the same shape of workload. */
    @PostMapping("/strategies")
    public List<StrategyRunResult> allStrategies(@RequestParam(defaultValue = "32") int workers,
                                                 @RequestParam(defaultValue = "1") int quantityEach,
                                                 @RequestParam(defaultValue = "1000") int initialQuantity,
                                                 @RequestParam(defaultValue = "5") long thinkTimeMillis) {
        return strategies.compareAll(workers, quantityEach, initialQuantity, thinkTimeMillis);
    }

    /** No application protection, REPEATABLE READ instead of READ COMMITTED. */
    @PostMapping("/repeatable-read")
    public StrategyRunResult repeatableRead(@RequestParam(defaultValue = "32") int workers,
                                            @RequestParam(defaultValue = "1") int quantityEach,
                                            @RequestParam(defaultValue = "1000") int initialQuantity,
                                            @RequestParam(defaultValue = "5") long thinkTimeMillis) {
        return strategies.runUnderRepeatableRead(workers, quantityEach, initialQuantity,
                thinkTimeMillis);
    }

    /** Pessimistic with {@code NOWAIT}: nobody queues. */
    @PostMapping("/no-wait")
    public StrategyRunResult noWait(@RequestParam(defaultValue = "32") int workers,
                                    @RequestParam(defaultValue = "1") int quantityEach,
                                    @RequestParam(defaultValue = "1000") int initialQuantity,
                                    @RequestParam(defaultValue = "5") long thinkTimeMillis) {
        return strategies.runFailFast(workers, quantityEach, initialQuantity, thinkTimeMillis);
    }

    /** Hot row against spread rows, same worker count. */
    @PostMapping("/contention/{strategy}")
    public ContentionResult contention(@PathVariable LockingStrategy strategy,
                                       @RequestParam(defaultValue = "32") int workers,
                                       @RequestParam(defaultValue = "1") int rows,
                                       @RequestParam(defaultValue = "1") int quantityEach,
                                       @RequestParam(defaultValue = "1000") int initialQuantity,
                                       @RequestParam(defaultValue = "0") long thinkTimeMillis) {
        return contention.run(strategy, workers, rows, quantityEach, initialQuantity,
                thinkTimeMillis);
    }

    @PostMapping("/deadlock/crossed")
    public DeadlockResult deadlockCrossed() {
        return deadlocks.crossedOrder();
    }

    @PostMapping("/deadlock/agreed")
    public DeadlockResult deadlockAgreed() {
        return deadlocks.agreedOrder();
    }

    @PostMapping("/lock-wait")
    public LockWaitResult lockWait(@RequestParam(defaultValue = "500") long holdMillis,
                                   @RequestParam(defaultValue = "2000") long serverLockTimeoutMillis) {
        return lockWaits.run(holdMillis, serverLockTimeoutMillis);
    }

    @PostMapping("/reservations")
    public ReservationGuardResult reservations(
            @RequestParam(defaultValue = "false") boolean forceParentIncrement,
            @RequestParam(defaultValue = "2") int workers,
            @RequestParam(defaultValue = "10") int initialQuantity,
            @RequestParam(defaultValue = "6") int quantityEach) {
        return reservationGuards.run(forceParentIncrement, workers, initialQuantity, quantityEach);
    }

    @PostMapping("/catch-inside")
    public CatchInsideResult catchInside(@RequestParam(defaultValue = "2") int workers) {
        return catchInside.run(workers);
    }
}
