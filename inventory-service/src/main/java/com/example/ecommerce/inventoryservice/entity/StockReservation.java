package com.example.ecommerce.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @NotNull
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Positive
    @Column(nullable = false)
    private int quantity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    StockReservation() {
    }

    private StockReservation(
        Long orderId,
        Long productId,
        int quantity,
        ReservationStatus status,
        String failureReason
    ) {
        requireReference(orderId, "Order id must not be null");
        requireReference(productId, "Product id must not be null");
        requirePositive(quantity);
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.failureReason = failureReason;
    }

    public static StockReservation reserved(Long orderId, Long productId, int quantity) {
        return new StockReservation(orderId, productId, quantity, ReservationStatus.RESERVED, null);
    }

    public static StockReservation failed(Long orderId, Long productId, int quantity, String failureReason) {
        return new StockReservation(orderId, productId, quantity, ReservationStatus.FAILED, failureReason);
    }

    public void release() {
        if (this.status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Only reserved stock can be released");
        }
        this.status = ReservationStatus.RELEASED;
    }

    public void deduct() {
        if (this.status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Only reserved stock can be deducted");
        }
        this.status = ReservationStatus.DEDUCTED;
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

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
