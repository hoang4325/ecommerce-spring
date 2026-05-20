package com.example.ecommerce.notificationservice.dto;

import com.example.ecommerce.notificationservice.entity.NotificationChannel;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import java.time.Instant;

public record NotificationResponse(
    Long notificationId,
    Long userId,
    Long orderId,
    Long paymentId,
    NotificationType type,
    NotificationChannel channel,
    String recipient,
    NotificationStatus status,
    String subject,
    String message,
    String failureReason,
    Instant createdAt
) {
}
