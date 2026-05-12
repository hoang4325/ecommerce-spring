package com.example.ecommerce.cartservice.dto;

import com.example.ecommerce.cartservice.entity.CartStatus;
import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long cartId,
        Long userId,
        CartStatus status,
        List<CartItemResponse> items,
        BigDecimal subtotal) {

    public CartResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
