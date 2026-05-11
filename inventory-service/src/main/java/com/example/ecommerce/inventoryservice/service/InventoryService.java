package com.example.ecommerce.inventoryservice.service;

import com.example.ecommerce.inventoryservice.dto.InventoryAdjustmentRequest;
import com.example.ecommerce.inventoryservice.dto.InventoryItemResponse;
import com.example.ecommerce.inventoryservice.dto.StockLevelRequest;
import com.example.ecommerce.inventoryservice.entity.InventoryItem;
import com.example.ecommerce.inventoryservice.exception.InvalidStockOperationException;
import com.example.ecommerce.inventoryservice.exception.ResourceNotFoundException;
import com.example.ecommerce.inventoryservice.repository.InventoryItemRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {

    private static final String INVENTORY_ITEM_NOT_FOUND_MESSAGE = "Inventory item not found";
    private static final String ZERO_ADJUSTMENT_MESSAGE = "Stock adjustment delta must not be zero";

    private final InventoryItemRepository inventoryItemRepository;

    public InventoryService(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    public InventoryItemResponse setStock(Long productId, StockLevelRequest request) {
        InventoryItem item = inventoryItemRepository.findByProductId(productId)
            .orElseGet(() -> InventoryItem.create(productId, request.availableQuantity()));
        item.setAvailableQuantity(request.availableQuantity());
        return toResponse(inventoryItemRepository.save(item));
    }

    public InventoryItemResponse adjustStock(Long productId, InventoryAdjustmentRequest request) {
        if (request.delta() == 0) {
            throw new InvalidStockOperationException(ZERO_ADJUSTMENT_MESSAGE);
        }

        InventoryItem item = findByProductId(productId);
        try {
            item.adjustAvailableQuantity(request.delta());
        } catch (IllegalArgumentException ex) {
            throw new InvalidStockOperationException(ex.getMessage());
        }
        return toResponse(inventoryItemRepository.save(item));
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getByProductId(Long productId) {
        return toResponse(findByProductId(productId));
    }

    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> list(Pageable pageable) {
        return inventoryItemRepository.findAll(pageable).map(this::toResponse);
    }

    private InventoryItem findByProductId(Long productId) {
        return inventoryItemRepository.findByProductId(productId)
            .orElseThrow(() -> new ResourceNotFoundException(INVENTORY_ITEM_NOT_FOUND_MESSAGE));
    }

    private InventoryItemResponse toResponse(InventoryItem item) {
        return new InventoryItemResponse(
            item.getId(),
            item.getProductId(),
            item.getAvailableQuantity(),
            item.getReservedQuantity(),
            toInstant(item.getCreatedAt()),
            toInstant(item.getUpdatedAt())
        );
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
