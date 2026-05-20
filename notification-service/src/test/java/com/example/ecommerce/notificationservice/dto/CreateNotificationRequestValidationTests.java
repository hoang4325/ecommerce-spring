package com.example.ecommerce.notificationservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.notificationservice.entity.NotificationType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateNotificationRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validRequestHasNoViolationsAndDefaultsSimulationFlag() {
        CreateNotificationRequest request = validRequest(false);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.shouldSimulateFailure()).isFalse();
        assertThat(validRequest(true).shouldSimulateFailure()).isTrue();
        assertThat(validRequest(null).shouldSimulateFailure()).isFalse();
    }

    @Test
    void rejectsInvalidValues() {
        CreateNotificationRequest request = new CreateNotificationRequest(
            null,
            "not-an-email",
            " ",
            "",
            10L,
            1000L,
            5000L,
            false
        );

        assertThat(fieldsOf(validator.validate(request)))
            .contains("type", "recipient", "subject", "message");
    }

    @Test
    void rejectsTooLongFields() {
        CreateNotificationRequest request = new CreateNotificationRequest(
            NotificationType.PAYMENT_SUCCEEDED,
            "a".repeat(310) + "@example.com",
            "s".repeat(201),
            "m".repeat(2001),
            10L,
            1000L,
            5000L,
            false
        );

        assertThat(fieldsOf(validator.validate(request)))
            .contains("recipient", "subject", "message");
    }

    private static CreateNotificationRequest validRequest(Boolean simulateFailure) {
        return new CreateNotificationRequest(
            NotificationType.PAYMENT_SUCCEEDED,
            "customer@example.com",
            "Payment received",
            "Your payment was successful.",
            10L,
            1000L,
            5000L,
            simulateFailure
        );
    }

    private static Set<String> fieldsOf(Set<ConstraintViolation<CreateNotificationRequest>> violations) {
        return violations.stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }
}
