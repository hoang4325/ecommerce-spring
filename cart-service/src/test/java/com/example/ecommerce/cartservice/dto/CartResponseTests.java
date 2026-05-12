package com.example.ecommerce.cartservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.cartservice.entity.CartStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CartResponseTests {

    @Test
    void nullItemsBecomeEmptyList() {
        var response = new CartResponse(
                1L,
                10L,
                CartStatus.ACTIVE,
                null,
                BigDecimal.ZERO);

        assertThat(response.items()).isEmpty();
    }

    @Test
    void itemsAreDefensivelyCopied() {
        var items = new ArrayList<CartItemResponse>();
        items.add(new CartItemResponse(20L, "Keyboard", BigDecimal.TEN, 1, BigDecimal.TEN));

        var response = new CartResponse(
                1L,
                10L,
                CartStatus.ACTIVE,
                items,
                BigDecimal.TEN);

        items.add(new CartItemResponse(30L, "Mouse", BigDecimal.ONE, 2, BigDecimal.valueOf(2)));

        assertThat(response.items()).containsExactly(new CartItemResponse(
                20L,
                "Keyboard",
                BigDecimal.TEN,
                1,
                BigDecimal.TEN));
    }
}
