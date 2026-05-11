package com.example.ecommerce.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockLevelRequest(
    @NotNull @PositiveOrZero Integer availableQuantity
) {
}
