package com.example.ecommerce.orderservice.service;

import com.example.ecommerce.orderservice.client.cart.CartClient;
import com.example.ecommerce.orderservice.client.cart.CartItemSnapshot;
import com.example.ecommerce.orderservice.client.cart.CartSnapshot;
import com.example.ecommerce.orderservice.client.inventory.InventoryReservationClient;
import com.example.ecommerce.orderservice.client.inventory.InventoryReservationItem;
import com.example.ecommerce.orderservice.client.inventory.InventoryReservationResult;
import com.example.ecommerce.orderservice.client.inventory.InventoryReservationStatus;
import com.example.ecommerce.orderservice.config.GatewayUser;
import com.example.ecommerce.orderservice.dto.OrderItemResponse;
import com.example.ecommerce.orderservice.dto.OrderResponse;
import com.example.ecommerce.orderservice.entity.Order;
import com.example.ecommerce.orderservice.entity.OrderItem;
import com.example.ecommerce.orderservice.entity.OrderStatus;
import com.example.ecommerce.orderservice.exception.EmptyCartException;
import com.example.ecommerce.orderservice.exception.InventoryReservationFailedException;
import com.example.ecommerce.orderservice.exception.InventoryServiceUnavailableException;
import com.example.ecommerce.orderservice.exception.InvalidOrderOperationException;
import com.example.ecommerce.orderservice.exception.OrderNotFoundException;
import com.example.ecommerce.orderservice.repository.OrderRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final List<OrderStatus> TERMINAL_STATUSES = List.of(
        OrderStatus.CANCELLED,
        OrderStatus.COMPLETED
    );
    private static final String STOCK_RESERVATION_FAILED_REASON = "Stock reservation failed";
    private static final String INVENTORY_SERVICE_UNAVAILABLE_REASON = "Inventory service unavailable";
    private static final String STOCK_RELEASE_FAILED_MESSAGE = "Stock release failed";
    private static final String TERMINAL_ORDER_MESSAGE = "Terminal order cannot be changed";

    private final OrderRepository orderRepository;
    private final CartClient cartClient;
    private final InventoryReservationClient inventoryReservationClient;

    public OrderService(
        OrderRepository orderRepository,
        CartClient cartClient,
        InventoryReservationClient inventoryReservationClient
    ) {
        this.orderRepository = orderRepository;
        this.cartClient = cartClient;
        this.inventoryReservationClient = inventoryReservationClient;
    }

    @Transactional(noRollbackFor = {
        InventoryReservationFailedException.class,
        InventoryServiceUnavailableException.class
    })
    public OrderResponse checkout(GatewayUser user) {
        CartSnapshot cart = cartClient.getCurrentCart(user);
        validateCart(cart);

        return orderRepository.findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDescIdDesc(
                user.id(),
                cart.cartId(),
                TERMINAL_STATUSES
            )
            .map(OrderService::toResponse)
            .orElseGet(() -> createAndReserveOrder(user, cart));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findCurrentUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable)
            .map(OrderService::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse findCurrentUserOrder(Long userId, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
            .map(OrderService::toResponse)
            .orElseThrow(OrderNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findAdminOrders(OrderStatus status, Pageable pageable) {
        Page<Order> orders = status == null
            ? orderRepository.findAll(pageable)
            : orderRepository.findByStatus(status, pageable);
        return orders.map(OrderService::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse findAdminOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .map(OrderService::toResponse)
            .orElseThrow(OrderNotFoundException::new);
    }

    @Transactional
    public OrderResponse cancelAsAdmin(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(OrderNotFoundException::new);
        if (order.isTerminal()) {
            throw new InvalidOrderOperationException(TERMINAL_ORDER_MESSAGE);
        }

        releaseStockIfNeeded(order);
        order.cancel(reason);
        return toResponse(orderRepository.save(order));
    }

    private OrderResponse createAndReserveOrder(GatewayUser user, CartSnapshot cart) {
        Order order = Order.createFromCart(
            user.id(),
            cart.cartId(),
            cart.items().stream()
                .map(OrderService::toOrderItem)
                .toList()
        );
        Order savedOrder = orderRepository.saveAndFlush(order);

        try {
            InventoryReservationResult result = inventoryReservationClient.reserve(
                savedOrder.getId(),
                toReservationItems(savedOrder)
            );
            if (result == null || result.status() != InventoryReservationStatus.RESERVED) {
                cancelAndSave(savedOrder, STOCK_RESERVATION_FAILED_REASON);
                throw new InventoryReservationFailedException();
            }

            savedOrder.markStockReserved();
            return toResponse(orderRepository.save(savedOrder));
        } catch (InventoryServiceUnavailableException ex) {
            cancelAndSave(savedOrder, INVENTORY_SERVICE_UNAVAILABLE_REASON);
            throw ex;
        }
    }

    private void releaseStockIfNeeded(Order order) {
        if (order.getStatus() != OrderStatus.STOCK_RESERVED && order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            return;
        }

        InventoryReservationResult result = inventoryReservationClient.release(order.getId());
        if (result == null || result.status() != InventoryReservationStatus.RELEASED) {
            throw new InventoryReservationFailedException(STOCK_RELEASE_FAILED_MESSAGE);
        }
    }

    private void cancelAndSave(Order order, String reason) {
        order.cancel(reason);
        orderRepository.save(order);
    }

    private static void validateCart(CartSnapshot cart) {
        if (cart == null || cart.cartId() == null || cart.items().isEmpty()) {
            throw new EmptyCartException();
        }
    }

    private static OrderItem toOrderItem(CartItemSnapshot item) {
        return OrderItem.create(
            item.productId(),
            item.productName(),
            item.unitPrice(),
            item.quantity()
        );
    }

    private static List<InventoryReservationItem> toReservationItems(Order order) {
        return order.getItems().stream()
            .map(item -> new InventoryReservationItem(item.getProductId(), item.getQuantity()))
            .toList();
    }

    private static OrderResponse toResponse(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getUserId(),
            order.getSourceCartId(),
            order.getStatus(),
            order.getItems().stream()
                .map(OrderService::toItemResponse)
                .toList(),
            order.getSubtotal(),
            order.getCancellationReason(),
            toInstant(order.getCreatedAt()),
            toInstant(order.getUpdatedAt())
        );
    }

    private static OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
            item.getProductId(),
            item.getProductName(),
            item.getUnitPrice(),
            item.getQuantity(),
            item.getLineTotal()
        );
    }

    private static Instant toInstant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }
}
