package com.gkcontas.inventory.service;

import com.gkcontas.inventory.repository.StockItemRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two rows, two transactions, and the only variable that decides whether the
 * database kills one of them: the order the locks are asked for.
 */
@Service
@RequiredArgsConstructor
public class CrossLockOperations {

    private final StockItemRepository items;

    /**
     * Locks two rows one after the other, in the order given. Run this with
     * one transaction asking for A then B and another asking for B then A and
     * PostgreSQL ends with a cycle it can only break by aborting a
     * participant: 40P01.
     */
    @Transactional
    public void lockInGivenOrder(Long firstId, Long secondId, Runnable betweenLocks) {
        items.findByIdForUpdate(firstId)
                .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(firstId)));
        betweenLocks.run();
        items.findByIdForUpdate(secondId)
                .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(secondId)));
    }

    /**
     * The fix, and it is not a retry. Both sides sort the ids and take the
     * locks in one statement, so there is no order in which they can disagree.
     * Primary keys work because every participant can compute the same order
     * without coordinating.
     */
    @Transactional
    public void lockInAgreedOrder(List<Long> ids, Runnable betweenLocks) {
        items.lockAllOrderedById(ids.stream().sorted(Comparator.naturalOrder()).toList());
        betweenLocks.run();
    }

    /**
     * Takes the row lock and keeps it for a while, so another transaction can
     * be timed waiting for it.
     */
    @Transactional
    public void holdLock(Long itemId, long holdMillis) {
        items.findByIdForUpdate(itemId)
                .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(itemId)));
        try {
            Thread.sleep(holdMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Waits for the lock, bounded only by the server's {@code lock_timeout}. */
    @Transactional
    public void takeLockWaiting(Long itemId) {
        items.findByIdForUpdate(itemId)
                .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(itemId)));
    }

    /** Refuses to wait at all: {@code FOR UPDATE NOWAIT}, 55P03 on contact. */
    @Transactional
    public void takeLockOrFail(Long itemId) {
        items.findByIdForUpdateNoWait(itemId)
                .orElseThrow(() -> new StockItemNotFoundException(String.valueOf(itemId)));
    }
}
