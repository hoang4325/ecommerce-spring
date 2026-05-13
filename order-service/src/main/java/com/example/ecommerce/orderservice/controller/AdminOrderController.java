package com.example.ecommerce.orderservice.controller;

import com.example.ecommerce.orderservice.dto.OrderResponse;
import com.example.ecommerce.orderservice.dto.UpdateOrderStatusRequest;
import com.example.ecommerce.orderservice.entity.OrderStatus;
import com.example.ecommerce.orderservice.exception.InvalidOrderOperationException;
import com.example.ecommerce.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController {

    private final OrderService orderService;

    AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    Page<OrderResponse> list(@RequestParam(required = false) OrderStatus status, Pageable pageable) {
        return orderService.findAdminOrders(status, pageable);
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable Long id) {
        return orderService.findAdminOrder(id);
    }

    @PatchMapping("/{id}/status")
    OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        if (request.status() != OrderStatus.CANCELLED) {
            throw new InvalidOrderOperationException("Only cancellation is supported");
        }

        return orderService.cancelAsAdmin(id, request.reason());
    }
}
