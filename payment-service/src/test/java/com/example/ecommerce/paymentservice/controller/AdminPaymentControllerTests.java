package com.example.ecommerce.paymentservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.paymentservice.dto.PaymentResponse;
import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import com.example.ecommerce.paymentservice.exception.InvalidPaymentOperationException;
import com.example.ecommerce.paymentservice.service.PaymentService;
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
        "spring.datasource.url=jdbc:h2:mem:payment_admin_controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPaymentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void adminListCanFilterByStatus() throws Exception {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(paymentService.findAdminPayments(eq(PaymentStatus.SUCCESS), any()))
            .thenReturn(new PageImpl<>(List.of(paymentResponse(PaymentStatus.SUCCESS)), pageRequest, 1));

        mockMvc.perform(get("/api/admin/payments?status=SUCCESS")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));

        verify(paymentService).findAdminPayments(eq(PaymentStatus.SUCCESS), any());
    }

    @Test
    void adminDetailReturnsPayment() throws Exception {
        when(paymentService.findAdminPayment(2000L)).thenReturn(paymentResponse(PaymentStatus.SUCCESS));

        mockMvc.perform(get("/api/admin/payments/2000")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(2000));
    }

    @Test
    void adminStatusUpdateMarksPaymentFailed() throws Exception {
        when(paymentService.updateStatusAsAdmin(eq(2000L), any()))
            .thenReturn(paymentResponse(PaymentStatus.FAILED));

        mockMvc.perform(patch("/api/admin/payments/2000/status")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "FAILED",
                      "failureReason": "Processor declined"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void adminStatusUpdateMarksPaymentSuccess() throws Exception {
        when(paymentService.updateStatusAsAdmin(eq(2000L), any()))
            .thenReturn(paymentResponse(PaymentStatus.SUCCESS));

        mockMvc.perform(patch("/api/admin/payments/2000/status")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "SUCCESS"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void userRoleReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isForbidden());
    }

    @Test
    void validationRejectsPendingStatus() throws Exception {
        mockMvc.perform(patch("/api/admin/payments/2000/status")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "PENDING"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void terminalUpdateMapsToConflict() throws Exception {
        when(paymentService.updateStatusAsAdmin(eq(2000L), any()))
            .thenThrow(new InvalidPaymentOperationException("Terminal payment cannot be changed"));

        mockMvc.perform(patch("/api/admin/payments/2000/status")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "FAILED",
                      "failureReason": "Second update"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Terminal payment cannot be changed"));
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
            status == PaymentStatus.FAILED ? "Processor declined" : null,
            now,
            now
        );
    }
}
