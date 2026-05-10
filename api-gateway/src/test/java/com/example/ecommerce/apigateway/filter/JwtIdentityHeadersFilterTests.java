package com.example.ecommerce.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import reactor.core.publisher.Mono;

class JwtIdentityHeadersFilterTests {

    private final JwtIdentityHeadersFilter filter = new JwtIdentityHeadersFilter();

    @Test
    void runsBeforeNettyRoutingFilter() {
        assertThat(filter.getOrder()).isLessThan(NettyRoutingFilter.ORDER);
    }

    @Test
    void removesSpoofedIdentityHeadersWhenNoJwtIsPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/cart")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Email", "spoofed@example.com")
                .header("X-User-Roles", "ADMIN")
        );
        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            forwardedRequest.set(chainExchange.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwardedRequest.get().getHeaders();
        assertThat(headers.containsKey("X-User-Id")).isFalse();
        assertThat(headers.containsKey("X-User-Email")).isFalse();
        assertThat(headers.containsKey("X-User-Roles")).isFalse();
    }

    @Test
    void forwardsTrustedIdentityHeadersFromJwtClaims() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/cart")
                .header("X-User-Id", "spoofed-user")
        );
        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            forwardedRequest.set(chainExchange.getRequest());
            return Mono.empty();
        };
        Jwt jwt = new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "HS256"),
            Map.of(
                "sub", "user-123",
                "email", "user@example.com",
                "roles", List.of("USER", "ADMIN")
            )
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        filter.filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block();

        HttpHeaders headers = forwardedRequest.get().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-123");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(headers.getFirst("X-User-Roles")).isEqualTo("USER,ADMIN");
    }
}
