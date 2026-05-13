package com.example.ecommerce.orderservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTests {

    @Test
    void apiErrorResponseCopiesDetailsDefensively() {
        ApiErrorResponse.FieldErrorDetail detail = new ApiErrorResponse.FieldErrorDetail("status", "must not be null");
        List<ApiErrorResponse.FieldErrorDetail> details = new ArrayList<>();
        details.add(detail);

        ApiErrorResponse response = new ApiErrorResponse(
            Instant.parse("2026-05-13T00:00:00Z"),
            400,
            "Bad Request",
            "Validation failed",
            "/api/orders",
            details
        );
        details.clear();

        assertThat(response.details()).containsExactly(detail);
        assertThatThrownBy(() -> response.details().add(detail)).isInstanceOf(UnsupportedOperationException.class);
    }
}
