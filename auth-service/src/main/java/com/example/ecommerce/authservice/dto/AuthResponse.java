package com.example.ecommerce.authservice.dto;

import com.example.ecommerce.authservice.entity.Role;
import java.util.Set;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    Long userId,
    String email,
    Set<Role> roles
) {
}
