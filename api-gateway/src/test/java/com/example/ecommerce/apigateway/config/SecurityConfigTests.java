package com.example.ecommerce.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.example.ecommerce.apigateway.ApiGatewayApplication;

@SpringBootTest(
    classes = ApiGatewayApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.enabled=false",
        "security.jwt.secret=01234567890123456789012345678901"
    }
)
class SecurityConfigTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpointIsPublic() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void authRoutesArePublic() {
        webTestClient.post()
            .uri("/api/auth/login")
            .exchange()
            .expectStatus()
            .value(status -> assertThat(status)
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
                .isNotEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void productReadRoutesArePublic() {
        webTestClient.get()
            .uri("/api/products")
            .exchange()
            .expectStatus()
            .value(status -> assertThat(status)
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
                .isNotEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void protectedRoutesRequireAuthentication() {
        webTestClient.get()
            .uri("/api/cart")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }
}
