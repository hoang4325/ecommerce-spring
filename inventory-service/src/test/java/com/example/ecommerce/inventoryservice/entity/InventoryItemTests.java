package com.example.ecommerce.inventoryservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InventoryItemTests {

    @Test
    void reserveMovesAvailableQuantityToReservedQuantity() {
        InventoryItem item = InventoryItem.create(10L, 8);

        item.reserve(3);

        assertThat(item.getAvailableQuantity()).isEqualTo(5);
        assertThat(item.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    void reserveRejectsInsufficientAvailableQuantity() {
        InventoryItem item = InventoryItem.create(10L, 2);

        assertThatThrownBy(() -> item.reserve(3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Insufficient available quantity");
    }

    @Test
    void reserveRejectsReservedQuantityOverflow() {
        InventoryItem item = InventoryItem.create(10L, Integer.MAX_VALUE);
        item.reserve(Integer.MAX_VALUE);
        item.setAvailableQuantity(1);

        assertThatThrownBy(() -> item.reserve(1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Reserved quantity must not overflow");
        assertThat(item.getAvailableQuantity()).isEqualTo(1);
        assertThat(item.getReservedQuantity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void releaseMovesReservedQuantityBackToAvailableQuantity() {
        InventoryItem item = InventoryItem.create(10L, 8);
        item.reserve(5);

        item.release(2);

        assertThat(item.getAvailableQuantity()).isEqualTo(5);
        assertThat(item.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    void releaseRejectsAvailableQuantityOverflow() {
        InventoryItem item = InventoryItem.create(10L, Integer.MAX_VALUE);
        item.reserve(1);
        item.setAvailableQuantity(Integer.MAX_VALUE);

        assertThatThrownBy(() -> item.release(1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Available quantity must not overflow");
        assertThat(item.getAvailableQuantity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(item.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void deductReservedReducesReservedQuantityOnly() {
        InventoryItem item = InventoryItem.create(10L, 8);
        item.reserve(5);

        item.deductReserved(4);

        assertThat(item.getAvailableQuantity()).isEqualTo(3);
        assertThat(item.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void adjustAvailableQuantityRejectsNegativeResult() {
        InventoryItem item = InventoryItem.create(10L, 2);

        assertThatThrownBy(() -> item.adjustAvailableQuantity(-3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Available quantity must not be negative");
    }
}
