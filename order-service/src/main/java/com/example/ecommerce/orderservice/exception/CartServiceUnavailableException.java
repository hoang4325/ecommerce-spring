package com.example.ecommerce.orderservice.exception;

public class CartServiceUnavailableException extends RuntimeException {

    public CartServiceUnavailableException() {
        super("Cart service unavailable");
    }
}
