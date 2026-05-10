package com.example.ecommerce.authservice.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.authservice.entity.Role;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthResponseTests {

    @Test
    void copiesRolesSetDefensively() {
        Set<Role> roles = new HashSet<>();
        roles.add(Role.USER);
        AuthResponse response = new AuthResponse("token", "Bearer", 900, 1L, "customer@example.com", roles);

        roles.add(Role.ADMIN);

        assertThat(response.roles()).containsExactly(Role.USER);
        assertThatThrownBy(() -> response.roles().add(Role.ADMIN))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullRolesBecomeEmptySet() {
        AuthResponse response = new AuthResponse("token", "Bearer", 900, 1L, "customer@example.com", null);

        assertThat(response.roles()).isEmpty();
    }
}
