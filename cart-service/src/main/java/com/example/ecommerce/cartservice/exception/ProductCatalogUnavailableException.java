package com.example.ecommerce.cartservice.exception;

public class ProductCatalogUnavailableException extends RuntimeException {

    public ProductCatalogUnavailableException() {
        super("Product catalog unavailable");
    }
}
