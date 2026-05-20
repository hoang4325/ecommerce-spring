package com.example.ecommerce.notificationservice.config;

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
        "spring.datasource.url=jdbc:h2:mem:notification_openapi;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "notification.internal-token=test-notification-token"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiEndpointTests {

    private final MockMvc mockMvc;

    @Autowired
    OpenApiEndpointTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void openApiDocsExposeNotificationServiceTitle() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Notification Service API"));
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection());
    }
}
