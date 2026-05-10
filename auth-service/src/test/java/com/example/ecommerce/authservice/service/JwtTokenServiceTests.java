package com.example.ecommerce.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.authservice.entity.AuthUser;
import com.example.ecommerce.authservice.entity.Role;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTests {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final String ISSUER = "auth-service-test";
    private static final long EXPIRATION_SECONDS = 900;

    @Test
    void issuedTokenContainsSubjectEmailRolesAndIssuer() {
        JwtTokenService jwtTokenService = new JwtTokenService(
            new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(SECRET.getBytes(StandardCharsets.UTF_8))),
            ISSUER,
            EXPIRATION_SECONDS
        );
        AuthUser user = AuthUser.create("Customer@Example.com", "hash", Set.of(Role.USER, Role.ADMIN));
        ReflectionTestUtils.setField(user, "id", 42L);

        String token = jwtTokenService.issueToken(user);

        Jwt jwt = NimbusJwtDecoder
            .withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
            .build()
            .decode(token);
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("customer@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
    }

    @Test
    void expiresInSecondsReturnsConfiguredExpiration() {
        JwtTokenService jwtTokenService = new JwtTokenService(
            new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(SECRET.getBytes(StandardCharsets.UTF_8))),
            ISSUER,
            EXPIRATION_SECONDS
        );

        assertThat(jwtTokenService.expiresInSeconds()).isEqualTo(EXPIRATION_SECONDS);
    }
}
