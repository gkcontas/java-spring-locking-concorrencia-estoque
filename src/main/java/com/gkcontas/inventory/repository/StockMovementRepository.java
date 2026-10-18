package com.gkcontas.inventory.repository;

import com.gkcontas.inventory.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** Units the application told callers they had taken. */
    @Query("select coalesce(sum(m.quantity), 0) from StockMovement m where m.stockItemId = :itemId")
    long promisedUnits(@Param("itemId") Long itemId);

    @Query("select coalesce(sum(m.attempts), 0) from StockMovement m where m.stockItemId = :itemId")
    long totalAttempts(@Param("itemId") Long itemId);

    long countByStockItemId(Long itemId);

    void deleteByStockItemId(Long itemId);
}
