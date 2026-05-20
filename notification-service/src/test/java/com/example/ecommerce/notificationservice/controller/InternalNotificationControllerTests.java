package com.example.ecommerce.notificationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.notificationservice.dto.CreateNotificationRequest;
import com.example.ecommerce.notificationservice.dto.NotificationResponse;
import com.example.ecommerce.notificationservice.entity.NotificationChannel;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import com.example.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
        "spring.datasource.url=jdbc:h2:mem:notification_internal_controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "notification.internal-token=test-internal-token"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalNotificationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void validInternalTokenAndRequestReturnsCreatedNotification() throws Exception {
        CreateNotificationRequest request = validRequest(false);
        when(notificationService.create(any())).thenReturn(response(NotificationStatus.SENT));

        mockMvc.perform(post("/api/internal/notifications")
                .header("X-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.notificationId").value(9000))
            .andExpect(jsonPath("$.status").value("SENT"));

        ArgumentCaptor<CreateNotificationRequest> requestCaptor =
            ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationService).create(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue()).isEqualTo(request);
    }

    @Test
    void missingInternalTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest(false))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInternalTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/internal/notifications")
                .header("X-Internal-Token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest(false))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validationErrorReturnsBadRequest() throws Exception {
        String request = """
            {
              "type": null,
              "recipient": "not-an-email",
              "subject": "",
              "message": ""
            }
            """;

        mockMvc.perform(post("/api/internal/notifications")
                .header("X-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/internal/notifications")
                .header("X-Internal-Token", "test-internal-token")
                .contentType(MediaType.TEXT_PLAIN)
                .content("not json"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/api/internal/notifications")
                .header("X-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest(false))))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void safeUnsupportedMethodStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/internal/notifications"))
            .andExpect(status().isUnauthorized());
    }

    private static CreateNotificationRequest validRequest(boolean simulateFailure) {
        return new CreateNotificationRequest(
            NotificationType.PAYMENT_SUCCEEDED,
            "customer@example.com",
            "Payment received",
            "Your payment was successful.",
            10L,
            1000L,
            5000L,
            simulateFailure
        );
    }

    private static NotificationResponse response(NotificationStatus status) {
        return new NotificationResponse(
            9000L,
            10L,
            1000L,
            5000L,
            NotificationType.PAYMENT_SUCCEEDED,
            NotificationChannel.EMAIL,
            "customer@example.com",
            status,
            "Payment received",
            "Your payment was successful.",
            status == NotificationStatus.FAILED ? "Simulated notification failure" : null,
            Instant.parse("2026-05-14T00:00:00Z")
        );
    }
}
