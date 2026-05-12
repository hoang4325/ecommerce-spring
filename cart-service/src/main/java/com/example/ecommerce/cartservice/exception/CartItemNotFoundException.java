package com.example.ecommerce.cartservice.exception;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException() {
        super("Cart item not found");
    }
}
