package com.example.ecommerce.authservice.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = BcryptPasswordValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface BcryptPassword {

    String message() default "must be 72 UTF-8 bytes or fewer";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
