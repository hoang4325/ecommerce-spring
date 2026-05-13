package com.example.ecommerce.orderservice.client.cart;

import com.example.ecommerce.orderservice.config.GatewayUser;

public interface CartClient {

    CartSnapshot getCurrentCart(GatewayUser user);
}
