package com.example.ecommerce.paymentservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.example.ecommerce.paymentservice.dto.PaymentResponse;
import com.example.ecommerce.paymentservice.dto.SimulatePaymentResult;
import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import com.example.ecommerce.paymentservice.exception.DuplicateOrderPaymentException;
import com.example.ecommerce.paymentservice.service.PaymentService;
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
        "spring.datasource.url=jdbc:h2:mem:payment_controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void createPaymentReturnsCreatedPayment() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(
            1000L,
            new BigDecimal("39.98"),
            PaymentMethod.CARD,
            SimulatePaymentResult.SUCCESS
        );
        when(paymentService.create(any(), any())).thenReturn(paymentResponse(PaymentStatus.SUCCESS));

        mockMvc.perform(post("/api/payments")
                .header("X-User-Id", "10")
                .header("X-User-Email", "user@example.com")
                .header("X-User-Roles", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void listPaymentsReturnsCurrentUserPayments() throws Exception {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(paymentService.findCurrentUserPayments(eq(10L), any()))
            .thenReturn(new PageImpl<>(List.of(paymentResponse(PaymentStatus.SUCCESS)), pageRequest, 1));

        mockMvc.perform(get("/api/payments")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].paymentId").value(2000));
    }

    @Test
    void getPaymentReturnsCurrentUserPayment() throws Exception {
        when(paymentService.findCurrentUserPayment(10L, 2000L)).thenReturn(paymentResponse(PaymentStatus.SUCCESS));

        mockMvc.perform(get("/api/payments/2000")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(2000));
    }

    @Test
    void getPaymentByOrderReturnsCurrentUserPayment() throws Exception {
        when(paymentService.findCurrentUserPaymentByOrder(10L, 1000L))
            .thenReturn(paymentResponse(PaymentStatus.SUCCESS));

        mockMvc.perform(get("/api/payments/by-order/1000")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(1000));
    }

    @Test
    void missingGatewayUserReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/payments"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing user identity"));
    }

    @Test
    void validationErrorReturnsBadRequest() throws Exception {
        String request = """
            {
              "orderId": null,
              "amount": 0,
              "method": null
            }
            """;

        mockMvc.perform(post("/api/payments")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details").isArray())
            .andExpect(jsonPath("$.details.length()").value(3));
    }

    @Test
    void duplicateOrderPaymentMapsToConflict() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(
            1000L,
            new BigDecimal("39.98"),
            PaymentMethod.CARD,
            SimulatePaymentResult.SUCCESS
        );
        when(paymentService.create(any(), any())).thenThrow(new DuplicateOrderPaymentException());

        mockMvc.perform(post("/api/payments")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Payment already exists for this order"));
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/payments")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER")
                .contentType(MediaType.TEXT_PLAIN)
                .content("not json"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/api/payments")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isMethodNotAllowed());
    }

    private static PaymentResponse paymentResponse(PaymentStatus status) {
        Instant now = Instant.parse("2026-05-13T00:00:00Z");
        return new PaymentResponse(
            2000L,
            1000L,
            10L,
            new BigDecimal("39.98"),
            PaymentMethod.CARD,
            status,
            status == PaymentStatus.FAILED ? "Payment failed" : null,
            now,
            now
        );
    }
}
