package com.example.ecommerce.notificationservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsMissingUserIdentityToUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/notifications");

        ResponseEntity<ApiErrorResponse> response =
            handler.handleMissingUserIdentityException(new MissingUserIdentityException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Missing user identity");
        assertThat(response.getBody().path()).isEqualTo("/api/admin/notifications");
    }

    @Test
    void mapsNotificationNotFoundToNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/notifications/99");

        ResponseEntity<ApiErrorResponse> response =
            handler.handleNotificationNotFoundException(new NotificationNotFoundException(99L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Notification not found: 99");
        assertThat(response.getBody().path()).isEqualTo("/api/admin/notifications/99");
    }
}
