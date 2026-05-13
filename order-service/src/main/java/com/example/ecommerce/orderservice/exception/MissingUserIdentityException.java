package com.example.ecommerce.orderservice.exception;

public class MissingUserIdentityException extends RuntimeException {

    public MissingUserIdentityException() {
        super("Missing user identity");
    }
}
