package com.example.ecommerce.orderservice.exception;

public class InventoryReservationFailedException extends RuntimeException {

    public InventoryReservationFailedException() {
        super("Stock reservation failed");
    }

    public InventoryReservationFailedException(String message) {
        super(message);
    }
}
