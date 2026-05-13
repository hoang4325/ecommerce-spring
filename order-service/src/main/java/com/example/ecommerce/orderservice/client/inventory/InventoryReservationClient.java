package com.example.ecommerce.orderservice.client.inventory;

import java.util.List;

public interface InventoryReservationClient {

    InventoryReservationResult reserve(Long orderId, List<InventoryReservationItem> items);

    InventoryReservationResult release(Long orderId);
}
