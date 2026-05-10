package com.example.ecommerce.authservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.authservice.entity.AuthUser;
import com.example.ecommerce.authservice.entity.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auth_service_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthUserRepositoryTests {

    @Autowired
    private AuthUserRepository authUserRepository;

    @Test
    void savingUserNormalizesEmailAndCanBeFoundIgnoringCase() {
        AuthUser user = AuthUser.create(" Customer@Example.com ", "hash", Set.of(Role.USER));
        authUserRepository.saveAndFlush(user);

        assertThat(user.getEmail()).isEqualTo("customer@example.com");
        assertThat(authUserRepository.findByEmailIgnoreCase("CUSTOMER@example.COM"))
            .hasValueSatisfying(found -> {
                assertThat(found.getEmail()).isEqualTo("customer@example.com");
                assertThat(found.getRoles()).containsExactly(Role.USER);
            });
    }

    @Test
    void normalizedUniqueEmailConstraintPreventsDuplicateEmailWithDifferentCasing() {
        authUserRepository.saveAndFlush(AuthUser.create("Duplicate@Example.com", "hash-1", Set.of(Role.USER)));

        AuthUser duplicate = AuthUser.create("duplicate@example.com", "hash-2", Set.of(Role.ADMIN));

        assertThatThrownBy(() -> authUserRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
