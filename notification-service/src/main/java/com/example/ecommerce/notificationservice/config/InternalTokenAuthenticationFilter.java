package com.example.ecommerce.notificationservice.config;

import com.example.ecommerce.notificationservice.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class InternalTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_NOTIFICATIONS_PATH = "/api/internal/notifications";

    private final NotificationProperties notificationProperties;
    private final ObjectMapper objectMapper;

    InternalTokenAuthenticationFilter(NotificationProperties notificationProperties, ObjectMapper objectMapper) {
        this.notificationProperties = notificationProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INTERNAL_NOTIFICATIONS_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!hasValidInternalToken(request)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid internal token", request.getRequestURI());
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "internal-notification-client",
            null,
            List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private boolean hasValidInternalToken(HttpServletRequest request) {
        String expectedToken = notificationProperties.internalToken();
        return expectedToken != null
            && !expectedToken.isBlank()
            && Objects.equals(expectedToken, request.getHeader(INTERNAL_TOKEN_HEADER));
    }

    private void writeError(
        HttpServletResponse response,
        HttpStatus status,
        String message,
        String path
    ) throws IOException {
        ApiErrorResponse error = new ApiErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            path,
            List.of()
        );
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
