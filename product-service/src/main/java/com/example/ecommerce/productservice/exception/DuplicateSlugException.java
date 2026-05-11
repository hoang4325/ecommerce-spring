package com.example.ecommerce.productservice.exception;

public class DuplicateSlugException extends RuntimeException {

    public DuplicateSlugException() {
        super("Slug is already in use");
    }
}
