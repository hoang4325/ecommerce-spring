package com.example.ecommerce.orderservice.dto;

import com.example.ecommerce.orderservice.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(
    @NotNull OrderStatus status,
    @Size(max = 500) String reason
) {
}
