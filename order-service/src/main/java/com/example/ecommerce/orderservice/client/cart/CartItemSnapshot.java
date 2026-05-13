package com.example.ecommerce.orderservice.client.cart;

import java.math.BigDecimal;

public record CartItemSnapshot(
    Long productId,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal lineTotal
) {
}
