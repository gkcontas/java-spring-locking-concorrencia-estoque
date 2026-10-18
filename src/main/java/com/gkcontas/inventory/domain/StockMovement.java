package com.gkcontas.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per withdrawal the application accepted.
 *
 * <p>This table is what makes a lost update a number instead of a story. The
 * units the application promised to callers are the sum of these rows; the
 * units actually taken off the shelf are the drop in
 * {@link StockItem#getQuantity()}. Under no protection the two disagree, and
 * the gap is stock that was handed out twice.
 */
@Entity
@Table(name = "stock_movement")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_item_id", nullable = false)
    private Long stockItemId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LockingStrategy strategy;

    /** How many times the write had to be attempted before it stuck. */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public StockMovement(Long stockItemId, int quantity, LockingStrategy strategy, int attempts) {
        this.stockItemId = stockItemId;
        this.quantity = quantity;
        this.strategy = strategy;
        this.attempts = attempts;
        this.createdAt = Instant.now();
    }
}
