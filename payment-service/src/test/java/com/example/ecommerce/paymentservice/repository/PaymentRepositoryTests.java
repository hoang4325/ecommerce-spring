package com.example.ecommerce.paymentservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.paymentservice.entity.Payment;
import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:payment_repository;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentRepositoryTests {

    @Autowired
    private PaymentRepository repository;

    @Test
    void persistsPayment() {
        Payment payment = repository.saveAndFlush(samplePayment(1000L, 10L));

        assertThat(payment.getId()).isNotNull();
        assertThat(repository.findById(payment.getId())).isPresent();
    }

    @Test
    void findsByIdAndUserId() {
        Payment payment = repository.saveAndFlush(samplePayment(1000L, 10L));

        assertThat(repository.findByIdAndUserId(payment.getId(), 10L)).isPresent();
        assertThat(repository.findByIdAndUserId(payment.getId(), 11L)).isEmpty();
    }

    @Test
    void findsByOrderIdAndUserId() {
        repository.saveAndFlush(samplePayment(1000L, 10L));

        assertThat(repository.findByOrderId(1000L)).isPresent();
        assertThat(repository.findByOrderIdAndUserId(1000L, 10L)).isPresent();
        assertThat(repository.findByOrderIdAndUserId(1000L, 11L)).isEmpty();
    }

    @Test
    void findsByUserId() {
        repository.saveAndFlush(samplePayment(1000L, 10L));
        repository.saveAndFlush(samplePayment(1001L, 10L));
        repository.saveAndFlush(samplePayment(1002L, 11L));

        Page<Payment> result = repository.findByUserId(
            10L,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(result.getContent()).extracting(Payment::getUserId).containsOnly(10L);
    }

    @Test
    void filtersByStatus() {
        Payment pending = samplePayment(1000L, 10L);
        Payment success = samplePayment(1001L, 11L);
        success.markSuccess();
        repository.saveAndFlush(pending);
        repository.saveAndFlush(success);

        Page<Payment> result = repository.findByStatus(
            PaymentStatus.SUCCESS,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(result.getContent()).extracting(Payment::getStatus).containsOnly(PaymentStatus.SUCCESS);
    }

    @Test
    void enforcesUniqueOrderPayment() {
        repository.saveAndFlush(samplePayment(1000L, 10L));

        assertThatThrownBy(() -> repository.saveAndFlush(samplePayment(1000L, 10L)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Payment samplePayment(Long orderId, Long userId) {
        return Payment.create(orderId, userId, new BigDecimal("99.98"), PaymentMethod.CARD);
    }
}
