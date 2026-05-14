package com.example.ecommerce.paymentservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class UpdatePaymentStatusRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void updatePaymentStatusRequestRejectsPendingAndLongFailureReason() {
        UpdatePaymentStatusRequest request = new UpdatePaymentStatusRequest(
            PaymentStatus.PENDING,
            "x".repeat(501)
        );

        assertThat(validator.validate(request)).hasSize(2);
    }

    @Test
    void updatePaymentStatusRequestAcceptsTerminalStatuses() {
        assertThat(validator.validate(new UpdatePaymentStatusRequest(PaymentStatus.SUCCESS, null))).isEmpty();
        assertThat(validator.validate(new UpdatePaymentStatusRequest(PaymentStatus.FAILED, "Declined"))).isEmpty();
    }
}
