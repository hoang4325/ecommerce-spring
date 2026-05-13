package com.example.ecommerce.orderservice.controller;

import com.example.ecommerce.orderservice.config.GatewayUser;
import com.example.ecommerce.orderservice.dto.OrderResponse;
import com.example.ecommerce.orderservice.exception.MissingUserIdentityException;
import com.example.ecommerce.orderservice.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse checkout(Authentication authentication) {
        return orderService.checkout(currentUser(authentication));
    }

    @GetMapping
    Page<OrderResponse> list(Authentication authentication, Pageable pageable) {
        return orderService.findCurrentUserOrders(currentUser(authentication).id(), pageable);
    }

    @GetMapping("/{id}")
    OrderResponse get(Authentication authentication, @PathVariable Long id) {
        return orderService.findCurrentUserOrder(currentUser(authentication).id(), id);
    }

    private static GatewayUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof GatewayUser user)) {
            throw new MissingUserIdentityException();
        }

        return user;
    }
}
