package com.example.ecommerce.authservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTests {

    @Test
    void copiesDetailsListDefensively() {
        List<ApiErrorResponse.FieldErrorDetail> details = new ArrayList<>();
        details.add(new ApiErrorResponse.FieldErrorDetail("email", "must be valid"));
        ApiErrorResponse response = new ApiErrorResponse(
            Instant.EPOCH,
            400,
            "Bad Request",
            "Validation failed",
            "/auth/register",
            details
        );

        details.add(new ApiErrorResponse.FieldErrorDetail("password", "must be valid"));

        assertThat(response.details()).containsExactly(new ApiErrorResponse.FieldErrorDetail("email", "must be valid"));
        assertThatThrownBy(() -> response.details().add(new ApiErrorResponse.FieldErrorDetail("name", "must be valid")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullDetailsBecomeEmptyList() {
        ApiErrorResponse response = new ApiErrorResponse(
            Instant.EPOCH,
            400,
            "Bad Request",
            "Validation failed",
            "/auth/register",
            null
        );

        assertThat(response.details()).isEmpty();
    }
}
