package com.example.ecommerce.paymentservice.exception;

public class DuplicateOrderPaymentException extends RuntimeException {

    public DuplicateOrderPaymentException() {
        super("Payment already exists for this order");
    }
}
