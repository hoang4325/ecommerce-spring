package com.example.ecommerce.orderservice.exception;

public class InventoryServiceUnavailableException extends RuntimeException {

    public InventoryServiceUnavailableException() {
        super("Inventory service unavailable");
    }
}
