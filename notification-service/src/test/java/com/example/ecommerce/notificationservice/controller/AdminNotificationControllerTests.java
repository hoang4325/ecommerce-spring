package com.example.ecommerce.notificationservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.notificationservice.dto.NotificationResponse;
import com.example.ecommerce.notificationservice.dto.NotificationSearchCriteria;
import com.example.ecommerce.notificationservice.entity.NotificationChannel;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import com.example.ecommerce.notificationservice.exception.NotificationNotFoundException;
import com.example.ecommerce.notificationservice.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:notification_admin_controller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "notification.internal-token=test-internal-token"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminNotificationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void adminListDelegatesAllFilters() throws Exception {
        when(notificationService.findAdminNotifications(any(), any()))
            .thenReturn(new PageImpl<>(List.of(response(NotificationStatus.SENT)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/notifications?type=PAYMENT_SUCCEEDED&status=SENT&userId=10&orderId=1000&paymentId=5000")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].notificationId").value(9000));

        ArgumentCaptor<NotificationSearchCriteria> criteriaCaptor =
            ArgumentCaptor.forClass(NotificationSearchCriteria.class);
        verify(notificationService).findAdminNotifications(criteriaCaptor.capture(), any());
        assertThat(criteriaCaptor.getValue()).isEqualTo(new NotificationSearchCriteria(
            NotificationType.PAYMENT_SUCCEEDED,
            NotificationStatus.SENT,
            10L,
            1000L,
            5000L
        ));
    }

    @Test
    void adminListDefaultsToNewestFirst() throws Exception {
        when(notificationService.findAdminNotifications(any(), any()))
            .thenReturn(new PageImpl<>(List.of(response(NotificationStatus.SENT))));

        mockMvc.perform(get("/api/admin/notifications")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).findAdminNotifications(any(), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void adminDetailReturnsNotification() throws Exception {
        when(notificationService.findAdminNotification(9000L)).thenReturn(response(NotificationStatus.SENT));

        mockMvc.perform(get("/api/admin/notifications/9000")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationId").value(9000));
    }

    @Test
    void missingGatewayUserReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/notifications"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing user identity"));
    }

    @Test
    void userRoleReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")
                .header("X-User-Id", "10")
                .header("X-User-Roles", "USER"))
            .andExpect(status().isForbidden());
    }

    @Test
    void missingNotificationMapsToNotFound() throws Exception {
        when(notificationService.findAdminNotification(9999L)).thenThrow(new NotificationNotFoundException(9999L));

        mockMvc.perform(get("/api/admin/notifications/9999")
                .header("X-User-Id", "1")
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Notification not found: 9999"));
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
            null,
            Instant.parse("2026-05-14T00:00:00Z")
        );
    }
}
