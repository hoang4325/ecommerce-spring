package com.example.ecommerce.paymentservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentTests {

    @Test
    void createPendingPayment() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);

        assertThat(payment.getOrderId()).isEqualTo(1000L);
        assertThat(payment.getUserId()).isEqualTo(10L);
        assertThat(payment.getAmount()).isEqualByComparingTo("99.98");
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void failedPaymentCannotBeMarkedSuccess() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);
        payment.markFailed("Card declined");

        assertThatThrownBy(payment::markSuccess)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Terminal payment cannot be changed");
    }

    @Test
    void pendingPaymentCanBeMarkedSuccess() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);

        payment.markSuccess();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void pendingPaymentCanBeMarkedFailed() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);

        payment.markFailed("Simulated card decline");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Simulated card decline");
    }

    @Test
    void createRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Payment.create(1000L, 10L, BigDecimal.ZERO, PaymentMethod.CARD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Amount must be greater than zero");
    }

    @Test
    void createRejectsRequiredFields() {
        assertThatThrownBy(() -> Payment.create(null, 10L, new BigDecimal("99.98"), PaymentMethod.CARD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Order id is required");

        assertThatThrownBy(() -> Payment.create(1000L, null, new BigDecimal("99.98"), PaymentMethod.CARD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("User id is required");

        assertThatThrownBy(() -> Payment.create(1000L, 10L, new BigDecimal("99.98"), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Payment method is required");
    }

    @Test
    void terminalPaymentCannotBeChanged() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);
        payment.markSuccess();

        assertThatThrownBy(() -> payment.markFailed("Late failure"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Terminal payment cannot be changed");
    }
}
