package com.example.ecommerce.inventoryservice.repository;

import com.example.ecommerce.inventoryservice.entity.ReservationStatus;
import com.example.ecommerce.inventoryservice.entity.StockReservation;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findAllByOrderIdOrderByProductIdAsc(Long orderId);

    List<StockReservation> findAllByOrderIdAndStatusOrderByProductIdAsc(
        Long orderId,
        ReservationStatus status
    );

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<ReservationStatus> statuses);
}
