package com.example.ecommerce.inventoryservice.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.inventoryservice.InventoryServiceApplication;
import com.example.ecommerce.inventoryservice.dto.InventoryItemResponse;
import com.example.ecommerce.inventoryservice.dto.StockReservationResultResponse;
import com.example.ecommerce.inventoryservice.dto.StockLevelRequest;
import com.example.ecommerce.inventoryservice.entity.ReservationStatus;
import com.example.ecommerce.inventoryservice.service.InventoryService;
import com.example.ecommerce.inventoryservice.service.StockReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = InventoryServiceApplication.class, properties = {
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private StockReservationService stockReservationService;

    @Test
    void inventoryApiWithoutGatewayRoleHeaderIsForbidden() throws Exception {
        mockMvc.perform(get("/api/inventory/items"))
            .andExpect(status().isForbidden());
    }

    @Test
    void inventoryApiWithUserGatewayRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/inventory/items")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isForbidden());
    }

    @Test
    void listInventoryApiWithAdminGatewayRoleReachesController() throws Exception {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(inventoryService.list(any())).thenReturn(new PageImpl<>(List.of(inventoryItemResponse()), pageRequest, 1));

        mockMvc.perform(get("/api/inventory/items")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].productId").value(10));
    }

    @Test
    void setStockApiWithAdminGatewayRoleReachesController() throws Exception {
        StockLevelRequest request = new StockLevelRequest(25);
        when(inventoryService.setStock(eq(10L), eq(request))).thenReturn(inventoryItemResponse());

        mockMvc.perform(put("/api/inventory/items/10")
                .header("X-User-Roles", " ROLE_ADMIN, ,USER ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(10));
    }

    @Test
    void reservationEndpointAllowsServiceRole() throws Exception {
        when(stockReservationService.reserve(any())).thenReturn(
            new StockReservationResultResponse(1001L, ReservationStatus.RESERVED, List.of())
        );

        mockMvc.perform(post("/api/inventory/reservations")
                .header("X-User-Roles", "SERVICE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "orderId": 1001,
                      "items": [
                        { "productId": 10, "quantity": 2 }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void stockManagementEndpointStillRejectsServiceRole() throws Exception {
        mockMvc.perform(get("/api/inventory/items")
                .header("X-User-Roles", "SERVICE"))
            .andExpect(status().isForbidden());
    }

    @Test
    void actuatorHealthSucceedsWithoutGatewayRoleHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    private static InventoryItemResponse inventoryItemResponse() {
        Instant now = Instant.parse("2026-05-11T00:00:00Z");
        return new InventoryItemResponse(1L, 10L, 25, 0, now, now);
    }
}
