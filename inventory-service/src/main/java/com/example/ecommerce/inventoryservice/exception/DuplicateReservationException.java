package com.example.ecommerce.inventoryservice.exception;

public class DuplicateReservationException extends RuntimeException {

    public DuplicateReservationException() {
        super("Order already has an active reservation");
    }
}
