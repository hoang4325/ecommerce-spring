package com.example.ecommerce.cartservice.exception;

public class InvalidCartOperationException extends RuntimeException {

    public InvalidCartOperationException(String message) {
        super(message);
    }
}
