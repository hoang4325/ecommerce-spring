package com.example.ecommerce.authservice.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.authservice.AuthServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

@SpringBootTest(classes = AuthServiceApplication.class, properties = {
    "eureka.client.enabled=false",
    "security.jwt.secret=01234567890123456789012345678901",
    "spring.datasource.url=jdbc:h2:mem:auth_service_openapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class OpenApiEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsEndpointReturnsAuthServiceOpenApiMetadata() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Auth Service API"));
    }

    @Test
    void swaggerUiHtmlEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(isOkOrRedirect());
    }

    private static ResultMatcher isOkOrRedirect() {
        return result -> {
            int status = result.getResponse().getStatus();
            if (status != 200 && (status < 300 || status >= 400)) {
                throw new AssertionError("Expected 200 or 3xx redirect, but was " + status);
            }
        };
    }
}
