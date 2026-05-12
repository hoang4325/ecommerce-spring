package com.example.ecommerce.cartservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTests {

    @Test
    void apiErrorResponseCarriesErrorMetadataAndFieldDetails() {
        var timestamp = Instant.parse("2026-05-12T00:00:00Z");
        var details = List.of(new ApiErrorResponse.FieldErrorDetail("quantity", "must be greater than 0"));

        var response = new ApiErrorResponse(
                timestamp,
                400,
                "Bad Request",
                "Validation failed",
                "/api/cart/items",
                details);

        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("Bad Request");
        assertThat(response.message()).isEqualTo("Validation failed");
        assertThat(response.path()).isEqualTo("/api/cart/items");
        assertThat(response.details()).containsExactly(new ApiErrorResponse.FieldErrorDetail(
                "quantity",
                "must be greater than 0"));
    }

    @Test
    void nullDetailsBecomeEmptyList() {
        var response = new ApiErrorResponse(
                Instant.parse("2026-05-12T00:00:00Z"),
                400,
                "Bad Request",
                "Validation failed",
                "/api/cart/items",
                null);

        assertThat(response.details()).isEmpty();
    }

    @Test
    void detailsAreDefensivelyCopied() {
        var details = new ArrayList<ApiErrorResponse.FieldErrorDetail>();
        details.add(new ApiErrorResponse.FieldErrorDetail("quantity", "must be greater than 0"));

        var response = new ApiErrorResponse(
                Instant.parse("2026-05-12T00:00:00Z"),
                400,
                "Bad Request",
                "Validation failed",
                "/api/cart/items",
                details);

        details.add(new ApiErrorResponse.FieldErrorDetail("productId", "must not be null"));

        assertThat(response.details()).containsExactly(new ApiErrorResponse.FieldErrorDetail(
                "quantity",
                "must be greater than 0"));
    }
}
