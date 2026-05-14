package com.example.ecommerce.paymentservice.dto;

import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreatePaymentRequest(
    @NotNull Long orderId,
    @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
    @NotNull PaymentMethod method,
    SimulatePaymentResult simulateResult
) {
}
