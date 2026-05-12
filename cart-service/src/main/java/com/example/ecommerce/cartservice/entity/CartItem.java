package com.example.ecommerce.cartservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_cart_items_cart_product",
        columnNames = {"cart_id", "product_id"}
    )
)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @NotNull
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @NotNull
    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @NotNull
    @Column(name = "unit_price_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Positive
    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    CartItem() {
    }

    private CartItem(
        Cart cart,
        Long productId,
        String productNameSnapshot,
        BigDecimal unitPriceSnapshot,
        int quantity
    ) {
        requireCart(cart);
        requireReference(productId, "Product id must not be null");
        requireSnapshot(productNameSnapshot, "Product name snapshot must not be blank");
        requirePrice(unitPriceSnapshot);
        requirePositive(quantity);
        this.cart = cart;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
        this.quantity = quantity;
    }

    public static CartItem create(
        Cart cart,
        Long productId,
        String productNameSnapshot,
        BigDecimal unitPriceSnapshot,
        int quantity
    ) {
        return new CartItem(cart, productId, productNameSnapshot, unitPriceSnapshot, quantity);
    }

    public void updateSnapshot(String productNameSnapshot, BigDecimal unitPriceSnapshot) {
        requireSnapshot(productNameSnapshot, "Product name snapshot must not be blank");
        requirePrice(unitPriceSnapshot);
        this.productNameSnapshot = productNameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public void setQuantity(int quantity) {
        requirePositive(quantity);
        this.quantity = quantity;
    }

    public BigDecimal lineTotal() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
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

    private static void requireCart(Cart cart) {
        if (cart == null) {
            throw new IllegalArgumentException("Cart must not be null");
        }
    }

    private static void requireReference(Long value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireSnapshot(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requirePrice(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Unit price snapshot must not be null");
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

    public Cart getCart() {
        return cart;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
