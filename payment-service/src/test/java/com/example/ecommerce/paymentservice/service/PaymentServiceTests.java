package com.example.ecommerce.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ecommerce.paymentservice.config.GatewayUser;
import com.example.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.example.ecommerce.paymentservice.dto.PaymentResponse;
import com.example.ecommerce.paymentservice.dto.SimulatePaymentResult;
import com.example.ecommerce.paymentservice.dto.UpdatePaymentStatusRequest;
import com.example.ecommerce.paymentservice.entity.Payment;
import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import com.example.ecommerce.paymentservice.exception.DuplicateOrderPaymentException;
import com.example.ecommerce.paymentservice.exception.InvalidPaymentOperationException;
import com.example.ecommerce.paymentservice.exception.PaymentNotFoundException;
import com.example.ecommerce.paymentservice.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

    private static final Long USER_ID = 10L;
    private static final Long PAYMENT_ID = 5000L;
    private static final Long ORDER_ID = 1000L;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createPaymentAppliesSuccessSimulation() {
        CreatePaymentRequest request = request(SimulatePaymentResult.SUCCESS);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> assignId(invocation.getArgument(0), PAYMENT_ID));

        PaymentResponse response = paymentService.create(user(), request);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.userId()).isEqualTo(USER_ID);
    }

    @Test
    void createPaymentAppliesFailedSimulation() {
        CreatePaymentRequest request = request(SimulatePaymentResult.FAILED);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> assignId(invocation.getArgument(0), PAYMENT_ID));

        PaymentResponse response = paymentService.create(user(), request);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Payment failed");
    }

    @Test
    void createPaymentDefaultsToPendingWhenSimulationOmitted() {
        CreatePaymentRequest request = new CreatePaymentRequest(
            ORDER_ID,
            new BigDecimal("99.98"),
            PaymentMethod.CARD,
            null
        );
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> assignId(invocation.getArgument(0), PAYMENT_ID));

        PaymentResponse response = paymentService.create(user(), request);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createPaymentKeepsPendingWhenSimulationPending() {
        CreatePaymentRequest request = new CreatePaymentRequest(
            ORDER_ID,
            new BigDecimal("99.98"),
            PaymentMethod.CARD,
            SimulatePaymentResult.PENDING
        );
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> assignId(invocation.getArgument(0), PAYMENT_ID));

        PaymentResponse response = paymentService.create(user(), request);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createPaymentReturnsExistingPaymentForSameUserAndOrder() {
        Payment existing = assignId(payment(ORDER_ID, USER_ID), PAYMENT_ID);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.create(user(), request(SimulatePaymentResult.SUCCESS));

        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPaymentRejectsExistingPaymentForDifferentUser() {
        Payment existing = assignId(payment(ORDER_ID, 11L), PAYMENT_ID);
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.create(user(), request(SimulatePaymentResult.SUCCESS)))
            .isInstanceOf(DuplicateOrderPaymentException.class);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPaymentReturnsExistingPaymentWhenConcurrentSameUserInsertWins() {
        Payment existing = assignId(payment(ORDER_ID, USER_ID), PAYMENT_ID);
        when(paymentRepository.findByOrderId(ORDER_ID))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate order payment"));

        PaymentResponse response = paymentService.create(user(), request(SimulatePaymentResult.SUCCESS));

        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    void createPaymentRejectsConcurrentDifferentUserInsert() {
        Payment existing = assignId(payment(ORDER_ID, 11L), PAYMENT_ID);
        when(paymentRepository.findByOrderId(ORDER_ID))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate order payment"));

        assertThatThrownBy(() -> paymentService.create(user(), request(SimulatePaymentResult.SUCCESS)))
            .isInstanceOf(DuplicateOrderPaymentException.class);
    }

    @Test
    void createPaymentRethrowsIntegrityViolationWhenRaceWinnerCannotBeFound() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicate order payment");
        when(paymentRepository.findByOrderId(ORDER_ID))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenThrow(violation);

        assertThatThrownBy(() -> paymentService.create(user(), request(SimulatePaymentResult.SUCCESS)))
            .isSameAs(violation);
    }

    @Test
    void currentUserPaymentDetailHidesAnotherUsersPayment() {
        when(paymentRepository.findByIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findCurrentUserPayment(USER_ID, PAYMENT_ID))
            .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void findCurrentUserPaymentsUsesUserScopedRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(paymentRepository.findByUserId(USER_ID, pageable)).thenReturn(new PageImpl<>(List.of()));

        paymentService.findCurrentUserPayments(USER_ID, pageable);

        verify(paymentRepository).findByUserId(USER_ID, pageable);
    }

    @Test
    void findCurrentUserPaymentByOrderUsesUserScopedRepository() {
        Payment existing = assignId(payment(ORDER_ID, USER_ID), PAYMENT_ID);
        when(paymentRepository.findByOrderIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.findCurrentUserPaymentByOrder(USER_ID, ORDER_ID);

        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        verify(paymentRepository).findByOrderIdAndUserId(ORDER_ID, USER_ID);
    }

    @Test
    void findCurrentUserPaymentByOrderThrowsWhenMissing() {
        when(paymentRepository.findByOrderIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findCurrentUserPaymentByOrder(USER_ID, ORDER_ID))
            .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void findAdminPaymentsUsesStatusFilterWhenPresent() {
        Pageable pageable = PageRequest.of(0, 20);
        when(paymentRepository.findByStatus(PaymentStatus.SUCCESS, pageable)).thenReturn(new PageImpl<>(List.of()));

        paymentService.findAdminPayments(PaymentStatus.SUCCESS, pageable);

        verify(paymentRepository).findByStatus(PaymentStatus.SUCCESS, pageable);
    }

    @Test
    void findAdminPaymentsUsesFindAllWhenStatusMissing() {
        Pageable pageable = PageRequest.of(0, 20);
        when(paymentRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        paymentService.findAdminPayments(null, pageable);

        verify(paymentRepository).findAll(pageable);
    }

    @Test
    void adminCanMarkPendingPaymentSuccess() {
        Payment existing = assignId(payment(ORDER_ID, USER_ID), PAYMENT_ID);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(existing)).thenReturn(existing);

        PaymentResponse response = paymentService.updateStatusAsAdmin(
            PAYMENT_ID,
            new UpdatePaymentStatusRequest(PaymentStatus.SUCCESS, null)
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void adminCanMarkPendingPaymentFailed() {
        Payment existing = assignId(payment(ORDER_ID, USER_ID), PAYMENT_ID);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(existing)).thenReturn(existing);

        PaymentResponse response = paymentService.updateStatusAsAdmin(
            PAYMENT_ID,
            new UpdatePaymentStatusRequest(PaymentStatus.FAILED, "Declined")
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Declined");
    }

    @Test
    void adminStatusUpdateThrowsWhenPaymentMissing() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.updateStatusAsAdmin(
            PAYMENT_ID,
            new UpdatePaymentStatusRequest(PaymentStatus.SUCCESS, null)
        ))
            .isInstanceOf(PaymentNotFoundException.class);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void adminStatusUpdateRejectsTerminalPayment() {
        Payment existing = assignId(payment(ORDER_ID, USER_ID), PAYMENT_ID);
        existing.markSuccess();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.updateStatusAsAdmin(
            PAYMENT_ID,
            new UpdatePaymentStatusRequest(PaymentStatus.FAILED, "Declined")
        ))
            .isInstanceOf(InvalidPaymentOperationException.class)
            .hasMessage("Terminal payment cannot be changed");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void adminStatusUpdateRejectsPendingTargetStatus() {
        Payment existing = assignId(payment(ORDER_ID, USER_ID), PAYMENT_ID);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.updateStatusAsAdmin(
            PAYMENT_ID,
            new UpdatePaymentStatusRequest(PaymentStatus.PENDING, null)
        ))
            .isInstanceOf(InvalidPaymentOperationException.class)
            .hasMessage("Only SUCCESS or FAILED is supported");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void findAdminPaymentThrowsWhenMissing() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findAdminPayment(PAYMENT_ID))
            .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void responseTimestampsTreatEntityTimestampsAsUtc() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            Payment existing = assignId(payment(ORDER_ID, USER_ID), PAYMENT_ID);
            ReflectionTestUtils.setField(existing, "createdAt", LocalDateTime.parse("2026-05-13T12:00:00"));
            ReflectionTestUtils.setField(existing, "updatedAt", LocalDateTime.parse("2026-05-13T12:05:00"));
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(existing));

            PaymentResponse response = paymentService.findAdminPayment(PAYMENT_ID);

            assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-13T12:00:00Z"));
            assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-05-13T12:05:00Z"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private GatewayUser user() {
        return new GatewayUser(USER_ID, "user@example.com", List.of("USER"));
    }

    private static CreatePaymentRequest request(SimulatePaymentResult result) {
        return new CreatePaymentRequest(ORDER_ID, new BigDecimal("99.98"), PaymentMethod.CARD, result);
    }

    private static Payment payment(Long orderId, Long userId) {
        return Payment.create(orderId, userId, new BigDecimal("99.98"), PaymentMethod.CARD);
    }

    private static Payment assignId(Payment payment, Long id) {
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }
}
