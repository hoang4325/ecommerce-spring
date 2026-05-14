package com.example.ecommerce.paymentservice.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void methodArgumentTypeMismatchMapsToBadRequest() throws Exception {
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException(
            "abc",
            Long.class,
            "id",
            null,
            new NumberFormatException("For input string: abc")
        );

        ResponseEntity<ApiErrorResponse> response = invokeResolvedHandler(mismatch, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid request");
        assertThat(response.getBody().path()).isEqualTo("/api/admin/payments");
    }

    @Test
    void missingServletRequestParameterMapsToBadRequest() throws Exception {
        MissingServletRequestParameterException missing =
            new MissingServletRequestParameterException("status", "PaymentStatus");

        ResponseEntity<ApiErrorResponse> response = invokeResolvedHandler(missing, request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid request");
        assertThat(response.getBody().path()).isEqualTo("/api/admin/payments");
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ApiErrorResponse> invokeResolvedHandler(Exception ex, HttpServletRequest request)
        throws Exception {
        Method method = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class).resolveMethod(ex);
        assertThat(method).isNotNull();
        method.setAccessible(true);
        return (ResponseEntity<ApiErrorResponse>) method.invoke(handler, ex, request);
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/admin/payments");
        when(request.getMethod()).thenReturn("GET");
        return request;
    }
}
