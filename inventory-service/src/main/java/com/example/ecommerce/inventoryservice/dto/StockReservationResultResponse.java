package com.example.ecommerce.inventoryservice.dto;

import com.example.ecommerce.inventoryservice.entity.ReservationStatus;
import java.util.List;

public record StockReservationResultResponse(
    Long orderId,
    ReservationStatus status,
    List<StockReservationResponse> reservations
) {

    public StockReservationResultResponse {
        reservations = reservations == null ? List.of() : List.copyOf(reservations);
    }
}
