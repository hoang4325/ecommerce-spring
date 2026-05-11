package com.example.ecommerce.inventoryservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class GatewayIdentityAuthenticationFilter extends OncePerRequestFilter {

    private static final String ROLES_HEADER = "X-User-Roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String rolesHeader = request.getHeader(ROLES_HEADER);

        if (rolesHeader != null && !rolesHeader.isBlank()) {
            List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(GatewayIdentityAuthenticationFilter::toAuthority)
                .toList();

            if (!authorities.isEmpty()) {
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken("gateway-user", null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private static SimpleGrantedAuthority toAuthority(String role) {
        if (role.startsWith(ROLE_PREFIX)) {
            return new SimpleGrantedAuthority(role);
        }

        return new SimpleGrantedAuthority(ROLE_PREFIX + role);
    }
}
