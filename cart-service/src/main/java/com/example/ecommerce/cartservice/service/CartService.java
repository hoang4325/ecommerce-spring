package com.example.ecommerce.cartservice.service;

import com.example.ecommerce.cartservice.client.ProductCatalogClient;
import com.example.ecommerce.cartservice.client.ProductCatalogItem;
import com.example.ecommerce.cartservice.dto.AddCartItemRequest;
import com.example.ecommerce.cartservice.dto.CartItemResponse;
import com.example.ecommerce.cartservice.dto.CartResponse;
import com.example.ecommerce.cartservice.dto.UpdateCartItemRequest;
import com.example.ecommerce.cartservice.entity.Cart;
import com.example.ecommerce.cartservice.entity.CartItem;
import com.example.ecommerce.cartservice.entity.CartStatus;
import com.example.ecommerce.cartservice.exception.CartItemNotFoundException;
import com.example.ecommerce.cartservice.exception.InvalidCartOperationException;
import com.example.ecommerce.cartservice.repository.CartRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final ProductCatalogClient productCatalogClient;

    public CartService(CartRepository cartRepository, ProductCatalogClient productCatalogClient) {
        this.cartRepository = cartRepository;
        this.productCatalogClient = productCatalogClient;
    }

    @Transactional(readOnly = true)
    public CartResponse getCurrentCart(Long userId) {
        return cartRepository.findByActiveCartKey(userId)
            .map(this::toResponse)
            .orElseGet(() -> emptyCartResponse(userId));
    }

    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        ProductCatalogItem product = productCatalogClient.getProduct(request.productId());
        Cart cart = cartRepository.findByActiveCartKey(userId)
            .orElseGet(() -> Cart.createActive(userId));

        try {
            cart.addOrIncrementItem(product.id(), product.name(), product.price(), request.quantity());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InvalidCartOperationException(ex.getMessage());
        }

        return toResponse(cartRepository.save(cart));
    }

    public CartResponse updateItem(Long userId, Long productId, UpdateCartItemRequest request) {
        Cart cart = cartRepository.findByActiveCartKey(userId)
            .orElseThrow(CartItemNotFoundException::new);
        if (!containsItem(cart, productId)) {
            throw new CartItemNotFoundException();
        }

        ProductCatalogItem product = productCatalogClient.getProduct(productId);

        try {
            cart.updateItem(product.id(), product.name(), product.price(), request.quantity());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InvalidCartOperationException(ex.getMessage());
        }

        return toResponse(cartRepository.save(cart));
    }

    public void removeItem(Long userId, Long productId) {
        Cart cart = cartRepository.findByActiveCartKey(userId)
            .orElseThrow(CartItemNotFoundException::new);

        boolean removed;
        try {
            removed = cart.removeItem(productId);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InvalidCartOperationException(ex.getMessage());
        }

        if (!removed) {
            throw new CartItemNotFoundException();
        }
        cartRepository.save(cart);
    }

    public void clearCart(Long userId) {
        cartRepository.findByActiveCartKey(userId).ifPresent(cart -> {
            try {
                cart.clearItems();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new InvalidCartOperationException(ex.getMessage());
            }
            cartRepository.save(cart);
        });
    }

    private static boolean containsItem(Cart cart, Long productId) {
        return cart.getItems().stream()
            .anyMatch(item -> item.getProductId().equals(productId));
    }

    private static CartResponse emptyCartResponse(Long userId) {
        return new CartResponse(null, userId, CartStatus.ACTIVE, List.of(), BigDecimal.ZERO);
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
            .map(this::toResponse)
            .toList();
        return new CartResponse(cart.getId(), cart.getUserId(), cart.getStatus(), items, cart.subtotal());
    }

    private CartItemResponse toResponse(CartItem item) {
        return new CartItemResponse(
            item.getProductId(),
            item.getProductNameSnapshot(),
            item.getUnitPriceSnapshot(),
            item.getQuantity(),
            item.lineTotal()
        );
    }
}
