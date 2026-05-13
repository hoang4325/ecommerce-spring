package com.example.ecommerce.orderservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(
    name = "orders",
    indexes = @Index(
        name = "idx_orders_user_source_cart",
        columnList = "user_id, source_cart_id"
    )
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Column(name = "source_cart_id", nullable = false)
    private Long sourceCartId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId ASC")
    private List<OrderItem> items = new ArrayList<>();

    @NotNull
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    Order() {
    }

    private Order(Long userId, Long sourceCartId, List<OrderItem> items) {
        requireReference(userId, "User id must not be null");
        requireReference(sourceCartId, "Source cart id must not be null");
        requireItems(items);
        this.userId = userId;
        this.sourceCartId = sourceCartId;
        this.status = OrderStatus.PENDING;
        items.forEach(this::addItem);
        recalculateSubtotal();
    }

    public static Order createFromCart(Long userId, Long sourceCartId, List<OrderItem> items) {
        return new Order(userId, sourceCartId, items);
    }

    public void markStockReserved() {
        ensureMutable();
        this.status = OrderStatus.STOCK_RESERVED;
    }

    public void cancel(String reason) {
        ensureMutable();
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    public boolean isTerminal() {
        return status == OrderStatus.CANCELLED || status == OrderStatus.COMPLETED;
    }

    public void recalculateSubtotal() {
        this.subtotal = items.stream()
            .map(OrderItem::getLineTotal)
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

    private void addItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Order item must not be null");
        }
        if (items.stream().anyMatch(existing -> existing.getProductId().equals(item.getProductId()))) {
            throw new IllegalArgumentException("Order item product already exists");
        }
        item.attachTo(this);
        this.items.add(item);
    }

    private void ensureMutable() {
        if (isTerminal()) {
            throw new IllegalStateException("Terminal order cannot be changed");
        }
    }

    private static void requireReference(Long value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSourceCartId() {
        return sourceCartId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
