package com.example.ecommerce.notificationservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.notificationservice.entity.NotificationChannel;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationResponseTests {

    @Test
    void notificationResponseCarriesFields() {
        Instant createdAt = Instant.parse("2026-05-14T00:00:00Z");

        NotificationResponse response = new NotificationResponse(
            9000L,
            10L,
            1000L,
            5000L,
            NotificationType.PAYMENT_SUCCEEDED,
            NotificationChannel.EMAIL,
            "customer@example.com",
            NotificationStatus.SENT,
            "Payment received",
            "Your payment was successful.",
            null,
            createdAt
        );

        assertThat(response.notificationId()).isEqualTo(9000L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.orderId()).isEqualTo(1000L);
        assertThat(response.paymentId()).isEqualTo(5000L);
        assertThat(response.type()).isEqualTo(NotificationType.PAYMENT_SUCCEEDED);
        assertThat(response.channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(response.recipient()).isEqualTo("customer@example.com");
        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.subject()).isEqualTo("Payment received");
        assertThat(response.message()).isEqualTo("Your payment was successful.");
        assertThat(response.failureReason()).isNull();
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
