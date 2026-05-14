package com.example.ecommerce.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_order_id", columnList = "order_id"),
        @Index(name = "idx_notifications_payment_id", columnList = "payment_id"),
        @Index(name = "idx_notifications_type", columnList = "type"),
        @Index(name = "idx_notifications_status", columnList = "status"),
        @Index(name = "idx_notifications_created_at", columnList = "created_at")
    }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "payment_id")
    private Long paymentId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationChannel channel;

    @NotNull
    @Column(nullable = false, length = 320)
    private String recipient;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationStatus status;

    @NotNull
    @Column(nullable = false, length = 200)
    private String subject;

    @NotNull
    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    Notification() {
    }

    private Notification(
        Long userId,
        Long orderId,
        Long paymentId,
        NotificationType type,
        NotificationChannel channel,
        String recipient,
        NotificationStatus status,
        String subject,
        String message,
        String failureReason
    ) {
        requireType(type);
        requireRecipient(recipient);
        requireSubject(subject);
        requireMessage(message);
        this.userId = userId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.type = type;
        this.channel = channel;
        this.recipient = recipient;
        this.status = status;
        this.subject = subject;
        this.message = message;
        this.failureReason = failureReason;
    }

    public static Notification sentEmail(
        Long userId,
        Long orderId,
        Long paymentId,
        NotificationType type,
        String recipient,
        String subject,
        String message
    ) {
        return new Notification(
            userId,
            orderId,
            paymentId,
            type,
            NotificationChannel.EMAIL,
            recipient,
            NotificationStatus.SENT,
            subject,
            message,
            null
        );
    }

    public static Notification failedEmail(
        Long userId,
        Long orderId,
        Long paymentId,
        NotificationType type,
        String recipient,
        String subject,
        String message,
        String failureReason
    ) {
        return new Notification(
            userId,
            orderId,
            paymentId,
            type,
            NotificationChannel.EMAIL,
            recipient,
            NotificationStatus.FAILED,
            subject,
            message,
            failureReason == null || failureReason.isBlank() ? "Notification failed" : failureReason
        );
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    private static void requireType(NotificationType type) {
        if (type == null) {
            throw new IllegalArgumentException("Notification type is required");
        }
    }

    private static void requireRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient is required");
        }
    }

    private static void requireSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
    }

    private static void requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
