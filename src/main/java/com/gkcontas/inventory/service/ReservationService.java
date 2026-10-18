package com.gkcontas.inventory.service;

import com.gkcontas.inventory.domain.Reservation;
import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.repository.ReservationRepository;
import com.gkcontas.inventory.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reservation path exists for one trap: an optimistic version guards the
 * row it lives on, and nothing else.
 *
 * <p>A reservation does not change the stock item. It only reads it, to check
 * that the request fits in what is left. Hibernate sees a clean parent and a
 * new child, emits an INSERT and no UPDATE, and the version check that the
 * team believes is protecting the invariant never runs. Both transactions
 * commit, both reservations exist, and together they exceed the quantity.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final StockItemRepository items;
    private final ReservationRepository reservations;

    /** No protection at all, despite the item carrying {@code @Version}. */
    @Transactional
    public long reserveUnguarded(Long itemId, String orderRef, int quantity, Runnable pause) {
        return reserve(items.findById(itemId), itemId, orderRef, quantity, pause);
    }

    /**
     * The same code with {@code OPTIMISTIC_FORCE_INCREMENT} on the read, which
     * makes the parent's version move even though nothing on it changed. The
     * second transaction to commit finds the version it read is gone and is
     * rolled back.
     */
    @Transactional
    public long reserveWithForceIncrement(Long itemId, String orderRef, int quantity, Runnable pause) {
        return reserve(items.findByIdForceIncrement(itemId), itemId, orderRef, quantity, pause);
    }

    private long reserve(java.util.Optional<StockItem> loaded, Long itemId, String orderRef,
                         int quantity, Runnable pause) {
        StockItem item = loaded.orElseThrow(() -> new StockItemNotFoundException(String.valueOf(itemId)));
        long alreadyReserved = reservations.reservedUnits(itemId);
        long free = item.getQuantity() - alreadyReserved;
        if (free < quantity) {
            throw new InsufficientStockException((int) free, quantity);
        }

        pause.run();

        reservations.save(new Reservation(itemId, orderRef, quantity));
        return free - quantity;
    }
}
