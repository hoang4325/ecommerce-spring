package com.example.ecommerce.cartservice.exception;

public class MissingUserIdentityException extends RuntimeException {

    public MissingUserIdentityException() {
        super("Missing user identity");
    }
}
