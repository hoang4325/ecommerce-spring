package com.example.ecommerce.productservice.dto;

import java.time.Instant;

public record CategoryResponse(
    Long id,
    String name,
    String slug,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
