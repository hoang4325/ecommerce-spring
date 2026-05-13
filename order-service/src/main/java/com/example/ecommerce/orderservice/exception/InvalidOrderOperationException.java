package com.example.ecommerce.orderservice.exception;

public class InvalidOrderOperationException extends RuntimeException {

    public InvalidOrderOperationException(String message) {
        super(message);
    }
}
