package com.example.ecommerce.inventoryservice.service;

import com.example.ecommerce.inventoryservice.dto.ReservationItemRequest;
import com.example.ecommerce.inventoryservice.dto.ReserveStockRequest;
import com.example.ecommerce.inventoryservice.dto.StockReservationResponse;
import com.example.ecommerce.inventoryservice.dto.StockReservationResultResponse;
import com.example.ecommerce.inventoryservice.entity.InventoryItem;
import com.example.ecommerce.inventoryservice.entity.ReservationStatus;
import com.example.ecommerce.inventoryservice.entity.StockReservation;
import com.example.ecommerce.inventoryservice.exception.DuplicateReservationException;
import com.example.ecommerce.inventoryservice.exception.InvalidStockOperationException;
import com.example.ecommerce.inventoryservice.exception.ResourceNotFoundException;
import com.example.ecommerce.inventoryservice.repository.InventoryItemRepository;
import com.example.ecommerce.inventoryservice.repository.StockReservationRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StockReservationService {

    private static final Collection<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(
        ReservationStatus.RESERVED,
        ReservationStatus.DEDUCTED
    );
    private static final String DUPLICATE_PRODUCT_MESSAGE = "Duplicate product in reservation request";
    private static final String INVENTORY_ITEM_NOT_FOUND_MESSAGE = "Inventory item not found";
    private static final String INVALID_TRANSITION_MESSAGE = "Invalid stock reservation transition";

    private final InventoryItemRepository inventoryItemRepository;
    private final StockReservationRepository stockReservationRepository;

    public StockReservationService(
        InventoryItemRepository inventoryItemRepository,
        StockReservationRepository stockReservationRepository
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.stockReservationRepository = stockReservationRepository;
    }

    public StockReservationResultResponse reserve(ReserveStockRequest request) {
        rejectDuplicateProducts(request.items());
        if (stockReservationRepository.existsByOrderIdAndStatusIn(request.orderId(), ACTIVE_RESERVATION_STATUSES)) {
            throw new DuplicateReservationException();
        }

        List<Long> productIds = productIds(request.items());
        Map<Long, InventoryItem> inventoryByProductId = inventoryItemRepository
            .findAllByProductIdInForUpdate(productIds)
            .stream()
            .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));

        String failureReason = firstReservationFailure(request.items(), inventoryByProductId);
        if (failureReason != null) {
            List<StockReservation> failedReservations = request.items().stream()
                .map(item -> StockReservation.failed(
                    request.orderId(),
                    item.productId(),
                    item.quantity(),
                    failureReasonFor(item, inventoryByProductId)
                ))
                .toList();
            return toResult(request.orderId(), ReservationStatus.FAILED, stockReservationRepository.saveAll(failedReservations));
        }

        List<StockReservation> reservations = request.items().stream()
            .map(item -> {
                InventoryItem inventoryItem = inventoryByProductId.get(item.productId());
                inventoryItem.reserve(item.quantity());
                return StockReservation.reserved(request.orderId(), item.productId(), item.quantity());
            })
            .toList();

        return toResult(request.orderId(), ReservationStatus.RESERVED, stockReservationRepository.saveAll(reservations));
    }

    public StockReservationResultResponse release(Long orderId) {
        List<StockReservation> reservations = findReservations(orderId);
        if (reservations.stream().anyMatch(reservation -> reservation.getStatus() == ReservationStatus.DEDUCTED)) {
            throw new InvalidStockOperationException(INVALID_TRANSITION_MESSAGE);
        }

        List<StockReservation> reservedReservations = reservationsWithStatus(reservations, ReservationStatus.RESERVED);
        if (!reservedReservations.isEmpty()) {
            Map<Long, InventoryItem> inventoryByProductId = lockInventoryFor(reservedReservations);
            reservedReservations.forEach(reservation -> {
                InventoryItem item = inventoryByProductId.get(reservation.getProductId());
                if (item == null) {
                    throw new ResourceNotFoundException(INVENTORY_ITEM_NOT_FOUND_MESSAGE);
                }
                item.release(reservation.getQuantity());
                reservation.release();
            });
        }

        return toResult(orderId, aggregateStatus(reservations), reservations);
    }

    public StockReservationResultResponse deduct(Long orderId) {
        List<StockReservation> reservations = findReservations(orderId);
        if (reservations.stream().anyMatch(reservation ->
            reservation.getStatus() == ReservationStatus.RELEASED || reservation.getStatus() == ReservationStatus.FAILED
        )) {
            throw new InvalidStockOperationException(INVALID_TRANSITION_MESSAGE);
        }

        List<StockReservation> reservedReservations = reservationsWithStatus(reservations, ReservationStatus.RESERVED);
        if (!reservedReservations.isEmpty()) {
            Map<Long, InventoryItem> inventoryByProductId = lockInventoryFor(reservedReservations);
            reservedReservations.forEach(reservation -> {
                InventoryItem item = inventoryByProductId.get(reservation.getProductId());
                if (item == null) {
                    throw new ResourceNotFoundException(INVENTORY_ITEM_NOT_FOUND_MESSAGE);
                }
                item.deductReserved(reservation.getQuantity());
                reservation.deduct();
            });
        }

        return toResult(orderId, aggregateStatus(reservations), reservations);
    }

    private List<StockReservation> findReservations(Long orderId) {
        List<StockReservation> reservations = stockReservationRepository.findAllByOrderIdOrderByProductIdAsc(orderId);
        if (reservations.isEmpty()) {
            throw new InvalidStockOperationException(INVALID_TRANSITION_MESSAGE);
        }
        return reservations;
    }

    private void rejectDuplicateProducts(List<ReservationItemRequest> items) {
        Set<Long> seenProductIds = new HashSet<>();
        for (ReservationItemRequest item : items) {
            if (!seenProductIds.add(item.productId())) {
                throw new InvalidStockOperationException(DUPLICATE_PRODUCT_MESSAGE);
            }
        }
    }

    private String firstReservationFailure(
        List<ReservationItemRequest> items,
        Map<Long, InventoryItem> inventoryByProductId
    ) {
        return items.stream()
            .map(item -> failureReasonFor(item, inventoryByProductId))
            .filter(reason -> reason != null)
            .findFirst()
            .orElse(null);
    }

    private String failureReasonFor(ReservationItemRequest requestItem, Map<Long, InventoryItem> inventoryByProductId) {
        InventoryItem inventoryItem = inventoryByProductId.get(requestItem.productId());
        if (inventoryItem == null) {
            return INVENTORY_ITEM_NOT_FOUND_MESSAGE;
        }
        if (inventoryItem.getAvailableQuantity() < requestItem.quantity()) {
            return "Insufficient stock for product " + requestItem.productId();
        }
        return null;
    }

    private Map<Long, InventoryItem> lockInventoryFor(List<StockReservation> reservations) {
        return inventoryItemRepository
            .findAllByProductIdInForUpdate(reservations.stream().map(StockReservation::getProductId).toList())
            .stream()
            .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
    }

    private List<StockReservation> reservationsWithStatus(
        List<StockReservation> reservations,
        ReservationStatus status
    ) {
        return reservations.stream()
            .filter(reservation -> reservation.getStatus() == status)
            .toList();
    }

    private List<Long> productIds(List<ReservationItemRequest> items) {
        return items.stream().map(ReservationItemRequest::productId).toList();
    }

    private ReservationStatus aggregateStatus(List<StockReservation> reservations) {
        if (reservations.stream().allMatch(reservation -> reservation.getStatus() == ReservationStatus.DEDUCTED)) {
            return ReservationStatus.DEDUCTED;
        }
        if (reservations.stream().allMatch(reservation -> reservation.getStatus() == ReservationStatus.RELEASED)) {
            return ReservationStatus.RELEASED;
        }
        if (reservations.stream().allMatch(reservation -> reservation.getStatus() == ReservationStatus.FAILED)) {
            return ReservationStatus.FAILED;
        }
        if (reservations.stream().anyMatch(reservation -> reservation.getStatus() == ReservationStatus.RESERVED)) {
            return ReservationStatus.RESERVED;
        }
        return reservations.getFirst().getStatus();
    }

    private StockReservationResultResponse toResult(
        Long orderId,
        ReservationStatus status,
        List<StockReservation> reservations
    ) {
        return new StockReservationResultResponse(
            orderId,
            status,
            reservations.stream().map(this::toResponse).toList()
        );
    }

    private StockReservationResponse toResponse(StockReservation reservation) {
        return new StockReservationResponse(
            reservation.getId(),
            reservation.getOrderId(),
            reservation.getProductId(),
            reservation.getQuantity(),
            reservation.getStatus(),
            reservation.getFailureReason(),
            toInstant(reservation.getCreatedAt()),
            toInstant(reservation.getUpdatedAt())
        );
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
