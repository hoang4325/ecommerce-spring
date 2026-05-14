package com.example.ecommerce.paymentservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CreatePaymentRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createPaymentRequestRejectsInvalidValues() {
        CreatePaymentRequest request = new CreatePaymentRequest(
            null,
            BigDecimal.ZERO,
            null,
            SimulatePaymentResult.SUCCESS
        );

        assertThat(validator.validate(request)).hasSize(3);
    }
}
