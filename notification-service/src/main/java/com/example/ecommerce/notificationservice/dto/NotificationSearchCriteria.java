package com.example.ecommerce.notificationservice.dto;

import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;

public record NotificationSearchCriteria(
    NotificationType type,
    NotificationStatus status,
    Long userId,
    Long orderId,
    Long paymentId
) {
}
