package com.gkcontas.inventory.service;

import com.gkcontas.inventory.domain.StockItem;
import com.gkcontas.inventory.repository.ReservationRepository;
import com.gkcontas.inventory.repository.StockItemRepository;
import com.gkcontas.inventory.repository.StockMovementRepository;
import com.gkcontas.inventory.web.dto.StockItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockItemService {

    private final StockItemRepository items;
    private final StockMovementRepository movements;
    private final ReservationRepository reservations;

    @Transactional
    public StockItemResponse create(String sku, String name, int quantity) {
        StockItem saved = items.save(new StockItem(sku, name, quantity));
        return toResponse(saved, 0, 0);
    }

    /**
     * The response is assembled inside the transaction. With
     * {@code open-in-view} off there is no session left while the response is
     * serialised, and the aggregate counts are two extra queries that have to
     * happen here or not at all.
     */
    @Transactional(readOnly = true)
    public StockItemResponse findBySku(String sku) {
        StockItem item = items.findBySku(sku)
                .orElseThrow(() -> new StockItemNotFoundException(sku));
        return toResponse(item,
                movements.promisedUnits(item.getId()),
                reservations.reservedUnits(item.getId()));
    }

    private StockItemResponse toResponse(StockItem item, long withdrawnUnits, long reservedUnits) {
        return new StockItemResponse(item.getId(), item.getSku(), item.getName(),
                item.getQuantity(), item.getVersion(), withdrawnUnits, reservedUnits);
    }
}
