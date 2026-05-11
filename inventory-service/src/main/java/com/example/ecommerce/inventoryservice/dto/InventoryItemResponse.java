package com.example.ecommerce.inventoryservice.dto;

import java.time.Instant;

public record InventoryItemResponse(
    Long id,
    Long productId,
    int availableQuantity,
    int reservedQuantity,
    Instant createdAt,
    Instant updatedAt
) {
}
