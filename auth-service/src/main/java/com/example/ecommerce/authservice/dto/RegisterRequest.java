package com.example.ecommerce.authservice.dto;

import com.example.ecommerce.authservice.validation.BcryptPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @Email @NotBlank String email,
    @NotBlank @Size(min = 8) @BcryptPassword String password,
    @NotBlank @Size(max = 120) String fullName
) {
}
