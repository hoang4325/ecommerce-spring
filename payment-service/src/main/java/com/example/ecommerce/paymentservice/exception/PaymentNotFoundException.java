package com.example.ecommerce.paymentservice.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long id) {
        super("Payment not found: " + id);
    }

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
