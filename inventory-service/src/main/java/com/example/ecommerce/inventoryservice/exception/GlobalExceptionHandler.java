package com.example.ecommerce.inventoryservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_FAILED_MESSAGE = "Validation failed";
    private static final String UNEXPECTED_SERVER_ERROR_MESSAGE = "Unexpected server error";
    private static final String MALFORMED_REQUEST_BODY_MESSAGE = "Malformed or missing request body";
    private static final String UNSUPPORTED_CONTENT_TYPE_MESSAGE = "Content type is not supported";
    private static final String UNSUPPORTED_METHOD_MESSAGE = "HTTP method is not supported";
    private static final String INVALID_REQUEST_MESSAGE = "Invalid request";
    private static final String ACCESS_DENIED_MESSAGE = "Access is denied";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        List<ApiErrorResponse.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new ApiErrorResponse.FieldErrorDetail(error.getField(), error.getDefaultMessage()))
            .sorted(Comparator
                .comparing(
                    ApiErrorResponse.FieldErrorDetail::field,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    ApiErrorResponse.FieldErrorDetail::message,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, VALIDATION_FAILED_MESSAGE, request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, MALFORMED_REQUEST_BODY_MESSAGE, request, List.of());
    }

    @ExceptionHandler({
        MissingPathVariableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleBadRequestException(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, INVALID_REQUEST_MESSAGE, request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleHttpMediaTypeNotSupportedException(
        HttpMediaTypeNotSupportedException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_CONTENT_TYPE_MESSAGE, request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleHttpRequestMethodNotSupportedException(
        HttpRequestMethodNotSupportedException ex,
        HttpServletRequest request
    ) {
        ApiErrorResponse response = buildErrorResponse(
            HttpStatus.METHOD_NOT_ALLOWED,
            UNSUPPORTED_METHOD_MESSAGE,
            request,
            List.of()
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supportedMethods = ex.getSupportedHttpMethods();
        if (supportedMethods != null && !supportedMethods.isEmpty()) {
            builder.allow(supportedMethods.toArray(HttpMethod[]::new));
        }

        return builder.body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
        ResourceNotFoundException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler({DuplicateReservationException.class, InvalidStockOperationException.class})
    ResponseEntity<ApiErrorResponse> handleConflictException(RuntimeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ACCESS_DENIED_MESSAGE, request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unexpected exception handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_SERVER_ERROR_MESSAGE, request, List.of());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
        HttpStatus status,
        String message,
        HttpServletRequest request,
        List<ApiErrorResponse.FieldErrorDetail> details
    ) {
        return ResponseEntity.status(status).body(buildErrorResponse(status, message, request, details));
    }

    private ApiErrorResponse buildErrorResponse(
        HttpStatus status,
        String message,
        HttpServletRequest request,
        List<ApiErrorResponse.FieldErrorDetail> details
    ) {
        return new ApiErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI(),
            details
        );
    }
}
