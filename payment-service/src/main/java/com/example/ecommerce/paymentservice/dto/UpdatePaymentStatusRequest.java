package com.example.ecommerce.paymentservice.dto;

import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePaymentStatusRequest(
    @NotNull PaymentStatus status,
    @Size(max = 500) String failureReason
) {

    @AssertTrue(message = "Status must be SUCCESS or FAILED")
    public boolean isTerminalStatus() {
        return status == null || status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED;
    }
}
