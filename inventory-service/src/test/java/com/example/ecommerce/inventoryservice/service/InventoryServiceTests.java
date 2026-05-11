package com.example.ecommerce.inventoryservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ecommerce.inventoryservice.dto.InventoryAdjustmentRequest;
import com.example.ecommerce.inventoryservice.dto.InventoryItemResponse;
import com.example.ecommerce.inventoryservice.dto.StockLevelRequest;
import com.example.ecommerce.inventoryservice.entity.InventoryItem;
import com.example.ecommerce.inventoryservice.exception.InvalidStockOperationException;
import com.example.ecommerce.inventoryservice.exception.ResourceNotFoundException;
import com.example.ecommerce.inventoryservice.repository.InventoryItemRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTests {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void setStockCreatesNewItem() {
        StockLevelRequest request = new StockLevelRequest(25);
        when(inventoryItemRepository.findByProductId(10L)).thenReturn(Optional.empty());
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> {
            InventoryItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 1L);
            return item;
        });

        InventoryItemResponse response = inventoryService.setStock(10L, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.productId()).isEqualTo(10L);
        assertThat(response.availableQuantity()).isEqualTo(25);
        assertThat(response.reservedQuantity()).isZero();
    }

    @Test
    void setStockUpdatesAvailableQuantityAndPreservesReservedQuantity() {
        InventoryItem item = inventoryItem(10L, 10);
        item.reserve(3);
        when(inventoryItemRepository.findByProductId(10L)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(item)).thenReturn(item);

        InventoryItemResponse response = inventoryService.setStock(10L, new StockLevelRequest(25));

        assertThat(response.availableQuantity()).isEqualTo(25);
        assertThat(response.reservedQuantity()).isEqualTo(3);
        assertThat(item.getAvailableQuantity()).isEqualTo(25);
        assertThat(item.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    void adjustStockIncreasesAvailableQuantity() {
        InventoryItem item = inventoryItem(10L, 10);
        when(inventoryItemRepository.findByProductId(10L)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(item)).thenReturn(item);

        InventoryItemResponse response = inventoryService.adjustStock(10L, new InventoryAdjustmentRequest(5));

        assertThat(response.availableQuantity()).isEqualTo(15);
        assertThat(response.reservedQuantity()).isZero();
    }

    @Test
    void adjustStockRejectsZeroDelta() {
        assertThatThrownBy(() -> inventoryService.adjustStock(10L, new InventoryAdjustmentRequest(0)))
            .isInstanceOf(InvalidStockOperationException.class)
            .hasMessage("Stock adjustment delta must not be zero");
        verifyNoInteractions(inventoryItemRepository);
    }

    @Test
    void adjustStockRejectsNegativeResult() {
        InventoryItem item = inventoryItem(10L, 2);
        when(inventoryItemRepository.findByProductId(10L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.adjustStock(10L, new InventoryAdjustmentRequest(-3)))
            .isInstanceOf(InvalidStockOperationException.class)
            .hasMessage("Available quantity must not be negative");
        verify(inventoryItemRepository, never()).save(any(InventoryItem.class));
    }

    @Test
    void getByProductIdRejectsMissingItem() {
        when(inventoryItemRepository.findByProductId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getByProductId(10L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Inventory item not found");
    }

    @Test
    void listReturnsMappedResponses() {
        InventoryItem item = inventoryItem(10L, 4);
        ReflectionTestUtils.setField(item, "id", 1L);
        PageRequest pageable = PageRequest.of(0, 20);
        when(inventoryItemRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        Page<InventoryItemResponse> response = inventoryService.list(pageable);

        assertThat(response.getContent())
            .extracting(InventoryItemResponse::productId)
            .containsExactly(10L);
    }

    private static InventoryItem inventoryItem(Long productId, int availableQuantity) {
        return InventoryItem.create(productId, availableQuantity);
    }
}
