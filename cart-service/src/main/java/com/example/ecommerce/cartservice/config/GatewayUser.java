package com.example.ecommerce.cartservice.config;

import java.util.List;

public record GatewayUser(Long userId, String email, List<String> roles) {

    public GatewayUser {
        roles = roles == null
            ? List.of()
            : roles.stream()
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
    }
}
