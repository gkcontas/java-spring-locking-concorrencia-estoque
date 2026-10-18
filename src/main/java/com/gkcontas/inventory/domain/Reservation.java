package com.gkcontas.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A child of the stock item, kept around for one specific trap: an optimistic
 * version only guards the row it lives on.
 *
 * <p>Inserting a reservation does not make {@code stock_item} dirty, so
 * Hibernate has no reason to bump {@code stock_item.version}. Two concurrent
 * transactions can each read the same quantity, each decide their reservation
 * fits, and each insert -- with the version check passing on both, because
 * neither of them wrote to the parent at all.
 */
@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_item_id", nullable = false)
    private Long stockItemId;

    @Column(name = "order_ref", nullable = false, unique = true, length = 64)
    private String orderRef;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Reservation(Long stockItemId, String orderRef, int quantity) {
        this.stockItemId = stockItemId;
        this.orderRef = orderRef;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }
}
