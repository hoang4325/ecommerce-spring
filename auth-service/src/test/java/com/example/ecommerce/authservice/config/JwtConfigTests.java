package com.example.ecommerce.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtConfigTests {

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
            .withPropertyValues("security.jwt.secret=01234567890123456789012345678901")
            .run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(JwtEncoder.class)
                .hasSingleBean(JwtDecoder.class));
    }
}
