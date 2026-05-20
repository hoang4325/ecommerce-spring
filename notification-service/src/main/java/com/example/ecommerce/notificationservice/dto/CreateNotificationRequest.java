package com.example.ecommerce.notificationservice.dto;

import com.example.ecommerce.notificationservice.entity.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
    @NotNull NotificationType type,
    @NotBlank @Email @Size(max = 320) String recipient,
    @NotBlank @Size(max = 200) String subject,
    @NotBlank @Size(max = 2000) String message,
    Long userId,
    Long orderId,
    Long paymentId,
    Boolean simulateFailure
) {

    public boolean shouldSimulateFailure() {
        return Boolean.TRUE.equals(simulateFailure);
    }
}
