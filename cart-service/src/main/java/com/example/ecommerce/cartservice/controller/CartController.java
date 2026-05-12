package com.example.ecommerce.cartservice.controller;

import com.example.ecommerce.cartservice.config.GatewayUser;
import com.example.ecommerce.cartservice.dto.AddCartItemRequest;
import com.example.ecommerce.cartservice.dto.CartResponse;
import com.example.ecommerce.cartservice.dto.UpdateCartItemRequest;
import com.example.ecommerce.cartservice.exception.MissingUserIdentityException;
import com.example.ecommerce.cartservice.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/cart")
class CartController {

    private final CartService cartService;

    CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    CartResponse getCurrentCart(Authentication authentication) {
        return cartService.getCurrentCart(currentUserId(authentication));
    }

    @PostMapping("/items")
    CartResponse addItem(Authentication authentication, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(currentUserId(authentication), request);
    }

    @PutMapping("/items/{productId}")
    CartResponse updateItem(
        Authentication authentication,
        @PathVariable Long productId,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateItem(currentUserId(authentication), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeItem(Authentication authentication, @PathVariable Long productId) {
        cartService.removeItem(currentUserId(authentication), productId);
    }

    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearCart(Authentication authentication) {
        cartService.clearCart(currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof GatewayUser user)) {
            throw new MissingUserIdentityException();
        }

        return user.userId();
    }
}
