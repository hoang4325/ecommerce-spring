package com.example.ecommerce.notificationservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class NotificationTests {

    @Test
    void createSentEmailNotification() {
        Notification notification = Notification.sentEmail(
            10L,
            1000L,
            2000L,
            NotificationType.ORDER_COMPLETED,
            "customer@example.com",
            "Order completed",
            "Your order is complete"
        );

        assertThat(notification.getUserId()).isEqualTo(10L);
        assertThat(notification.getOrderId()).isEqualTo(1000L);
        assertThat(notification.getPaymentId()).isEqualTo(2000L);
        assertThat(notification.getType()).isEqualTo(NotificationType.ORDER_COMPLETED);
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.getRecipient()).isEqualTo("customer@example.com");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSubject()).isEqualTo("Order completed");
        assertThat(notification.getMessage()).isEqualTo("Your order is complete");
        assertThat(notification.getFailureReason()).isNull();
    }

    @Test
    void createFailedEmailNotification() {
        Notification notification = Notification.failedEmail(
            10L,
            1000L,
            2000L,
            NotificationType.PAYMENT_FAILED,
            "customer@example.com",
            "Payment failed",
            "Payment could not be completed",
            "SMTP rejected recipient"
        );

        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).isEqualTo("SMTP rejected recipient");

        Notification defaultReason = Notification.failedEmail(
            10L,
            1000L,
            2000L,
            NotificationType.PAYMENT_FAILED,
            "customer@example.com",
            "Payment failed",
            "Payment could not be completed",
            " "
        );

        assertThat(defaultReason.getFailureReason()).isEqualTo("Notification failed");
    }

    @Test
    void prePersistStoresCreatedAtInUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            Notification notification = Notification.sentEmail(
                10L,
                1000L,
                2000L,
                NotificationType.ORDER_COMPLETED,
                "customer@example.com",
                "Order completed",
                "Your order is complete"
            );
            LocalDateTime beforeUtc = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);

            notification.prePersist();

            LocalDateTime afterUtc = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1);
            assertThat(notification.getCreatedAt()).isBetween(beforeUtc, afterUtc);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void createRejectsMissingType() {
        assertThatThrownBy(() -> Notification.sentEmail(
            10L,
            1000L,
            2000L,
            null,
            "customer@example.com",
            "Order completed",
            "Your order is complete"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Notification type is required");
    }

    @Test
    void createRejectsMissingRecipient() {
        assertThatThrownBy(() -> Notification.sentEmail(
            10L,
            1000L,
            2000L,
            NotificationType.ORDER_COMPLETED,
            " ",
            "Order completed",
            "Your order is complete"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Recipient is required");
    }

    @Test
    void createRejectsMissingSubject() {
        assertThatThrownBy(() -> Notification.sentEmail(
            10L,
            1000L,
            2000L,
            NotificationType.ORDER_COMPLETED,
            "customer@example.com",
            "",
            "Your order is complete"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Subject is required");
    }

    @Test
    void createRejectsMissingMessage() {
        assertThatThrownBy(() -> Notification.sentEmail(
            10L,
            1000L,
            2000L,
            NotificationType.ORDER_COMPLETED,
            "customer@example.com",
            "Order completed",
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Message is required");
    }
}
