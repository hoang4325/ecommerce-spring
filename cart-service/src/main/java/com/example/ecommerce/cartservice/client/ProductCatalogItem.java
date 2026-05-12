package com.example.ecommerce.cartservice.client;

import java.math.BigDecimal;

public record ProductCatalogItem(Long id, String name, BigDecimal price) {
}
