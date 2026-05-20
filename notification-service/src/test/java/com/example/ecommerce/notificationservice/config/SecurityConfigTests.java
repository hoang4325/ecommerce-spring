package com.example.ecommerce.notificationservice.config;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_security;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "notification.internal-token=test-notification-token"
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
    void adminEndpointRequiresIdentity() throws Exception {
        mockMvc.perform(get("/api/admin/notifications"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing user identity"));
    }

    @Test
    void adminEndpointRejectsUserRole() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void adminEndpointAllowsAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().is(not(401)))
            .andExpect(status().is(not(403)));
    }

    @Test
    void internalEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/api/internal/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpointRejectsWrongToken() throws Exception {
        mockMvc.perform(post("/api/internal/notifications")
                .header("X-Internal-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpointAllowsCorrectToken() throws Exception {
        mockMvc.perform(post("/api/internal/notifications")
                .header("X-Internal-Token", "test-notification-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().is(not(401)))
            .andExpect(status().is(not(403)));
    }
}
