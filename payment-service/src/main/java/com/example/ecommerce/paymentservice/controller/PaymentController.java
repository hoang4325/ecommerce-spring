package com.example.ecommerce.paymentservice.controller;

import com.example.ecommerce.paymentservice.config.GatewayUser;
import com.example.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.example.ecommerce.paymentservice.dto.PaymentResponse;
import com.example.ecommerce.paymentservice.exception.MissingUserIdentityException;
import com.example.ecommerce.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
        Authentication authentication,
        @Valid @RequestBody CreatePaymentRequest request
    ) {
        return paymentService.create(currentUser(authentication), request);
    }

    @GetMapping
    public Page<PaymentResponse> listPayments(
        Authentication authentication,
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return paymentService.findCurrentUserPayments(currentUser(authentication).id(), pageable);
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(Authentication authentication, @PathVariable Long id) {
        return paymentService.findCurrentUserPayment(currentUser(authentication).id(), id);
    }

    @GetMapping("/by-order/{orderId}")
    public PaymentResponse getPaymentByOrder(Authentication authentication, @PathVariable Long orderId) {
        return paymentService.findCurrentUserPaymentByOrder(currentUser(authentication).id(), orderId);
    }

    private static GatewayUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof GatewayUser user)) {
            throw new MissingUserIdentityException();
        }
        return user;
    }
}
