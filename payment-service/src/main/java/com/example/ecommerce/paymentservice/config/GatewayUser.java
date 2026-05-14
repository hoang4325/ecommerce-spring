package com.example.ecommerce.paymentservice.config;

import java.util.List;
import java.util.Objects;

public record GatewayUser(Long id, String email, List<String> roles) {

    public GatewayUser {
        Objects.requireNonNull(id, "id must not be null");
        roles = roles == null
            ? List.of()
            : roles.stream()
                .map(role -> role == null ? "" : role.trim())
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }
}
