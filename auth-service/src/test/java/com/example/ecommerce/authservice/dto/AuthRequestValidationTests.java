package com.example.ecommerce.authservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class AuthRequestValidationTests {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void registerRequestRejectsPasswordOverBcryptByteLimit() {
        RegisterRequest request = new RegisterRequest(
            "customer@example.com",
            "a".repeat(73),
            "Customer Name"
        );

        Set<String> invalidFields = invalidFields(request);

        assertThat(invalidFields).contains("password");
    }

    @Test
    void loginRequestRejectsPasswordOverBcryptByteLimit() {
        LoginRequest request = new LoginRequest("customer@example.com", "a".repeat(73));

        Set<String> invalidFields = invalidFields(request);

        assertThat(invalidFields).contains("password");
    }

    @Test
    void registerRequestAcceptsValidPasswordWithinBcryptByteLimit() {
        RegisterRequest request = new RegisterRequest(
            "customer@example.com",
            "password123",
            "Customer Name"
        );

        Set<String> invalidFields = invalidFields(request);

        assertThat(invalidFields).doesNotContain("password");
    }

    private Set<String> invalidFields(Object request) {
        return VALIDATOR.validate(request).stream()
            .filter(violation -> violation.getConstraintDescriptor().getAnnotation().annotationType() != NotBlank.class)
            .map(violation -> violation.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }
}
