package com.example.ecommerce.inventoryservice.controller;

import com.example.ecommerce.inventoryservice.dto.InventoryAdjustmentRequest;
import com.example.ecommerce.inventoryservice.dto.InventoryItemResponse;
import com.example.ecommerce.inventoryservice.dto.StockLevelRequest;
import com.example.ecommerce.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/inventory/items")
class InventoryController {

    private final InventoryService inventoryService;

    InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    Page<InventoryItemResponse> list(Pageable pageable) {
        return inventoryService.list(pageable);
    }

    @GetMapping("/{productId}")
    InventoryItemResponse getByProductId(@PathVariable Long productId) {
        return inventoryService.getByProductId(productId);
    }

    @PutMapping("/{productId}")
    InventoryItemResponse setStock(
        @PathVariable Long productId,
        @Valid @RequestBody StockLevelRequest request
    ) {
        return inventoryService.setStock(productId, request);
    }

    @PostMapping("/{productId}/adjust")
    InventoryItemResponse adjustStock(
        @PathVariable Long productId,
        @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        return inventoryService.adjustStock(productId, request);
    }
}
