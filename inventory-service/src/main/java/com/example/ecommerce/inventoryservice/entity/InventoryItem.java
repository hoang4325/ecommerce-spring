package com.example.ecommerce.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @PositiveOrZero
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @PositiveOrZero
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    InventoryItem() {
    }

    private InventoryItem(Long productId, int availableQuantity) {
        requireProductId(productId);
        requireNonNegative(availableQuantity, "Available quantity must not be negative");
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;
    }

    public static InventoryItem create(Long productId, int availableQuantity) {
        return new InventoryItem(productId, availableQuantity);
    }

    public void setAvailableQuantity(int availableQuantity) {
        requireNonNegative(availableQuantity, "Available quantity must not be negative");
        this.availableQuantity = availableQuantity;
    }

    public void adjustAvailableQuantity(int delta) {
        int nextQuantity = addExact(this.availableQuantity, delta, "Available quantity must not overflow");
        requireNonNegative(nextQuantity, "Available quantity must not be negative");
        this.availableQuantity = nextQuantity;
    }

    public void reserve(int quantity) {
        requirePositive(quantity);
        if (this.availableQuantity < quantity) {
            throw new IllegalArgumentException("Insufficient available quantity");
        }
        int nextAvailableQuantity = this.availableQuantity - quantity;
        int nextReservedQuantity = addExact(
            this.reservedQuantity,
            quantity,
            "Reserved quantity must not overflow"
        );
        this.availableQuantity = nextAvailableQuantity;
        this.reservedQuantity = nextReservedQuantity;
    }

    public void release(int quantity) {
        requirePositive(quantity);
        ensureReservedQuantity(quantity);
        int nextReservedQuantity = this.reservedQuantity - quantity;
        int nextAvailableQuantity = addExact(
            this.availableQuantity,
            quantity,
            "Available quantity must not overflow"
        );
        this.reservedQuantity = nextReservedQuantity;
        this.availableQuantity = nextAvailableQuantity;
    }

    public void deductReserved(int quantity) {
        requirePositive(quantity);
        ensureReservedQuantity(quantity);
        this.reservedQuantity -= quantity;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private static void requireProductId(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Product id must not be null");
        }
    }

    private static void requireNonNegative(int quantity, String message) {
        if (quantity < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private static int addExact(int left, int right, String message) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }

    private void ensureReservedQuantity(int quantity) {
        if (this.reservedQuantity < quantity) {
            throw new IllegalArgumentException("Insufficient reserved quantity");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
