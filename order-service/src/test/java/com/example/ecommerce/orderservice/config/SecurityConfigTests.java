package com.example.ecommerce.orderservice.config;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:order_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "clients.cart-service.base-url=http://cart-service",
        "clients.inventory-service.base-url=http://inventory-service"
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

    @MockitoBean
    private OrderService orderService;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @Test
    void livenessIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().is(not(401)));
    }

    @Test
    void ordersRequireUserIdentity() throws Exception {
        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing user identity"));
    }

    @Test
    void ordersWithUserIdentityAreAuthenticated() throws Exception {
        mockMvc.perform(get("/api/orders").header("X-User-Id", "10"))
            .andExpect(status().is(not(401)));
    }

    @Test
    void adminOrdersRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminOrdersAllowAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().is(not(403)));
    }
}
