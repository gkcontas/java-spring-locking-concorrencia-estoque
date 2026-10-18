package com.gkcontas.inventory;

import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.repository.StockItemRepository;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every test gets its own row.
 *
 * <p>Sharing one is tempting and wrong: a test that asserts "the balance
 * dropped by exactly 16" cannot share the row with anything, and two
 * concurrency tests on the same row measure each other instead of the strategy
 * under test.
 */
@Component
public class InventoryFixtures {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    private StockItemRepository items;

    @Transactional
    public StockItem newItem(String prefix, int quantity) {
        String sku = "%s-%04d".formatted(prefix, SEQUENCE.incrementAndGet());
        return items.save(new StockItem(sku, prefix + " test item", quantity));
    }

    @Transactional(readOnly = true)
    public StockItem reload(Long id) {
        return items.findById(id).orElseThrow();
    }
}
