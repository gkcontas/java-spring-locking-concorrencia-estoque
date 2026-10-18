package com.gkcontas.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The row every strategy in this project fights over.
 *
 * <p>{@code @Version} is not a lock. It is a column Hibernate adds to the WHERE
 * clause of its own UPDATE and increments on success:
 * {@code update stock_item set quantity=?, version=2 where id=? and version=1}.
 * If another transaction already moved the row to version 2, that UPDATE
 * matches zero rows, and Hibernate turns the zero into
 * {@code StaleObjectStateException}. Nothing is held, nothing waits -- the
 * second writer simply finds out it lost, after the fact.
 */
@Entity
@Table(name = "stock_item")
@Getter
@Setter
@NoArgsConstructor
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String sku;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int quantity;

    /**
     * Hibernate owns this field. Setting it by hand is how a "retry" that
     * reuses a detached entity keeps writing the same stale version forever.
     */
    @Version
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private Long version;

    public StockItem(String sku, String name, int quantity) {
        this.sku = sku;
        this.name = name;
        this.quantity = quantity;
    }

    public boolean hasAtLeast(int requested) {
        return quantity >= requested;
    }

    public void withdraw(int requested) {
        if (!hasAtLeast(requested)) {
            throw new IllegalStateException("quantity %d is below the requested %d"
                    .formatted(quantity, requested));
        }
        quantity -= requested;
    }
}
