package com.example.ecommerce.productservice.exception;

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
        details.add(new ApiErrorResponse.FieldErrorDetail("slug", "must match pattern"));
        ApiErrorResponse response = new ApiErrorResponse(
            Instant.EPOCH,
            400,
            "Bad Request",
            "Validation failed",
            "/categories",
            details
        );

        details.add(new ApiErrorResponse.FieldErrorDetail("name", "must not be blank"));

        assertThat(response.details()).containsExactly(
            new ApiErrorResponse.FieldErrorDetail("slug", "must match pattern")
        );
        assertThatThrownBy(() -> response.details().add(
            new ApiErrorResponse.FieldErrorDetail("price", "must be positive")
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullDetailsBecomeEmptyList() {
        ApiErrorResponse response = new ApiErrorResponse(
            Instant.EPOCH,
            400,
            "Bad Request",
            "Validation failed",
            "/categories",
            null
        );

        assertThat(response.details()).isEmpty();
    }
}
