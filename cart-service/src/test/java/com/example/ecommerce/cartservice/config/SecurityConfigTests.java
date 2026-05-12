package com.example.ecommerce.cartservice.config;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.cartservice.CartServiceApplication;
import com.example.ecommerce.cartservice.dto.CartItemResponse;
import com.example.ecommerce.cartservice.dto.CartResponse;
import com.example.ecommerce.cartservice.entity.CartStatus;
import com.example.ecommerce.cartservice.service.CartService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = CartServiceApplication.class, properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:cart_service_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Test
    void cartApiWithoutGatewayUserIdIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cart"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(cartService);
    }

    @Test
    void cartApiWithGatewayUserIdReachesController() throws Exception {
        when(cartService.getCurrentCart(10L)).thenReturn(cartResponse());

        mockMvc.perform(get("/api/cart")
                .header("X-User-Id", "10")
                .header("X-User-Email", "customer@example.com")
                .header("X-User-Roles", " USER, ROLE_ADMIN ,, "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(10))
            .andExpect(jsonPath("$.items[0].productId").value(20));
    }

    @Test
    void cartApiWithInvalidGatewayUserIdIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cart")
                .header("X-User-Id", "not-a-number")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(cartService);
    }

    @Test
    void actuatorHealthSucceedsWithoutGatewayHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    private static CartResponse cartResponse() {
        BigDecimal unitPrice = new BigDecimal("19.99");
        BigDecimal lineTotal = new BigDecimal("39.98");
        return new CartResponse(
            100L,
            10L,
            CartStatus.ACTIVE,
            List.of(new CartItemResponse(20L, "Pour Over", unitPrice, 2, lineTotal)),
            lineTotal
        );
    }
}
