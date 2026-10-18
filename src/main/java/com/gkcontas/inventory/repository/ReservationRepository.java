package com.gkcontas.inventory.repository;

import com.gkcontas.inventory.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("select coalesce(sum(r.quantity), 0) from Reservation r where r.stockItemId = :itemId")
    long reservedUnits(@Param("itemId") Long itemId);

    long countByStockItemId(Long itemId);

    void deleteByStockItemId(Long itemId);
}
