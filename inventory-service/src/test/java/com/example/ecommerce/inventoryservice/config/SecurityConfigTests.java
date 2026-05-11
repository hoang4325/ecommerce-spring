package com.example.ecommerce.inventoryservice.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.inventoryservice.InventoryServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {
    InventoryServiceApplication.class,
    SecurityConfigTests.InventoryProbeController.class
}, properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:inventory_service_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void inventoryApiWithoutGatewayRoleHeaderIsForbidden() throws Exception {
        mockMvc.perform(get("/api/inventory/probe"))
            .andExpect(status().isForbidden());
    }

    @Test
    void inventoryApiWithUserGatewayRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/inventory/probe")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isForbidden());
    }

    @Test
    void inventoryApiWithAdminGatewayRoleSucceeds() throws Exception {
        mockMvc.perform(get("/api/inventory/probe")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(content().string("inventory-ok"));
    }

    @Test
    void inventoryApiWithPrefixedAdminGatewayRoleSucceeds() throws Exception {
        mockMvc.perform(get("/api/inventory/probe")
                .header("X-User-Roles", " ROLE_ADMIN, ,USER "))
            .andExpect(status().isOk())
            .andExpect(content().string("inventory-ok"));
    }

    @Test
    void actuatorHealthSucceedsWithoutGatewayRoleHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    @RestController
    static class InventoryProbeController {

        @GetMapping("/api/inventory/probe")
        String probe() {
            return "inventory-ok";
        }
    }
}
