package com.example.ecommerce.productservice.service;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SlugNormalizer {

    public String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Slug must not be blank");
        }
        return normalized;
    }
}
