package com.example.ecommerce.cartservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CartTests {

    @Test
    void createActiveCartUsesUserIdAsActiveKey() {
        Cart cart = Cart.createActive(10L);

        assertThat(cart.getUserId()).isEqualTo(10L);
        assertThat(cart.getActiveCartKey()).isEqualTo(10L);
        assertThat(cart.getStatus()).isEqualTo(CartStatus.ACTIVE);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void addItemStoresSnapshotAndCalculatesSubtotal() {
        Cart cart = Cart.createActive(10L);

        cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 2);

        assertThat(cart.getItems()).hasSize(1);
        CartItem item = cart.getItems().getFirst();
        assertThat(item.getProductId()).isEqualTo(20L);
        assertThat(item.getProductNameSnapshot()).isEqualTo("Pour Over");
        assertThat(item.getUnitPriceSnapshot()).isEqualByComparingTo("19.99");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(cart.subtotal()).isEqualByComparingTo("39.98");
    }

    @Test
    void addExistingProductIncrementsQuantityAndRefreshesSnapshot() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Old Name", new BigDecimal("10.00"), 1);

        cart.addOrIncrementItem(20L, "New Name", new BigDecimal("12.50"), 3);

        CartItem item = cart.getItems().getFirst();
        assertThat(item.getQuantity()).isEqualTo(4);
        assertThat(item.getProductNameSnapshot()).isEqualTo("New Name");
        assertThat(item.getUnitPriceSnapshot()).isEqualByComparingTo("12.50");
    }

    @Test
    void updateItemQuantityReplacesQuantityAndSnapshot() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Old Name", new BigDecimal("10.00"), 1);

        cart.updateItem(20L, "New Name", new BigDecimal("12.50"), 5);

        CartItem item = cart.getItems().getFirst();
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getProductNameSnapshot()).isEqualTo("New Name");
        assertThat(item.getUnitPriceSnapshot()).isEqualByComparingTo("12.50");
    }

    @Test
    void invalidQuantityIsRejected() {
        Cart cart = Cart.createActive(10L);

        assertThatThrownBy(() -> cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity must be positive");
    }
}
