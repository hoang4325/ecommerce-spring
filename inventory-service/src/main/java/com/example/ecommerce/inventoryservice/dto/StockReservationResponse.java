package com.example.ecommerce.inventoryservice.dto;

import com.example.ecommerce.inventoryservice.entity.ReservationStatus;
import java.time.Instant;

public record StockReservationResponse(
    Long id,
    Long orderId,
    Long productId,
    int quantity,
    ReservationStatus status,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {
}
