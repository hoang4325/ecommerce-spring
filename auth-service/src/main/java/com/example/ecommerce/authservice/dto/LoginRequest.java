package com.example.ecommerce.authservice.dto;

import com.example.ecommerce.authservice.validation.BcryptPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank @BcryptPassword String password
) {
}
