package com.example.ecommerce.orderservice.config;

import java.util.List;

public record GatewayUser(Long id, String email, List<String> roles) {

    public GatewayUser {
        roles = roles == null
            ? List.of()
            : roles.stream()
                .map(role -> role == null ? "" : role.trim())
                .filter(role -> !role.isBlank())
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .distinct()
                .toList();
    }
}
