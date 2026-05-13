package com.example.ecommerce.orderservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTests {

    @Test
    void createFromCartSnapshotsComputesLineTotalsAndSubtotal() {
        Order order = Order.createFromCart(10L, 20L, List.of(
            OrderItem.create(100L, "Pour Over", new BigDecimal("19.99"), 2),
            OrderItem.create(101L, "Filters", new BigDecimal("4.50"), 3)
        ));

        assertThat(order.getUserId()).isEqualTo(10L);
        assertThat(order.getSourceCartId()).isEqualTo(20L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getSubtotal()).isEqualByComparingTo("53.48");
        assertThat(order.getItems()).extracting(OrderItem::getLineTotal)
            .containsExactly(new BigDecimal("39.98"), new BigDecimal("13.50"));
    }

    @Test
    void createFromCartRejectsEmptyItems() {
        assertThatThrownBy(() -> Order.createFromCart(10L, 20L, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Order must contain at least one item");
    }

    @Test
    void orderItemRejectsInvalidQuantityAndPrice() {
        assertThatThrownBy(() -> OrderItem.create(100L, "Pour Over", BigDecimal.ONE, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity must be positive");

        assertThatThrownBy(() -> OrderItem.create(100L, "Pour Over", new BigDecimal("-0.01"), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unit price must be zero or positive");
    }

    @Test
    void cancelRejectsTerminalOrders() {
        Order order = Order.createFromCart(10L, 20L, List.of(
            OrderItem.create(100L, "Pour Over", new BigDecimal("19.99"), 1)
        ));
        order.cancel("Stock reservation failed");

        assertThatThrownBy(() -> order.cancel("Second cancellation"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Terminal order cannot be changed");
    }
}
