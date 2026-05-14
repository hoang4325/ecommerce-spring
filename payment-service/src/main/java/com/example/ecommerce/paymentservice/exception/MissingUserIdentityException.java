package com.example.ecommerce.paymentservice.exception;

public class MissingUserIdentityException extends RuntimeException {

    public MissingUserIdentityException() {
        super("Missing user identity");
    }
}
