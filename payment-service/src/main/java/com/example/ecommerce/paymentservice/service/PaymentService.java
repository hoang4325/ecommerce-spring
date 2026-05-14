package com.example.ecommerce.paymentservice.service;

import com.example.ecommerce.paymentservice.config.GatewayUser;
import com.example.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.example.ecommerce.paymentservice.dto.PaymentResponse;
import com.example.ecommerce.paymentservice.dto.SimulatePaymentResult;
import com.example.ecommerce.paymentservice.dto.UpdatePaymentStatusRequest;
import com.example.ecommerce.paymentservice.entity.Payment;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import com.example.ecommerce.paymentservice.exception.DuplicateOrderPaymentException;
import com.example.ecommerce.paymentservice.exception.InvalidPaymentOperationException;
import com.example.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.example.ecommerce.paymentservice.repository.PaymentRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentResponse create(GatewayUser user, CreatePaymentRequest request) {
        Optional<Payment> existing = paymentRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (!payment.getUserId().equals(user.id())) {
                throw new DuplicateOrderPaymentException();
            }
            return toResponse(payment);
        }

        Payment payment = Payment.create(request.orderId(), user.id(), request.amount(), request.method());
        SimulatePaymentResult result = request.simulateResult() == null
            ? SimulatePaymentResult.PENDING
            : request.simulateResult();
        if (result == SimulatePaymentResult.SUCCESS) {
            payment.markSuccess();
        } else if (result == SimulatePaymentResult.FAILED) {
            payment.markFailed("Payment failed");
        }
        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> findCurrentUserPayments(Long userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findCurrentUserPayment(Long userId, Long paymentId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
            .map(this::toResponse)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findCurrentUserPaymentByOrder(Long userId, Long orderId) {
        return paymentRepository.findByOrderIdAndUserId(orderId, userId)
            .map(this::toResponse)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found for order: " + orderId));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> findAdminPayments(PaymentStatus status, Pageable pageable) {
        Page<Payment> payments = status == null
            ? paymentRepository.findAll(pageable)
            : paymentRepository.findByStatus(status, pageable);
        return payments.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findAdminPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .map(this::toResponse)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Transactional
    public PaymentResponse updateStatusAsAdmin(Long paymentId, UpdatePaymentStatusRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.isTerminal()) {
            throw new InvalidPaymentOperationException("Terminal payment cannot be changed");
        }
        if (request.status() == PaymentStatus.SUCCESS) {
            payment.markSuccess();
        } else if (request.status() == PaymentStatus.FAILED) {
            payment.markFailed(request.failureReason());
        } else {
            throw new InvalidPaymentOperationException("Only SUCCESS or FAILED is supported");
        }
        return toResponse(paymentRepository.save(payment));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getMethod(),
            payment.getStatus(),
            payment.getFailureReason(),
            toInstant(payment.getCreatedAt()),
            toInstant(payment.getUpdatedAt())
        );
    }

    private static Instant toInstant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }
}
