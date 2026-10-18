package com.gkcontas.inventory.service;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.domain.StockMovement;
import com.gkcontas.inventory.repository.StockItemRepository;
import com.gkcontas.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One transaction per withdrawal, one method per strategy.
 *
 * <p>This is a bean of its own on purpose. The retry for the optimistic
 * strategy has to start a NEW transaction on every attempt, and a method
 * calling a sibling {@code @Transactional} method on {@code this} never leaves
 * the object, so it never crosses the proxy and never starts anything -- it
 * would retry inside the transaction that already failed.
 *
 * <p>Each method takes a {@code pause} that runs between the read and the
 * write. That is not test scaffolding: the window a lost update needs is
 * exactly the time between reading a value and writing a value derived from it,
 * and in a real service that window is filled with validation, a call to
 * another system or a template being rendered. Making it a parameter makes the
 * window measurable instead of accidental.
 */
@Service
@RequiredArgsConstructor
public class TransactionalWithdrawals {

    private final StockItemRepository items;
    private final StockMovementRepository movements;

    /**
     * Read, subtract in Java, write back an absolute value. No version, no
     * lock, no condition -- and under PostgreSQL's default READ COMMITTED the
     * second writer simply overwrites the first. The row ends up on a value
     * that satisfies every constraint on the table and is still wrong.
     */
    @Transactional
    public WithdrawalOutcome unguarded(Long itemId, int requested, Runnable pause) {
        return readModifyWrite(itemId, requested, LockingStrategy.NONE, pause);
    }

    /**
     * The same code under REPEATABLE READ, which in PostgreSQL is snapshot
     * isolation: a transaction that writes a row changed since its snapshot is
     * aborted with 40001 rather than allowed to overwrite.
     *
     * <p>So the isolation level does fix the lost update -- by moving the
     * problem. The balance stops being wrong and the caller starts getting
     * errors it has to retry, which is the same obligation the optimistic
     * version creates, only with the whole transaction as the unit of conflict
     * instead of one row.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public WithdrawalOutcome unguardedRepeatableRead(Long itemId, int requested, Runnable pause) {
        return readModifyWrite(itemId, requested, LockingStrategy.NONE, pause);
    }

    private WithdrawalOutcome readModifyWrite(Long itemId, int requested,
                                              LockingStrategy strategy, Runnable pause) {
        StockItem item = items.findById(itemId)
                .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(itemId)));
        if (!item.hasAtLeast(requested)) {
            throw new InsufficientStockException(item.getQuantity(), requested);
        }
        int computed = item.getQuantity() - requested;

        pause.run();

        // Not item.withdraw() + save(): the entity carries @Version, and
        // Hibernate puts that column in the WHERE clause of every UPDATE it
        // emits for an entity that has one. Going around the entity is what
        // produces the statement a codebase WITHOUT a version column writes.
        items.overwriteQuantity(itemId, computed);
        movements.save(new StockMovement(itemId, requested, strategy, 1));
        return new WithdrawalOutcome(item.getSku(), requested, computed, strategy, 1);
    }

    /**
     * The optimistic path. Note what is NOT here: no try/catch.
     *
     * <p>The version check is not performed by any statement in this method.
     * Hibernate emits the UPDATE at flush time, and with no explicit flush that
     * is during the commit -- after this method has already returned normally.
     * A try/catch around the write, or around the whole body, catches nothing.
     * The failure belongs to the caller, outside the transaction boundary,
     * which is also the only place that can usefully retry it.
     *
     * @param attempt recorded on the movement row, so the cost of the retry is
     *                visible in the data and not only in a log line
     */
    @Transactional
    public WithdrawalOutcome optimistic(Long itemId, int requested, LockingStrategy strategy,
                                        int attempt, Runnable pause) {
        StockItem item = items.findById(itemId)
                .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(itemId)));
        if (!item.hasAtLeast(requested)) {
            throw new InsufficientStockException(item.getQuantity(), requested);
        }

        pause.run();

        item.withdraw(requested);
        movements.save(new StockMovement(itemId, requested, strategy, attempt));
        return new WithdrawalOutcome(item.getSku(), requested, item.getQuantity(), strategy, attempt);
    }

    /**
     * The pessimistic path: the row is locked by the query that loads it, so
     * every other writer queues behind this transaction's commit.
     *
     * <p>Nothing can fail the version check here -- the row cannot change
     * while the lock is held -- and Hibernate still puts the version in the
     * UPDATE, which costs nothing and bumps it. What it buys instead of
     * retries is waiting, and what it risks instead of conflicts is a deadlock
     * whenever a transaction locks more than one row.
     */
    @Transactional
    public WithdrawalOutcome pessimistic(Long itemId, int requested, boolean noWait, Runnable pause) {
        StockItem item = (noWait
                ? items.findByIdForUpdateNoWait(itemId)
                : items.findByIdForUpdate(itemId))
                .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(itemId)));
        if (!item.hasAtLeast(requested)) {
            throw new InsufficientStockException(item.getQuantity(), requested);
        }

        pause.run();

        item.withdraw(requested);
        movements.save(new StockMovement(itemId, requested, LockingStrategy.PESSIMISTIC, 1));
        return new WithdrawalOutcome(item.getSku(), requested, item.getQuantity(),
                LockingStrategy.PESSIMISTIC, 1);
    }

    /**
     * The whole decision in one statement:
     * {@code update ... set quantity = quantity - ? where id = ? and quantity >= ?}.
     *
     * <p>The row is never read into the application, so there is no window to
     * lose an update in. The business rule travels inside the WHERE clause and
     * the row count is the verdict: 1 means the units are gone, 0 means the
     * balance was short. Row locks are still taken -- a conditional UPDATE is
     * not lock-free -- but they are held for the length of one statement
     * instead of a round trip, which is why this is the cheapest of the four
     * under contention.
     *
     * <p>Two things are given up. The resulting balance is not known without
     * asking again, which is the second statement below. And the version column
     * is not touched, so a row written both this way and through the entity has
     * an optimistic check that silently stops meaning anything.
     */
    @Transactional
    public WithdrawalOutcome atomic(Long itemId, String sku, int requested, Runnable pause) {
        pause.run();

        int affected = items.withdrawAtomically(itemId, requested);
        if (affected == 0) {
            throw new InsufficientStockException(requested);
        }
        movements.save(new StockMovement(itemId, requested, LockingStrategy.ATOMIC, 1));

        int remaining = items.findById(itemId)
                .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(itemId)))
                .getQuantity();
        return new WithdrawalOutcome(sku, requested, remaining, LockingStrategy.ATOMIC, 1);
    }

    /**
     * Only here to prove the point made on {@link #optimistic}: the catch block
     * never runs. The conflict is raised by the commit, which happens after
     * this method returns, so the caller still sees the failure.
     */
    @Transactional
    public WithdrawalOutcome optimisticCaughtInside(Long itemId, int requested, Runnable pause) {
        try {
            StockItem item = items.findById(itemId)
                    .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(itemId)));
            pause.run();
            item.withdraw(requested);
            items.save(item);
            return new WithdrawalOutcome(item.getSku(), requested, item.getQuantity(),
                    LockingStrategy.OPTIMISTIC, 1);
        } catch (RuntimeException swallowed) {
            throw new IllegalStateException(
                    "unreachable for a version conflict: it is raised by the commit", swallowed);
        }
    }
}
