package com.example.ecommerce.orderservice.client.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.ecommerce.orderservice.config.GatewayUser;
import com.example.ecommerce.orderservice.exception.CartServiceUnavailableException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientCartClientTests {

    private MockRestServiceServer server;
    private CartClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://cart-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientCartClient(builder.build());
    }

    @Test
    void getCurrentCartForwardsGatewayIdentityAndReturnsSnapshot() {
        server.expect(once(), requestTo("http://cart-service/api/cart"))
            .andExpect(method(GET))
            .andExpect(header("X-User-Id", "10"))
            .andExpect(header("X-User-Email", "user@example.com"))
            .andExpect(header("X-User-Roles", "USER"))
            .andRespond(withSuccess("""
                {
                  "cartId": 20,
                  "userId": 10,
                  "status": "ACTIVE",
                  "items": [
                    {
                      "productId": 100,
                      "productName": "Pour Over",
                      "unitPrice": 19.99,
                      "quantity": 2,
                      "lineTotal": 39.98
                    }
                  ],
                  "subtotal": 39.98
                }
                """, APPLICATION_JSON));

        CartSnapshot cart = client.getCurrentCart(new GatewayUser(10L, "user@example.com", List.of()));

        assertThat(cart.cartId()).isEqualTo(20L);
        assertThat(cart.userId()).isEqualTo(10L);
        assertThat(cart.status()).isEqualTo("ACTIVE");
        assertThat(cart.subtotal()).isEqualByComparingTo(new BigDecimal("39.98"));
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().productId()).isEqualTo(100L);
        assertThat(cart.items().getFirst().lineTotal()).isEqualByComparingTo(new BigDecimal("39.98"));
        server.verify();
    }

    @Test
    void cartSnapshotCopiesItemsDefensively() {
        CartItemSnapshot item = new CartItemSnapshot(
            100L,
            "Pour Over",
            new BigDecimal("19.99"),
            2,
            new BigDecimal("39.98")
        );
        List<CartItemSnapshot> items = new ArrayList<>();
        items.add(item);

        CartSnapshot cart = new CartSnapshot(20L, 10L, "ACTIVE", items, new BigDecimal("39.98"));
        items.clear();

        assertThat(cart.items()).containsExactly(item);
        assertThatThrownBy(() -> cart.items().add(item)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getCurrentCartMapsServerErrorToCartServiceUnavailableException() {
        server.expect(once(), requestTo("http://cart-service/api/cart"))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getCurrentCart(new GatewayUser(10L, "user@example.com", List.of("USER"))))
            .isInstanceOf(CartServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void getCurrentCartMapsEmptySuccessfulBodyToCartServiceUnavailableException() {
        server.expect(once(), requestTo("http://cart-service/api/cart"))
            .andRespond(withSuccess("", APPLICATION_JSON));

        assertThatThrownBy(() -> client.getCurrentCart(new GatewayUser(10L, "user@example.com", List.of("USER"))))
            .isInstanceOf(CartServiceUnavailableException.class);
        server.verify();
    }
}
