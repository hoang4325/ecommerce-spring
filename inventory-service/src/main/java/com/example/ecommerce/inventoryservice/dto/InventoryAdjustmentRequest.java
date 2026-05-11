package com.example.ecommerce.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustmentRequest(
    @NotNull Integer delta
) {
}
