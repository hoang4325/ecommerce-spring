package com.example.ecommerce.authservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_FAILED_MESSAGE = "Validation failed";
    private static final String UNEXPECTED_SERVER_ERROR_MESSAGE = "Unexpected server error";
    private static final String MALFORMED_REQUEST_BODY_MESSAGE = "Malformed or missing request body";
    private static final String UNSUPPORTED_CONTENT_TYPE_MESSAGE = "Content type is not supported";
    private static final String UNSUPPORTED_METHOD_MESSAGE = "HTTP method is not supported";

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
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, UNSUPPORTED_METHOD_MESSAGE, request, List.of());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateEmailException(
        DuplicateEmailException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentialsException(
        InvalidCredentialsException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, List.of());
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
        ApiErrorResponse response = new ApiErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI(),
            details
        );

        return ResponseEntity.status(status).body(response);
    }
}
