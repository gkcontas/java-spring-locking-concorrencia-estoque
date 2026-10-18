package com.gkcontas.inventory.scenario;

import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.repository.ReservationRepository;
import com.gkcontas.inventory.repository.StockItemRepository;
import com.gkcontas.inventory.repository.StockMovementRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts a row back to a known quantity before each run.
 *
 * <p>Every scenario resets its own item instead of sharing one. Two concurrent
 * scenarios on the same row would measure each other, and a scenario that
 * asserts "the balance should have dropped by exactly 32" cannot share the row
 * with anything.
 */
@Service
@RequiredArgsConstructor
public class ScenarioSetup {

    private final StockItemRepository items;
    private final StockMovementRepository movements;
    private final ReservationRepository reservations;

    @Transactional
    public StockItem freshItem(String sku, int quantity) {
        StockItem item = items.findBySku(sku).orElseGet(() ->
                items.save(new StockItem(sku, "Scenario item " + sku, quantity)));
        movements.deleteByStockItemId(item.getId());
        reservations.deleteByStockItemId(item.getId());
        items.resetQuantity(item.getId(), quantity);
        return items.findById(item.getId()).orElseThrow();
    }

    @Transactional
    public List<StockItem> freshItems(String skuPrefix, int count, int quantity) {
        List<StockItem> created = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            created.add(freshItem("%s-%03d".formatted(skuPrefix, i), quantity));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public int quantityOf(Long itemId) {
        return items.findById(itemId).orElseThrow().getQuantity();
    }

    @Transactional(readOnly = true)
    public long versionOf(Long itemId) {
        return items.findById(itemId).orElseThrow().getVersion();
    }

    @Transactional(readOnly = true)
    public long promisedUnits(Long itemId) {
        return movements.promisedUnits(itemId);
    }

    @Transactional(readOnly = true)
    public long totalAttempts(Long itemId) {
        return movements.totalAttempts(itemId);
    }

    @Transactional(readOnly = true)
    public long reservedUnits(Long itemId) {
        return reservations.reservedUnits(itemId);
    }

    @Transactional(readOnly = true)
    public long reservationCount(Long itemId) {
        return reservations.countByStockItemId(itemId);
    }
}
