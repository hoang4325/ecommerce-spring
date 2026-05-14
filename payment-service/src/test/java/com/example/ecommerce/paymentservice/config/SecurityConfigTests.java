package com.example.ecommerce.paymentservice.config;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_security;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTests {

    private final MockMvc mockMvc;

    @Autowired
    SecurityConfigTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void paymentEndpointRequiresIdentity() throws Exception {
        mockMvc.perform(get("/api/payments"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing user identity"));
    }

    @Test
    void paymentEndpointWithIdentityPassesSecurity() throws Exception {
        mockMvc.perform(get("/api/payments").header("X-User-Id", "10"))
            .andExpect(status().is(not(401)))
            .andExpect(status().is(not(403)));
    }

    @Test
    void adminEndpointRejectsUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void adminEndpointAllowsAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().is(not(401)))
            .andExpect(status().is(not(403)));
    }

    @Test
    void invalidUserIdHeaderOnProtectedEndpointReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/payments").header("X-User-Id", "abc"))
            .andExpect(status().isUnauthorized());
    }
}
