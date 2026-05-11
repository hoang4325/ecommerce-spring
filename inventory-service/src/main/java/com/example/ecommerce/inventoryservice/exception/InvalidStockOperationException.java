package com.example.ecommerce.inventoryservice.exception;

public class InvalidStockOperationException extends RuntimeException {

    public InvalidStockOperationException(String message) {
        super(message);
    }
}
