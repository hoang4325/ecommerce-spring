package com.example.ecommerce.authservice.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
class JwtConfig {

    @Bean
    JwtEncoder jwtEncoder(@Value("${security.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(validatedSecretBytes(secret)));
    }

    @Bean
    JwtDecoder jwtDecoder(
        @Value("${security.jwt.secret}") String secret,
        @Value("${security.jwt.issuer}") String issuer
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(validatedSecretBytes(secret), "HmacSHA256"))
            .build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private byte[] validatedSecretBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("security.jwt.secret must be nonblank and at least 32 bytes for HS256");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 bytes for HS256");
        }

        return secretBytes;
    }
}
