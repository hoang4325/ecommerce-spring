package com.example.ecommerce.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SlugNormalizerTests {

    private final SlugNormalizer slugNormalizer = new SlugNormalizer();

    @Test
    void trimsAndLowercasesSlug() {
        assertThat(slugNormalizer.normalize("  Coffee-GEAR  ")).isEqualTo("coffee-gear");
    }

    @Test
    void rejectsBlankSlug() {
        assertThatThrownBy(() -> slugNormalizer.normalize("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Slug must not be blank");
    }
}
