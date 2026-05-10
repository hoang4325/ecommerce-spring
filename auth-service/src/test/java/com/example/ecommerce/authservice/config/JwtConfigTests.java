package com.example.ecommerce.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtConfigTests {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final String ISSUER = "https://auth.example.test";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(JwtConfig.class);

    @Test
    void shortJwtSecretFailsContextStartupWithClearMessage() {
        contextRunner
            .withPropertyValues("security.jwt.secret=short")
            .run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasMessageContaining("security.jwt.secret")
                .hasMessageContaining("32 bytes"));
    }

    @Test
    void thirtyTwoByteJwtSecretCreatesEncoderAndDecoder() {
        contextRunner
            .withPropertyValues(
                "security.jwt.secret=" + SECRET,
                "security.jwt.issuer=" + ISSUER
            )
            .run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(JwtEncoder.class)
                .hasSingleBean(JwtDecoder.class));
    }

    @Test
    void decoderRejectsTokenSignedWithSameSecretButWrongIssuer() {
        contextRunner
            .withPropertyValues(
                "security.jwt.secret=" + SECRET,
                "security.jwt.issuer=" + ISSUER
            )
            .run(context -> {
                JwtDecoder decoder = context.getBean(JwtDecoder.class);
                String token = token("https://wrong-issuer.example.test", Instant.now().plusSeconds(900));

                assertThatThrownBy(() -> decoder.decode(token))
                    .isInstanceOf(JwtException.class);
            });
    }

    @Test
    void decoderRejectsExpiredToken() {
        contextRunner
            .withPropertyValues(
                "security.jwt.secret=" + SECRET,
                "security.jwt.issuer=" + ISSUER
            )
            .run(context -> {
                JwtDecoder decoder = context.getBean(JwtDecoder.class);
                String token = token(ISSUER, Instant.now().minusSeconds(60));

                assertThatThrownBy(() -> decoder.decode(token))
                    .isInstanceOf(JwtException.class);
            });
    }

    @Test
    void decoderAcceptsTokenWithConfiguredIssuer() {
        contextRunner
            .withPropertyValues(
                "security.jwt.secret=" + SECRET,
                "security.jwt.issuer=" + ISSUER
            )
            .run(context -> {
                JwtDecoder decoder = context.getBean(JwtDecoder.class);
                String token = token(ISSUER, Instant.now().plusSeconds(900));

                Jwt jwt = decoder.decode(token);

                assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
                assertThat(jwt.getSubject()).isEqualTo("42");
            });
    }

    private String token(String issuer, Instant expiresAt) {
        JwtEncoder encoder = new NimbusJwtEncoder(
            new ImmutableSecret<SecurityContext>(SECRET.getBytes(StandardCharsets.UTF_8))
        );
        Instant now = Instant.now();
        Instant issuedAt = expiresAt.isBefore(now) ? expiresAt.minusSeconds(60) : now;
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("42")
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
        return encoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)
        ).getTokenValue();
    }
}
