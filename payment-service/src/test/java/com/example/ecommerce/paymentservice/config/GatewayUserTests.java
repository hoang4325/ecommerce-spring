package com.example.ecommerce.paymentservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayUserTests {

    @Test
    void gatewayUserCopiesAndNormalizesRoles() {
        GatewayUser user = new GatewayUser(10L, "user@example.com", List.of("ROLE_ADMIN", "USER"));

        assertThat(user.roles()).containsExactly("ADMIN", "USER");
        assertThatThrownBy(() -> user.roles().add("SUPPORT"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
