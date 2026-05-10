package com.example.ecommerce.authservice.controller;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.authservice.dto.AuthResponse;
import com.example.ecommerce.authservice.dto.LoginRequest;
import com.example.ecommerce.authservice.dto.RegisterRequest;
import com.example.ecommerce.authservice.entity.Role;
import com.example.ecommerce.authservice.exception.DuplicateEmailException;
import com.example.ecommerce.authservice.exception.GlobalExceptionHandler;
import com.example.ecommerce.authservice.exception.InvalidCredentialsException;
import com.example.ecommerce.authservice.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerValidRequestReturnsCreatedTokenResponse() throws Exception {
        RegisterRequest request = new RegisterRequest("customer@example.com", "password123", "Customer Name");
        AuthResponse response = authResponse();
        when(authService.register(request)).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.accessToken").value("jwt-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andExpect(jsonPath("$.userId").value(42))
            .andExpect(jsonPath("$.email").value("customer@example.com"))
            .andExpect(jsonPath("$.roles[0]").value("USER"));

        verify(authService).register(request);
    }

    @Test
    void loginValidRequestReturnsOkTokenResponse() throws Exception {
        LoginRequest request = new LoginRequest("customer@example.com", "password123");
        AuthResponse response = authResponse();
        when(authService.login(request)).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.accessToken").value("jwt-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andExpect(jsonPath("$.userId").value(42))
            .andExpect(jsonPath("$.email").value("customer@example.com"))
            .andExpect(jsonPath("$.roles[0]").value("USER"));

        verify(authService).login(request);
    }

    @Test
    void registerInvalidEmailReturnsBadRequestFieldDetail() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "password123", "Customer Name");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.path").value("/api/auth/register"))
            .andExpect(jsonPath("$.details[*].field").value(hasItem("email")));

        verifyNoInteractions(authService);
    }

    @Test
    void registerOverBcryptByteLimitPasswordReturnsBadRequestFieldDetail() throws Exception {
        RegisterRequest request = new RegisterRequest("customer@example.com", "a".repeat(73), "Customer Name");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.path").value("/api/auth/register"))
            .andExpect(jsonPath("$.details[*].field").value(hasItem("password")))
            .andExpect(jsonPath("$.details[*].message").value(hasItem("must be 72 UTF-8 bytes or fewer")));

        verifyNoInteractions(authService);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        RegisterRequest request = new RegisterRequest("customer@example.com", "password123", "Customer Name");
        when(authService.register(any(RegisterRequest.class))).thenThrow(new DuplicateEmailException());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Conflict"))
            .andExpect(jsonPath("$.message").value("Email is already registered"))
            .andExpect(jsonPath("$.path").value("/api/auth/register"))
            .andExpect(jsonPath("$.details").value(empty()));
    }

    @Test
    void invalidCredentialsReturnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest("customer@example.com", "password123");
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("Invalid email or password"))
            .andExpect(jsonPath("$.path").value("/api/auth/login"))
            .andExpect(jsonPath("$.details").value(empty()));
    }

    @Test
    void unexpectedExceptionReturnsInternalServerErrorWithoutLeakingRawMessage() throws Exception {
        LoginRequest request = new LoginRequest("customer@example.com", "password123");
        when(authService.login(any(LoginRequest.class))).thenThrow(new IllegalStateException("database password leaked"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.error").value("Internal Server Error"))
            .andExpect(jsonPath("$.message").value("Unexpected server error"))
            .andExpect(jsonPath("$.path").value("/api/auth/login"))
            .andExpect(jsonPath("$.details").value(empty()));
    }

    private AuthResponse authResponse() {
        return new AuthResponse("jwt-token", "Bearer", 3600, 42L, "customer@example.com", Set.of(Role.USER));
    }
}
