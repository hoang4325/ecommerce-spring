package com.example.ecommerce.paymentservice.dto;

import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    Long paymentId,
    Long orderId,
    Long userId,
    BigDecimal amount,
    PaymentMethod method,
    PaymentStatus status,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {
}
