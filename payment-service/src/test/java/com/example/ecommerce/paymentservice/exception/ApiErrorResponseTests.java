package com.example.ecommerce.paymentservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTests {

    @Test
    void apiErrorResponseCopiesDetailsDefensively() {
        List<ApiErrorResponse.FieldErrorDetail> details = new ArrayList<>();
        details.add(new ApiErrorResponse.FieldErrorDetail("amount", "must be greater than 0"));

        ApiErrorResponse response = new ApiErrorResponse(
            Instant.parse("2026-05-13T00:00:00Z"),
            400,
            "Bad Request",
            "Validation failed",
            "/api/payments",
            details
        );
        details.clear();

        assertThat(response.details())
            .containsExactly(new ApiErrorResponse.FieldErrorDetail("amount", "must be greater than 0"));
        assertThatThrownBy(() -> response.details().add(new ApiErrorResponse.FieldErrorDetail("method", "required")))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
