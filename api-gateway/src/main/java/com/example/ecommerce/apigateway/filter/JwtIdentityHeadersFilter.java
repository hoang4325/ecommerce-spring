package com.example.ecommerce.apigateway.filter;

import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class JwtIdentityHeadersFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.remove(USER_ID_HEADER);
                headers.remove(USER_EMAIL_HEADER);
                headers.remove(USER_ROLES_HEADER);
            });

        return ReactiveSecurityContextHolder.getContext()
            .map(securityContext -> securityContext.getAuthentication())
            .filter(Authentication::isAuthenticated)
            .map(authentication -> addIdentityHeaders(requestBuilder, authentication))
            .defaultIfEmpty(requestBuilder)
            .flatMap(builder -> chain.filter(exchange.mutate().request(builder.build()).build()));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private ServerHttpRequest.Builder addIdentityHeaders(
        ServerHttpRequest.Builder requestBuilder,
        Authentication authentication
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            return requestBuilder;
        }

        Jwt jwt = jwtAuthenticationToken.getToken();
        requestBuilder.header(USER_ID_HEADER, jwt.getSubject());

        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            requestBuilder.header(USER_EMAIL_HEADER, email);
        }

        String roles = rolesAsHeader(jwt.getClaim("roles"));
        if (!roles.isBlank()) {
            requestBuilder.header(USER_ROLES_HEADER, roles);
        }

        return requestBuilder;
    }

    private String rolesAsHeader(Object rolesClaim) {
        if (rolesClaim instanceof Collection<?> roles) {
            return roles.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        }
        if (rolesClaim instanceof String roles) {
            return roles;
        }
        return "";
    }
}
