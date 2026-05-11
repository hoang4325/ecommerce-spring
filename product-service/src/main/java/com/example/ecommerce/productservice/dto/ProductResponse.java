package com.example.ecommerce.productservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
    Long id,
    Long categoryId,
    String categoryName,
    String categorySlug,
    String name,
    String slug,
    String description,
    BigDecimal price,
    String imageUrl,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
