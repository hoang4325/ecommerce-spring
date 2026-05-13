package com.example.ecommerce.orderservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.orderservice.entity.OrderStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class UpdateOrderStatusRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void updateStatusRequestRejectsTooLongReason() {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "x".repeat(501));

        assertThat(validator.validate(request))
            .anySatisfy(violation -> assertThat(violation.getPropertyPath()).hasToString("reason"));
    }
}
