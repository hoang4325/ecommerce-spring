package com.example.ecommerce.cartservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class GatewayIdentityAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String ROLES_HEADER = "X-User-Roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Long userId = parseUserId(request.getHeader(USER_ID_HEADER));
        if (userId == null) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        GatewayUser user = new GatewayUser(userId, request.getHeader(USER_EMAIL_HEADER), roles(request));
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(user, null, authorities(user.roles()));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private static Long parseUserId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> roles(HttpServletRequest request) {
        String rolesHeader = request.getHeader(ROLES_HEADER);
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return List.of();
        }

        return Arrays.stream(rolesHeader.split(","))
            .map(String::trim)
            .filter(role -> !role.isBlank())
            .toList();
    }

    private static List<SimpleGrantedAuthority> authorities(List<String> roles) {
        return roles.stream()
            .map(GatewayIdentityAuthenticationFilter::toAuthority)
            .toList();
    }

    private static SimpleGrantedAuthority toAuthority(String role) {
        if (role.startsWith(ROLE_PREFIX)) {
            return new SimpleGrantedAuthority(role);
        }

        return new SimpleGrantedAuthority(ROLE_PREFIX + role);
    }
}
