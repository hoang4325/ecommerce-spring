package com.example.ecommerce.productservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
    @NotNull Long categoryId,
    @NotBlank @Size(max = 180) String name,
    @NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 200) String slug,
    @Size(max = 4000) String description,
    @NotNull @DecimalMin(value = "0.01") BigDecimal price,
    @Size(max = 1000) String imageUrl
) {
}
