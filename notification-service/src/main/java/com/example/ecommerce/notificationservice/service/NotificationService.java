package com.example.ecommerce.notificationservice.service;

import com.example.ecommerce.notificationservice.dto.CreateNotificationRequest;
import com.example.ecommerce.notificationservice.dto.NotificationResponse;
import com.example.ecommerce.notificationservice.dto.NotificationSearchCriteria;
import com.example.ecommerce.notificationservice.entity.Notification;
import com.example.ecommerce.notificationservice.exception.NotificationNotFoundException;
import com.example.ecommerce.notificationservice.repository.NotificationRepository;
import com.example.ecommerce.notificationservice.repository.NotificationSpecifications;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final String SIMULATED_FAILURE_REASON = "Simulated notification failure";

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        Notification notification = request.shouldSimulateFailure()
            ? Notification.failedEmail(
                request.userId(),
                request.orderId(),
                request.paymentId(),
                request.type(),
                request.recipient(),
                request.subject(),
                request.message(),
                SIMULATED_FAILURE_REASON
            )
            : Notification.sentEmail(
                request.userId(),
                request.orderId(),
                request.paymentId(),
                request.type(),
                request.recipient(),
                request.subject(),
                request.message()
            );

        return toResponse(notificationRepository.save(notification));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> findAdminNotifications(NotificationSearchCriteria criteria, Pageable pageable) {
        return notificationRepository.findAll(NotificationSpecifications.byCriteria(criteria), pageable)
            .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NotificationResponse findAdminNotification(Long id) {
        return notificationRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getUserId(),
            notification.getOrderId(),
            notification.getPaymentId(),
            notification.getType(),
            notification.getChannel(),
            notification.getRecipient(),
            notification.getStatus(),
            notification.getSubject(),
            notification.getMessage(),
            notification.getFailureReason(),
            toInstant(notification.getCreatedAt())
        );
    }

    private static Instant toInstant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }
}
