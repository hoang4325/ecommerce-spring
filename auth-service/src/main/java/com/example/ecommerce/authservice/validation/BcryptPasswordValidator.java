package com.example.ecommerce.authservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class BcryptPasswordValidator implements ConstraintValidator<BcryptPassword, String> {

    private static final int BCRYPT_PASSWORD_BYTE_LIMIT = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_PASSWORD_BYTE_LIMIT;
    }
}
