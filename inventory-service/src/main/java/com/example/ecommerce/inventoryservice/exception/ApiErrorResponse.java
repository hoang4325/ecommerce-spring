package com.example.ecommerce.inventoryservice.exception;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldErrorDetail> details
) {

    public ApiErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
