package com.example.ecommerce.paymentservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentResponseTests {

    @Test
    void paymentResponseCarriesFields() {
        PaymentResponse response = new PaymentResponse(
            5000L,
            1000L,
            10L,
            new BigDecimal("99.98"),
            PaymentMethod.CARD,
            PaymentStatus.SUCCESS,
            null,
            Instant.parse("2026-05-13T00:00:00Z"),
            Instant.parse("2026-05-13T00:00:01Z")
        );

        assertThat(response.paymentId()).isEqualTo(5000L);
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
    }
}
