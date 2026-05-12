package com.example.ecommerce.cartservice.exception;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> details) {

    public record FieldErrorDetail(String field, String message) {
    }
}
