package com.example.ecommerce.orderservice.client.cart;

import com.example.ecommerce.orderservice.config.GatewayUser;
import com.example.ecommerce.orderservice.exception.CartServiceUnavailableException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestClientCartClient implements CartClient {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String ROLES_HEADER = "X-User-Roles";
    private static final String DEFAULT_USER_ROLE = "USER";

    private final RestClient cartServiceRestClient;

    public RestClientCartClient(@Qualifier("cartServiceRestClient") RestClient cartServiceRestClient) {
        this.cartServiceRestClient = cartServiceRestClient;
    }

    @Override
    public CartSnapshot getCurrentCart(GatewayUser user) {
        try {
            CartSnapshot cart = cartServiceRestClient.get()
                .uri("/api/cart")
                .headers(headers -> applyGatewayHeaders(headers, user))
                .retrieve()
                .body(CartSnapshot.class);

            if (cart == null) {
                throw new CartServiceUnavailableException();
            }

            return cart;
        } catch (RestClientException ex) {
            throw new CartServiceUnavailableException();
        }
    }

    private static void applyGatewayHeaders(HttpHeaders headers, GatewayUser user) {
        headers.set(USER_ID_HEADER, user.id().toString());
        if (user.email() != null && !user.email().isBlank()) {
            headers.set(USER_EMAIL_HEADER, user.email());
        }
        headers.set(ROLES_HEADER, String.join(",", roles(user)));
    }

    private static List<String> roles(GatewayUser user) {
        return user.roles().isEmpty() ? List.of(DEFAULT_USER_ROLE) : user.roles();
    }
}
