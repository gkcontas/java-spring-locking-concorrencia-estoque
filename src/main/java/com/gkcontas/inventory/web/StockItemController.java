package com.gkcontas.inventory.web;

import com.gkcontas.inventory.service.ReservationService;
import com.gkcontas.inventory.service.StockItemNotFoundException;
import com.gkcontas.inventory.service.StockItemService;
import com.gkcontas.inventory.service.WithdrawalOutcome;
import com.gkcontas.inventory.service.WithdrawalService;
import com.gkcontas.inventory.repository.StockItemRepository;
import com.gkcontas.inventory.web.dto.ReservationRequest;
import com.gkcontas.inventory.web.dto.ReservationResponse;
import com.gkcontas.inventory.web.dto.StockItemRequest;
import com.gkcontas.inventory.web.dto.StockItemResponse;
import com.gkcontas.inventory.web.dto.WithdrawalRequest;
import com.gkcontas.inventory.web.dto.WithdrawalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stock-items")
@RequiredArgsConstructor
public class StockItemController {

    private final StockItemService stockItems;
    private final StockItemRepository items;
    private final WithdrawalService withdrawals;
    private final ReservationService reservations;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockItemResponse create(@Valid @RequestBody StockItemRequest request) {
        return stockItems.create(request.sku(), request.name(), request.quantity());
    }

    @GetMapping("/{sku}")
    public StockItemResponse findBySku(@PathVariable String sku) {
        return stockItems.findBySku(sku);
    }

    @PostMapping("/{sku}/withdrawals")
    public WithdrawalResponse withdraw(@PathVariable String sku,
                                       @Valid @RequestBody WithdrawalRequest request) {
        WithdrawalOutcome outcome = withdrawals.withdraw(sku, request.quantity(), request.strategy());
        return new WithdrawalResponse(outcome.sku(), outcome.requested(), outcome.remaining(),
                outcome.strategy(), outcome.attempts());
    }

    @PostMapping("/{sku}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@PathVariable String sku,
                                       @Valid @RequestBody ReservationRequest request) {
        Long itemId = items.findBySku(sku)
                .orElseThrow(() -> new StockItemNotFoundException(sku))
                .getId();
        long freeAfter = request.forceParentIncrement()
                ? reservations.reserveWithForceIncrement(itemId, request.orderRef(), request.quantity(), () -> {
        })
                : reservations.reserveUnguarded(itemId, request.orderRef(), request.quantity(), () -> {
        });
        return new ReservationResponse(sku, request.orderRef(), request.quantity(), freeAfter,
                request.forceParentIncrement());
    }
}
