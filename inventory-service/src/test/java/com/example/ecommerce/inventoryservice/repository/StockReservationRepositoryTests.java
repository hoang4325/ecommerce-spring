package com.example.ecommerce.inventoryservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.inventoryservice.entity.ReservationStatus;
import com.example.ecommerce.inventoryservice.entity.StockReservation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:inventory_service_reservation_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockReservationRepositoryTests {

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Test
    void lookupByOrderReturnsReservationsOrderedByProductId() {
        stockReservationRepository.save(StockReservation.reserved(1001L, 12L, 1));
        stockReservationRepository.save(StockReservation.reserved(1001L, 10L, 2));
        stockReservationRepository.save(StockReservation.reserved(1002L, 11L, 3));
        stockReservationRepository.flush();

        assertThat(stockReservationRepository.findAllByOrderIdOrderByProductIdAsc(1001L))
            .extracting(StockReservation::getProductId)
            .containsExactly(10L, 12L);
    }

    @Test
    void lookupByOrderAndStatusReturnsMatchingReservations() {
        StockReservation released = StockReservation.reserved(1001L, 10L, 2);
        released.release();
        stockReservationRepository.save(released);
        stockReservationRepository.save(StockReservation.reserved(1001L, 12L, 1));
        stockReservationRepository.flush();

        assertThat(stockReservationRepository.findAllByOrderIdAndStatusOrderByProductIdAsc(
            1001L,
            ReservationStatus.RESERVED
        ))
            .extracting(StockReservation::getProductId)
            .containsExactly(12L);
    }

    @Test
    void existsByOrderIdAndStatusInFindsActiveReservations() {
        stockReservationRepository.save(StockReservation.reserved(1001L, 10L, 2));
        stockReservationRepository.flush();

        assertThat(stockReservationRepository.existsByOrderIdAndStatusIn(
            1001L,
            List.of(ReservationStatus.RESERVED, ReservationStatus.DEDUCTED)
        )).isTrue();
        assertThat(stockReservationRepository.existsByOrderIdAndStatusIn(
            1002L,
            List.of(ReservationStatus.RESERVED, ReservationStatus.DEDUCTED)
        )).isFalse();
    }

    @Test
    void failureReasonAllowsFiveHundredCharacters() {
        String reason = "x".repeat(500);

        StockReservation saved = stockReservationRepository.saveAndFlush(
            StockReservation.failed(1001L, 10L, 2, reason)
        );

        assertThat(saved.getFailureReason()).hasSize(500);
    }
}
