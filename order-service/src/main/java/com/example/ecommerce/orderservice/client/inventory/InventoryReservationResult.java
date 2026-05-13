package com.example.ecommerce.orderservice.client.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReservationResult(
    Long orderId,
    InventoryReservationStatus status
) {
}
