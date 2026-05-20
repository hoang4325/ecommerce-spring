package com.example.ecommerce.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ecommerce.notificationservice.dto.CreateNotificationRequest;
import com.example.ecommerce.notificationservice.dto.NotificationResponse;
import com.example.ecommerce.notificationservice.dto.NotificationSearchCriteria;
import com.example.ecommerce.notificationservice.entity.Notification;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import com.example.ecommerce.notificationservice.exception.NotificationNotFoundException;
import com.example.ecommerce.notificationservice.repository.NotificationRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    private static final Long NOTIFICATION_ID = 9000L;
    private static final Long USER_ID = 10L;
    private static final Long ORDER_ID = 1000L;
    private static final Long PAYMENT_ID = 5000L;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createNotificationStoresSentNotification() {
        CreateNotificationRequest request = request(false);
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> withId(invocation.getArgument(0), NOTIFICATION_ID));

        NotificationResponse response = notificationService.create(request);

        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.failureReason()).isNull();
        assertThat(response.notificationId()).isEqualTo(NOTIFICATION_ID);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.PAYMENT_SUCCEEDED);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getPaymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    void createNotificationStoresFailedNotification() {
        CreateNotificationRequest request = request(true);
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> withId(invocation.getArgument(0), NOTIFICATION_ID));

        NotificationResponse response = notificationService.create(request);

        assertThat(response.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Simulated notification failure");
    }

    @Test
    void findAdminNotificationsDelegatesCriteriaAndPageable() {
        NotificationSearchCriteria criteria = new NotificationSearchCriteria(
            NotificationType.PAYMENT_SUCCEEDED,
            NotificationStatus.SENT,
            USER_ID,
            ORDER_ID,
            PAYMENT_ID
        );
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationRepository.findAll(anyNotificationSpecification(), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(withId(sentNotification(), NOTIFICATION_ID))));

        NotificationResponse response = notificationService.findAdminNotifications(criteria, pageable)
            .getContent()
            .getFirst();

        assertThat(response.notificationId()).isEqualTo(NOTIFICATION_ID);
        verify(notificationRepository).findAll(anyNotificationSpecification(), eq(pageable));
    }

    @Test
    void findAdminNotificationReturnsNotification() {
        when(notificationRepository.findById(NOTIFICATION_ID))
            .thenReturn(Optional.of(withId(sentNotification(), NOTIFICATION_ID)));

        NotificationResponse response = notificationService.findAdminNotification(NOTIFICATION_ID);

        assertThat(response.notificationId()).isEqualTo(NOTIFICATION_ID);
        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void findAdminNotificationThrowsWhenMissing() {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.findAdminNotification(NOTIFICATION_ID))
            .isInstanceOf(NotificationNotFoundException.class)
            .hasMessage("Notification not found: 9000");
    }

    @Test
    void mapsCreatedAtAsUtcInstant() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            Notification notification = withId(sentNotification(), NOTIFICATION_ID);
            ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.parse("2026-05-14T12:00:00"));
            when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));

            NotificationResponse response = notificationService.findAdminNotification(NOTIFICATION_ID);

            assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-14T12:00:00Z"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static CreateNotificationRequest request(boolean simulateFailure) {
        return new CreateNotificationRequest(
            NotificationType.PAYMENT_SUCCEEDED,
            "customer@example.com",
            "Payment received",
            "Your payment was successful.",
            USER_ID,
            ORDER_ID,
            PAYMENT_ID,
            simulateFailure
        );
    }

    private static Notification sentNotification() {
        return Notification.sentEmail(
            USER_ID,
            ORDER_ID,
            PAYMENT_ID,
            NotificationType.PAYMENT_SUCCEEDED,
            "customer@example.com",
            "Payment received",
            "Your payment was successful."
        );
    }

    private static Notification withId(Notification notification, Long id) {
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.parse("2026-05-14T12:00:00"));
        return notification;
    }

    private static Specification<Notification> anyNotificationSpecification() {
        return any();
    }
}
