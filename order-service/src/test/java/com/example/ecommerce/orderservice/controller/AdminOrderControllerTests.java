package com.example.ecommerce.orderservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.orderservice.dto.OrderItemResponse;
import com.example.ecommerce.orderservice.dto.OrderResponse;
import com.example.ecommerce.orderservice.dto.UpdateOrderStatusRequest;
import com.example.ecommerce.orderservice.entity.OrderStatus;
import com.example.ecommerce.orderservice.exception.InvalidOrderOperationException;
import com.example.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:order_admin_controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
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
class AdminOrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @Test
    void adminListWithStatusDelegatesFilter() throws Exception {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(orderService.findAdminOrders(eq(OrderStatus.STOCK_RESERVED), any()))
            .thenReturn(new PageImpl<>(List.of(orderResponse(OrderStatus.STOCK_RESERVED)), pageRequest, 1));

        mockMvc.perform(get("/api/admin/orders?status=STOCK_RESERVED")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("STOCK_RESERVED"));

        verify(orderService).findAdminOrders(eq(OrderStatus.STOCK_RESERVED), any());
    }

    @Test
    void adminDetailReturnsOrder() throws Exception {
        when(orderService.findAdminOrder(1000L)).thenReturn(orderResponse(OrderStatus.STOCK_RESERVED));

        mockMvc.perform(get("/api/admin/orders/1000")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(1000));
    }

    @Test
    void adminCancelAcceptsCancelledStatus() throws Exception {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(
            OrderStatus.CANCELLED,
            "Customer requested"
        );
        when(orderService.cancelAsAdmin(1000L, "Customer requested"))
            .thenReturn(orderResponse(OrderStatus.CANCELLED));

        mockMvc.perform(patch("/api/admin/orders/1000/status")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void adminCancelRejectsUnsupportedStatus() throws Exception {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(
            OrderStatus.COMPLETED,
            "Unsupported transition"
        );

        mockMvc.perform(patch("/api/admin/orders/1000/status")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Only cancellation is supported"));
    }

    @Test
    void adminCancelMapsTerminalOrderConflict() throws Exception {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(
            OrderStatus.CANCELLED,
            "Second cancellation"
        );
        when(orderService.cancelAsAdmin(1000L, "Second cancellation"))
            .thenThrow(new InvalidOrderOperationException("Terminal order cannot be changed"));

        mockMvc.perform(patch("/api/admin/orders/1000/status")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Terminal order cannot be changed"));
    }

    @Test
    void userRoleReceivesForbiddenForAdminOrders() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isForbidden());
    }

    private static OrderResponse orderResponse(OrderStatus status) {
        Instant now = Instant.parse("2026-05-13T00:00:00Z");
        return new OrderResponse(
            1000L,
            10L,
            20L,
            status,
            List.of(new OrderItemResponse(
                100L,
                "Pour Over",
                new BigDecimal("19.99"),
                2,
                new BigDecimal("39.98")
            )),
            new BigDecimal("39.98"),
            status == OrderStatus.CANCELLED ? "Customer requested" : null,
            now,
            now
        );
    }
}
