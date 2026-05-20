package com.example.ecommerce.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String internalToken) {
}
