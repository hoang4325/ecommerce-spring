package com.example.ecommerce.orderservice.client.inventory;

import java.util.List;

public record InventoryReservationRequest(Long orderId, List<InventoryReservationItem> items) {

    public InventoryReservationRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
