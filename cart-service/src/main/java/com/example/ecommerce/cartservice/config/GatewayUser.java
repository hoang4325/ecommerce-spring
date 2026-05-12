package com.example.ecommerce.cartservice.config;

import java.util.List;

public record GatewayUser(Long userId, String email, List<String> roles) {

    public GatewayUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
