package com.example.ecommerce.authservice.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.authservice.AuthServiceApplication;
import com.example.ecommerce.authservice.dto.AuthResponse;
import com.example.ecommerce.authservice.dto.LoginRequest;
import com.example.ecommerce.authservice.dto.RegisterRequest;
import com.example.ecommerce.authservice.entity.Role;
import com.example.ecommerce.authservice.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AuthServiceApplication.class, properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false",
    "security.jwt.secret=01234567890123456789012345678901",
    "spring.datasource.url=jdbc:h2:mem:auth_endpoint_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AuthEndpointSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void unauthenticatedRegisterSucceedsWithoutCsrfTokenOrJwt() throws Exception {
        RegisterRequest request = new RegisterRequest("customer@example.com", "password123", "Customer Name");
        when(authService.register(request)).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        verify(authService).register(request);
    }

    @Test
    void unauthenticatedLoginSucceedsWithoutCsrfTokenOrJwt() throws Exception {
        LoginRequest request = new LoginRequest("customer@example.com", "password123");
        when(authService.login(request)).thenReturn(authResponse());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        verify(authService).login(request);
    }

    private AuthResponse authResponse() {
        return new AuthResponse("jwt-token", "Bearer", 3600, 42L, "customer@example.com", Set.of(Role.USER));
    }
}
