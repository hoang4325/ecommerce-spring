package com.example.ecommerce.inventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ecommerce.inventoryservice.dto.ReservationItemRequest;
import com.example.ecommerce.inventoryservice.dto.ReserveStockRequest;
import com.example.ecommerce.inventoryservice.dto.StockReservationResultResponse;
import com.example.ecommerce.inventoryservice.entity.InventoryItem;
import com.example.ecommerce.inventoryservice.entity.ReservationStatus;
import com.example.ecommerce.inventoryservice.entity.StockReservation;
import com.example.ecommerce.inventoryservice.exception.DuplicateReservationException;
import com.example.ecommerce.inventoryservice.exception.InvalidStockOperationException;
import com.example.ecommerce.inventoryservice.repository.InventoryItemRepository;
import com.example.ecommerce.inventoryservice.repository.StockReservationRepository;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTests {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    @InjectMocks
    private StockReservationService stockReservationService;

    @Test
    void reserveSucceedsAndMovesAvailableToReservedQuantity() {
        InventoryItem product10 = InventoryItem.create(10L, 5);
        InventoryItem product11 = InventoryItem.create(11L, 3);
        when(stockReservationRepository.existsByOrderIdAndStatusIn(1001L, activeStatuses())).thenReturn(false);
        when(inventoryItemRepository.findAllByProductIdInForUpdate(List.of(10L, 11L)))
            .thenReturn(List.of(product10, product11));
        when(stockReservationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StockReservationResultResponse response = stockReservationService.reserve(new ReserveStockRequest(
            1001L,
            List.of(new ReservationItemRequest(10L, 2), new ReservationItemRequest(11L, 1))
        ));

        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(response.reservations())
            .extracting(reservation -> reservation.productId())
            .containsExactly(10L, 11L);
        assertThat(product10.getAvailableQuantity()).isEqualTo(3);
        assertThat(product10.getReservedQuantity()).isEqualTo(2);
        assertThat(product11.getAvailableQuantity()).isEqualTo(2);
        assertThat(product11.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void reserveRejectsDuplicateProductIdsInRequest() {
        ReserveStockRequest request = new ReserveStockRequest(
            1001L,
            List.of(new ReservationItemRequest(10L, 1), new ReservationItemRequest(10L, 2))
        );

        assertThatThrownBy(() -> stockReservationService.reserve(request))
            .isInstanceOf(InvalidStockOperationException.class)
            .hasMessage("Duplicate product in reservation request");
        verify(stockReservationRepository, never()).saveAll(any());
    }

    @Test
    void reserveFailsWithoutStockMovementWhenItemIsMissing() {
        InventoryItem product10 = InventoryItem.create(10L, 5);
        when(stockReservationRepository.existsByOrderIdAndStatusIn(1001L, activeStatuses())).thenReturn(false);
        when(inventoryItemRepository.findAllByProductIdInForUpdate(List.of(10L, 11L))).thenReturn(List.of(product10));
        when(stockReservationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StockReservationResultResponse response = stockReservationService.reserve(new ReserveStockRequest(
            1001L,
            List.of(new ReservationItemRequest(10L, 2), new ReservationItemRequest(11L, 1))
        ));

        assertThat(response.status()).isEqualTo(ReservationStatus.FAILED);
        assertThat(response.reservations())
            .extracting(reservation -> reservation.status())
            .containsOnly(ReservationStatus.FAILED);
        assertThat(product10.getAvailableQuantity()).isEqualTo(5);
        assertThat(product10.getReservedQuantity()).isZero();
    }

    @Test
    void reserveFailsWithoutStockMovementWhenQuantityIsInsufficient() {
        InventoryItem product10 = InventoryItem.create(10L, 1);
        when(stockReservationRepository.existsByOrderIdAndStatusIn(1001L, activeStatuses())).thenReturn(false);
        when(inventoryItemRepository.findAllByProductIdInForUpdate(List.of(10L))).thenReturn(List.of(product10));
        when(stockReservationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StockReservationResultResponse response = stockReservationService.reserve(new ReserveStockRequest(
            1001L,
            List.of(new ReservationItemRequest(10L, 2))
        ));

        assertThat(response.status()).isEqualTo(ReservationStatus.FAILED);
        assertThat(response.reservations().getFirst().failureReason()).isEqualTo("Insufficient stock for product 10");
        assertThat(product10.getAvailableQuantity()).isEqualTo(1);
        assertThat(product10.getReservedQuantity()).isZero();
    }

    @Test
    void reserveRejectsOrderWithActiveReservation() {
        when(stockReservationRepository.existsByOrderIdAndStatusIn(1001L, activeStatuses())).thenReturn(true);

        assertThatThrownBy(() -> stockReservationService.reserve(new ReserveStockRequest(
            1001L,
            List.of(new ReservationItemRequest(10L, 1))
        )))
            .isInstanceOf(DuplicateReservationException.class)
            .hasMessage("Order already has an active reservation");
        verify(inventoryItemRepository, never()).findAllByProductIdInForUpdate(any());
    }

    @Test
    void releaseReturnsStockAndMarksReservationsReleased() {
        InventoryItem item = InventoryItem.create(10L, 5);
        item.reserve(2);
        StockReservation reservation = StockReservation.reserved(1001L, 10L, 2);
        when(stockReservationRepository.findAllByOrderIdOrderByProductIdAsc(1001L)).thenReturn(List.of(reservation));
        when(inventoryItemRepository.findAllByProductIdInForUpdate(List.of(10L))).thenReturn(List.of(item));

        StockReservationResultResponse response = stockReservationService.release(1001L);

        assertThat(response.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(item.getAvailableQuantity()).isEqualTo(5);
        assertThat(item.getReservedQuantity()).isZero();
    }

    @Test
    void releaseRejectsDeductedReservations() {
        StockReservation reservation = StockReservation.reserved(1001L, 10L, 2);
        reservation.deduct();
        when(stockReservationRepository.findAllByOrderIdOrderByProductIdAsc(1001L)).thenReturn(List.of(reservation));

        assertThatThrownBy(() -> stockReservationService.release(1001L))
            .isInstanceOf(InvalidStockOperationException.class)
            .hasMessage("Invalid stock reservation transition");
        verify(inventoryItemRepository, never()).findAllByProductIdInForUpdate(any());
    }

    @Test
    void deductClearsReservedQuantityAndMarksReservationsDeducted() {
        InventoryItem item = InventoryItem.create(10L, 5);
        item.reserve(2);
        StockReservation reservation = StockReservation.reserved(1001L, 10L, 2);
        when(stockReservationRepository.findAllByOrderIdOrderByProductIdAsc(1001L)).thenReturn(List.of(reservation));
        when(inventoryItemRepository.findAllByProductIdInForUpdate(List.of(10L))).thenReturn(List.of(item));

        StockReservationResultResponse response = stockReservationService.deduct(1001L);

        assertThat(response.status()).isEqualTo(ReservationStatus.DEDUCTED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.DEDUCTED);
        assertThat(item.getAvailableQuantity()).isEqualTo(3);
        assertThat(item.getReservedQuantity()).isZero();
    }

    @Test
    void deductRejectsReleasedReservations() {
        StockReservation reservation = StockReservation.reserved(1001L, 10L, 2);
        reservation.release();
        when(stockReservationRepository.findAllByOrderIdOrderByProductIdAsc(1001L)).thenReturn(List.of(reservation));

        assertThatThrownBy(() -> stockReservationService.deduct(1001L))
            .isInstanceOf(InvalidStockOperationException.class)
            .hasMessage("Invalid stock reservation transition");
        verify(inventoryItemRepository, never()).findAllByProductIdInForUpdate(any());
    }

    @Test
    void releaseIsIdempotentForAlreadyReleasedReservations() {
        StockReservation reservation = StockReservation.reserved(1001L, 10L, 2);
        reservation.release();
        when(stockReservationRepository.findAllByOrderIdOrderByProductIdAsc(1001L)).thenReturn(List.of(reservation));

        StockReservationResultResponse response = stockReservationService.release(1001L);

        assertThat(response.status()).isEqualTo(ReservationStatus.RELEASED);
        verify(inventoryItemRepository, never()).findAllByProductIdInForUpdate(any());
    }

    @Test
    void deductIsIdempotentForAlreadyDeductedReservations() {
        StockReservation reservation = StockReservation.reserved(1001L, 10L, 2);
        reservation.deduct();
        when(stockReservationRepository.findAllByOrderIdOrderByProductIdAsc(1001L)).thenReturn(List.of(reservation));

        StockReservationResultResponse response = stockReservationService.deduct(1001L);

        assertThat(response.status()).isEqualTo(ReservationStatus.DEDUCTED);
        verify(inventoryItemRepository, never()).findAllByProductIdInForUpdate(any());
    }

    @Test
    void releaseRejectsMissingReservations() {
        when(stockReservationRepository.findAllByOrderIdOrderByProductIdAsc(1001L)).thenReturn(List.of());

        assertThatThrownBy(() -> stockReservationService.release(1001L))
            .isInstanceOf(InvalidStockOperationException.class)
            .hasMessage("Invalid stock reservation transition");
    }

    private static Collection<ReservationStatus> activeStatuses() {
        return List.of(ReservationStatus.RESERVED, ReservationStatus.DEDUCTED);
    }
}
