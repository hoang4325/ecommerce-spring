package com.example.ecommerce.orderservice.dto;

import com.example.ecommerce.orderservice.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long orderId,
    Long userId,
    Long sourceCartId,
    OrderStatus status,
    List<OrderItemResponse> items,
    BigDecimal subtotal,
    String cancellationReason,
    Instant createdAt,
    Instant updatedAt
) {

    public OrderResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
