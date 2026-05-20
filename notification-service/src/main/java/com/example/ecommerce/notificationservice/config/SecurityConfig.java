package com.example.ecommerce.notificationservice.config;

import com.example.ecommerce.notificationservice.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        GatewayIdentityAuthenticationFilter gatewayIdentityAuthenticationFilter,
        InternalTokenAuthenticationFilter internalTokenAuthenticationFilter,
        ObjectMapper objectMapper
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> writeError(
                    response,
                    objectMapper,
                    HttpStatus.UNAUTHORIZED,
                    "Missing user identity",
                    request.getRequestURI()
                ))
                .accessDeniedHandler((request, response, accessDeniedException) -> writeError(
                    response,
                    objectMapper,
                    HttpStatus.FORBIDDEN,
                    "Access denied",
                    request.getRequestURI()
                )))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**"
                ).permitAll()
                .requestMatchers("/api/internal/notifications").hasRole("INTERNAL")
                .requestMatchers("/api/admin/notifications/**", "/api/admin/notifications").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(internalTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(gatewayIdentityAuthenticationFilter, InternalTokenAuthenticationFilter.class)
            .build();
    }

    private static void writeError(
        jakarta.servlet.http.HttpServletResponse response,
        ObjectMapper objectMapper,
        HttpStatus status,
        String message,
        String path
    ) throws java.io.IOException {
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
