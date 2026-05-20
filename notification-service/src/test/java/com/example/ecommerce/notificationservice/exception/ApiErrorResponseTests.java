package com.example.ecommerce.notificationservice.exception;

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
        details.add(new ApiErrorResponse.FieldErrorDetail("recipient", "must be a well-formed email address"));

        ApiErrorResponse response = new ApiErrorResponse(
            Instant.parse("2026-05-14T00:00:00Z"),
            400,
            "Bad Request",
            "Validation failed",
            "/api/internal/notifications",
            details
        );
        details.clear();

        assertThat(response.details())
            .containsExactly(new ApiErrorResponse.FieldErrorDetail(
                "recipient",
                "must be a well-formed email address"
            ));
        assertThatThrownBy(() -> response.details().add(new ApiErrorResponse.FieldErrorDetail("subject", "required")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void apiErrorResponseDefaultsNullDetailsToEmptyList() {
        ApiErrorResponse response = new ApiErrorResponse(
            Instant.parse("2026-05-14T00:00:00Z"),
            401,
            "Unauthorized",
            "Missing user identity",
            "/api/admin/notifications",
            null
        );

        assertThat(response.details()).isEmpty();
    }
}
