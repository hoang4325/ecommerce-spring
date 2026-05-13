package com.example.ecommerce.orderservice.client.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartSnapshot(
    Long cartId,
    Long userId,
    String status,
    List<CartItemSnapshot> items,
    BigDecimal subtotal
) {

    public CartSnapshot {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
