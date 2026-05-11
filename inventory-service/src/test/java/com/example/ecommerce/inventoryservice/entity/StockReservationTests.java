package com.example.ecommerce.inventoryservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StockReservationTests {

    @Test
    void reservedFactoryCreatesReservedReservation() {
        StockReservation reservation = StockReservation.reserved(1001L, 10L, 2);

        assertThat(reservation.getOrderId()).isEqualTo(1001L);
        assertThat(reservation.getProductId()).isEqualTo(10L);
        assertThat(reservation.getQuantity()).isEqualTo(2);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.getFailureReason()).isNull();
    }

    @Test
    void failedFactoryCreatesFailedReservationWithReason() {
        StockReservation reservation = StockReservation.failed(1001L, 10L, 2, "Insufficient stock");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(reservation.getFailureReason()).isEqualTo("Insufficient stock");
    }

    @Test
    void releaseChangesReservedReservationToReleased() {
        StockReservation reservation = StockReservation.reserved(1001L, 10L, 2);

        reservation.release();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void deductChangesReservedReservationToDeducted() {
        StockReservation reservation = StockReservation.reserved(1001L, 10L, 2);

        reservation.deduct();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.DEDUCTED);
    }

    @Test
    void releaseRejectsNonReservedReservation() {
        StockReservation reservation = StockReservation.failed(1001L, 10L, 2, "Insufficient stock");

        assertThatThrownBy(reservation::release)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only reserved stock can be released");
    }

    @Test
    void deductRejectsNonReservedReservation() {
        StockReservation reservation = StockReservation.failed(1001L, 10L, 2, "Insufficient stock");

        assertThatThrownBy(reservation::deduct)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Only reserved stock can be deducted");
    }
}
