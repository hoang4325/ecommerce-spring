package com.example.ecommerce.inventoryservice.controller;

import com.example.ecommerce.inventoryservice.dto.ReserveStockRequest;
import com.example.ecommerce.inventoryservice.dto.StockReservationResultResponse;
import com.example.ecommerce.inventoryservice.service.StockReservationService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/inventory/reservations")
class StockReservationController {

    private final StockReservationService stockReservationService;

    StockReservationController(StockReservationService stockReservationService) {
        this.stockReservationService = stockReservationService;
    }

    @PostMapping
    StockReservationResultResponse reserve(@Valid @RequestBody ReserveStockRequest request) {
        return stockReservationService.reserve(request);
    }

    @PostMapping("/{orderId}/release")
    StockReservationResultResponse release(@PathVariable Long orderId) {
        return stockReservationService.release(orderId);
    }

    @PostMapping("/{orderId}/deduct")
    StockReservationResultResponse deduct(@PathVariable Long orderId) {
        return stockReservationService.deduct(orderId);
    }
}
