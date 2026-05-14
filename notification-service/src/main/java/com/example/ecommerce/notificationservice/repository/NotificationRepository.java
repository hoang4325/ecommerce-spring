package com.example.ecommerce.notificationservice.repository;

import com.example.ecommerce.notificationservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationRepository
    extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
}
