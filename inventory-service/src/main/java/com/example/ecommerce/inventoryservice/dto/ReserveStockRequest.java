package com.example.ecommerce.inventoryservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReserveStockRequest(
    @NotNull Long orderId,
    @NotEmpty @Valid List<ReservationItemRequest> items
) {
}
