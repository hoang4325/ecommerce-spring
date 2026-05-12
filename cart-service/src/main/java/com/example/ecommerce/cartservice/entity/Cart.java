package com.example.ecommerce.cartservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "active_cart_key", unique = true)
    private Long activeCartKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CartStatus status;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId ASC")
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    Cart() {
    }

    private Cart(Long userId) {
        requireReference(userId, "User id must not be null");
        this.userId = userId;
        this.activeCartKey = userId;
        this.status = CartStatus.ACTIVE;
    }

    public static Cart createActive(Long userId) {
        return new Cart(userId);
    }

    public void addOrIncrementItem(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        ensureActive();
        requirePositive(quantity);
        CartItem item = findItem(productId);
        if (item == null) {
            items.add(CartItem.create(this, productId, productName, unitPrice, quantity));
            return;
        }
        item.updateSnapshot(productName, unitPrice);
        item.setQuantity(Math.addExact(item.getQuantity(), quantity));
    }

    public void updateItem(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        ensureActive();
        requirePositive(quantity);
        CartItem item = findItem(productId);
        if (item == null) {
            throw new IllegalArgumentException("Cart item not found");
        }
        item.updateSnapshot(productName, unitPrice);
        item.setQuantity(quantity);
    }

    public boolean removeItem(Long productId) {
        ensureActive();
        return items.removeIf(item -> item.getProductId().equals(productId));
    }

    public void clearItems() {
        ensureActive();
        items.clear();
    }

    public BigDecimal subtotal() {
        return items.stream()
            .map(CartItem::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private CartItem findItem(Long productId) {
        requireReference(productId, "Product id must not be null");
        return items.stream()
            .filter(item -> item.getProductId().equals(productId))
            .findFirst()
            .orElse(null);
    }

    private void ensureActive() {
        if (this.status != CartStatus.ACTIVE) {
            throw new IllegalStateException("Cart is not active");
        }
    }

    private static void requireReference(Long value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getActiveCartKey() {
        return activeCartKey;
    }

    public CartStatus getStatus() {
        return status;
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
