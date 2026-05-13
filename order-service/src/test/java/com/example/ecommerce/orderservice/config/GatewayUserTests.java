package com.example.ecommerce.orderservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayUserTests {

    @Test
    void gatewayUserCopiesAndNormalizesRoles() {
        List<String> roles = new ArrayList<>(List.of("ROLE_ADMIN", "USER", " ROLE_USER ", "", "ROLE_", "ADMIN"));

        GatewayUser user = new GatewayUser(10L, "user@example.com", roles);
        roles.add("SUPER_ADMIN");

        assertThat(user.roles()).containsExactly("ADMIN", "USER");
        assertThatThrownBy(() -> user.roles().add("SUPER_ADMIN")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void gatewayUserUsesEmptyRolesWhenRoleListIsNull() {
        assertThat(new GatewayUser(10L, "user@example.com", null).roles()).isEmpty();
    }
}
