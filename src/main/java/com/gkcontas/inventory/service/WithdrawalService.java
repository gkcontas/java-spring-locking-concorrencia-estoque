package com.gkcontas.inventory.service;

import com.gkcontas.inventory.domain.LockingStrategy;
import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * The entry point for a withdrawal, and deliberately NOT transactional.
 *
 * <p>Everything interesting about recovering from a concurrency failure lives
 * outside the transaction that failed. A rolled back transaction cannot be
 * continued, its persistence context is unusable, and the entity it read holds
 * a version that no longer exists. A retry is a new transaction with a new
 * read, and the only place to start one is here.
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private static final Runnable NO_PAUSE = () -> {
    };

    private final StockItemRepository items;
    private final TransactionalWithdrawals transactional;
    private final RetryTemplate optimisticRetryTemplate;

    public WithdrawalOutcome withdraw(String sku, int requested, LockingStrategy strategy) {
        StockItem item = items.findBySku(sku)
                .orElseThrow(() -> new StockItemNotFoundException(sku));
        return withdraw(item.getId(), item.getSku(), requested, strategy, NO_PAUSE);
    }

    public WithdrawalOutcome withdraw(Long itemId, String sku, int requested,
                                      LockingStrategy strategy, Runnable pause) {
        try {
            return switch (strategy) {
                case NONE -> transactional.unguarded(itemId, requested, pause);
                case OPTIMISTIC -> transactional.optimistic(
                        itemId, requested, LockingStrategy.OPTIMISTIC, 1, pause);
                case OPTIMISTIC_RETRY -> withRetry(itemId, requested, pause);
                case PESSIMISTIC -> transactional.pessimistic(itemId, requested, false, pause);
                case ATOMIC -> transactional.atomic(itemId, sku, requested, pause);
            };
        } catch (ConcurrentUpdateException | InsufficientStockException
                 | StockItemNotFoundException known) {
            throw known;
        } catch (RuntimeException failure) {
            throw ConcurrencyFailures.translate(failure, 1);
        }
    }

    /** Same strategy under REPEATABLE READ, for the isolation scenario. */
    public WithdrawalOutcome withdrawUnderRepeatableRead(Long itemId, int requested, Runnable pause) {
        try {
            return transactional.unguardedRepeatableRead(itemId, requested, pause);
        } catch (InsufficientStockException | StockItemNotFoundException known) {
            throw known;
        } catch (RuntimeException failure) {
            throw ConcurrencyFailures.translate(failure, 1);
        }
    }

    /** Pessimistic with {@code NOWAIT}: fails immediately instead of queueing. */
    public WithdrawalOutcome withdrawOrFailFast(Long itemId, int requested, Runnable pause) {
        try {
            return transactional.pessimistic(itemId, requested, true, pause);
        } catch (InsufficientStockException | StockItemNotFoundException known) {
            throw known;
        } catch (RuntimeException failure) {
            throw ConcurrencyFailures.translate(failure, 1);
        }
    }

    /**
     * The retry, with the whole point in two lines: the body runs
     * {@code transactional.optimistic}, so every attempt is a new transaction
     * that reads the row again. Handing the same entity back to a retry keeps
     * writing the version that already lost and never succeeds.
     *
     * <p>The pause only runs on the first attempt. It is there to open the
     * conflict window, not to be paid again on every retry.
     */
    private WithdrawalOutcome withRetry(Long itemId, int requested, Runnable pause) {
        try {
            return optimisticRetryTemplate.execute(context -> {
                int attempt = context.getRetryCount() + 1;
                return transactional.optimistic(itemId, requested,
                        LockingStrategy.OPTIMISTIC_RETRY, attempt,
                        attempt == 1 ? pause : NO_PAUSE);
            });
        } catch (RuntimeException exhausted) {
            throw ConcurrencyFailures.translate(exhausted, RetryConfig.MAX_ATTEMPTS);
        }
    }
}
