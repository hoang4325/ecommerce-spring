package com.example.ecommerce.orderservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayUserTests {

    @Test
    void gatewayUserCopiesAndNormalizesRoles() {
        assertThat(new GatewayUser(10L, "user@example.com", List.of("ROLE_ADMIN", "USER")).roles())
            .containsExactly("ADMIN", "USER");
    }
}
