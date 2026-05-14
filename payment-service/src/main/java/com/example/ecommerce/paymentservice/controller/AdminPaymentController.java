package com.example.ecommerce.paymentservice.controller;

import com.example.ecommerce.paymentservice.dto.PaymentResponse;
import com.example.ecommerce.paymentservice.dto.UpdatePaymentStatusRequest;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import com.example.ecommerce.paymentservice.exception.InvalidPaymentOperationException;
import com.example.ecommerce.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public Page<PaymentResponse> listPayments(
        @RequestParam(required = false) PaymentStatus status,
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return paymentService.findAdminPayments(status, pageable);
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return paymentService.findAdminPayment(id);
    }

    @PatchMapping("/{id}/status")
    public PaymentResponse updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdatePaymentStatusRequest request
    ) {
        if (request.status() != PaymentStatus.SUCCESS && request.status() != PaymentStatus.FAILED) {
            throw new InvalidPaymentOperationException("Only SUCCESS or FAILED is supported");
        }
        return paymentService.updateStatusAsAdmin(id, request);
    }
}
