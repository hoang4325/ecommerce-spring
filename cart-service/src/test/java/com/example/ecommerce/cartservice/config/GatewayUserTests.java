package com.example.ecommerce.cartservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayUserTests {

    @Test
    void nullRolesBecomeEmptyList() {
        GatewayUser user = new GatewayUser(10L, "customer@example.com", null);

        assertThat(user.roles()).isEmpty();
    }

    @Test
    void rolesAreTrimmedFilteredAndDefensivelyCopied() {
        List<String> roles = new ArrayList<>(List.of(" USER ", "", "ROLE_ADMIN"));

        GatewayUser user = new GatewayUser(10L, "customer@example.com", roles);
        roles.add("OTHER");

        assertThat(user.roles()).containsExactly("USER", "ROLE_ADMIN");
    }
}
