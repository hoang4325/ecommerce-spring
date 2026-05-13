package com.example.ecommerce.orderservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.orderservice.dto.OrderItemResponse;
import com.example.ecommerce.orderservice.dto.OrderResponse;
import com.example.ecommerce.orderservice.entity.OrderStatus;
import com.example.ecommerce.orderservice.exception.EmptyCartException;
import com.example.ecommerce.orderservice.exception.InventoryServiceUnavailableException;
import com.example.ecommerce.orderservice.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:order_controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
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
class OrderControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void checkoutReturnsCreatedOrder() throws Exception {
        when(orderService.checkout(any())).thenReturn(orderResponse(OrderStatus.STOCK_RESERVED));

        mockMvc.perform(post("/api/orders/checkout")
                .header("X-User-Id", "10")
                .header("X-User-Email", "user@example.com")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("STOCK_RESERVED"))
            .andExpect(jsonPath("$.items[0].productId").value(100));
    }

    @Test
    void checkoutMapsEmptyCartToConflict() throws Exception {
        when(orderService.checkout(any())).thenThrow(new EmptyCartException());

        mockMvc.perform(post("/api/orders/checkout")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Cart is empty"));
    }

    @Test
    void checkoutMapsInventoryUnavailableToServiceUnavailable() throws Exception {
        when(orderService.checkout(any())).thenThrow(new InventoryServiceUnavailableException());

        mockMvc.perform(post("/api/orders/checkout")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value("Inventory service unavailable"));
    }

    @Test
    void listOrdersReturnsCurrentUserOrders() throws Exception {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(orderService.findCurrentUserOrders(eq(10L), any()))
            .thenReturn(new PageImpl<>(List.of(orderResponse(OrderStatus.STOCK_RESERVED)), pageRequest, 1));

        mockMvc.perform(get("/api/orders")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].orderId").value(1000));
    }

    @Test
    void getOrderReturnsCurrentUserOrder() throws Exception {
        when(orderService.findCurrentUserOrder(10L, 1000L)).thenReturn(orderResponse(OrderStatus.STOCK_RESERVED));

        mockMvc.perform(get("/api/orders/1000")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(1000));
    }

    @Test
    void missingGatewayUserReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing user identity"));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/api/orders")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownAuthenticatedPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/unknown")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isNotFound());
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
            null,
            now,
            now
        );
    }
}
