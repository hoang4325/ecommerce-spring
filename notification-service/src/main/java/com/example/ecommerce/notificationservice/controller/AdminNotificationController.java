package com.example.ecommerce.notificationservice.controller;

import com.example.ecommerce.notificationservice.dto.NotificationResponse;
import com.example.ecommerce.notificationservice.dto.NotificationSearchCriteria;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import com.example.ecommerce.notificationservice.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Page<NotificationResponse> list(
        @RequestParam(required = false) NotificationType type,
        @RequestParam(required = false) NotificationStatus status,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) Long paymentId,
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return notificationService.findAdminNotifications(
            new NotificationSearchCriteria(type, status, userId, orderId, paymentId),
            pageable
        );
    }

    @GetMapping("/{id}")
    public NotificationResponse get(@PathVariable Long id) {
        return notificationService.findAdminNotification(id);
    }
}
