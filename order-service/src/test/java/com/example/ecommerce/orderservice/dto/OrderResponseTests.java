package com.example.ecommerce.orderservice.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.orderservice.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderResponseTests {

    @Test
    void orderResponseCopiesItemsDefensively() {
        OrderItemResponse item = new OrderItemResponse(
            20L,
            "Keyboard",
            BigDecimal.valueOf(99.99),
            2,
            BigDecimal.valueOf(199.98)
        );
        List<OrderItemResponse> items = new ArrayList<>();
        items.add(item);

        OrderResponse response = new OrderResponse(
            1L,
            10L,
            30L,
            OrderStatus.PENDING,
            items,
            BigDecimal.valueOf(199.98),
            null,
            Instant.parse("2026-05-13T00:00:00Z"),
            Instant.parse("2026-05-13T00:00:00Z")
        );
        items.clear();

        assertThat(response.items()).containsExactly(item);
        assertThatThrownBy(() -> response.items().add(item)).isInstanceOf(UnsupportedOperationException.class);
    }
}
