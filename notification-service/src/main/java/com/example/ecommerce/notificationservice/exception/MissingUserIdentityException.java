package com.example.ecommerce.notificationservice.exception;

public class MissingUserIdentityException extends RuntimeException {

    public MissingUserIdentityException() {
        super("Missing user identity");
    }
}
