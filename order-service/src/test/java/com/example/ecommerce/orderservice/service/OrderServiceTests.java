package com.example.ecommerce.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ecommerce.orderservice.client.cart.CartClient;
import com.example.ecommerce.orderservice.client.cart.CartItemSnapshot;
import com.example.ecommerce.orderservice.client.cart.CartSnapshot;
import com.example.ecommerce.orderservice.client.inventory.InventoryReservationClient;
import com.example.ecommerce.orderservice.client.inventory.InventoryReservationItem;
import com.example.ecommerce.orderservice.client.inventory.InventoryReservationResult;
import com.example.ecommerce.orderservice.client.inventory.InventoryReservationStatus;
import com.example.ecommerce.orderservice.config.GatewayUser;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    private static final Long USER_ID = 10L;
    private static final Long CART_ID = 20L;
    private static final Long ORDER_ID = 1000L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartClient cartClient;

    @Mock
    private InventoryReservationClient inventoryReservationClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, cartClient, inventoryReservationClient);
    }

    @Test
    void checkoutRejectsEmptyCart() {
        when(cartClient.getCurrentCart(user()))
            .thenReturn(new CartSnapshot(CART_ID, USER_ID, "ACTIVE", List.of(), BigDecimal.ZERO));

        assertThatThrownBy(() -> orderService.checkout(user()))
            .isInstanceOf(EmptyCartException.class);

        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    void checkoutReturnsExistingNonTerminalOrderForSameCart() {
        CartSnapshot cart = cartWithOneItem();
        Order existing = assignId(sampleOrder(), ORDER_ID);
        when(cartClient.getCurrentCart(user())).thenReturn(cart);
        when(orderRepository.findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDescIdDesc(
            USER_ID,
            CART_ID,
            List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
        )).thenReturn(Optional.of(existing));

        OrderResponse response = orderService.checkout(user());

        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.sourceCartId()).isEqualTo(CART_ID);
        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(inventoryReservationClient);
    }

    @Test
    void checkoutCreatesOrderAndMarksStockReserved() {
        when(cartClient.getCurrentCart(user())).thenReturn(cartWithOneItem());
        when(orderRepository.findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDescIdDesc(
            USER_ID,
            CART_ID,
            List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
        )).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0), ORDER_ID));
        when(inventoryReservationClient.reserve(eq(ORDER_ID), any()))
            .thenReturn(new InventoryReservationResult(ORDER_ID, InventoryReservationStatus.RESERVED));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.checkout(user());

        assertThat(response.status()).isEqualTo(OrderStatus.STOCK_RESERVED);
        ArgumentCaptor<List<InventoryReservationItem>> itemsCaptor = ArgumentCaptor.captor();
        verify(inventoryReservationClient).reserve(eq(ORDER_ID), itemsCaptor.capture());
        assertThat(itemsCaptor.getValue())
            .containsExactly(new InventoryReservationItem(100L, 2));
    }

    @Test
    void checkoutCancelsOrderWhenReservationFails() {
        when(cartClient.getCurrentCart(user())).thenReturn(cartWithOneItem());
        when(orderRepository.findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDescIdDesc(
            USER_ID,
            CART_ID,
            List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
        )).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0), ORDER_ID));
        when(inventoryReservationClient.reserve(eq(ORDER_ID), any()))
            .thenReturn(new InventoryReservationResult(ORDER_ID, InventoryReservationStatus.FAILED));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> orderService.checkout(user()))
            .isInstanceOf(InventoryReservationFailedException.class);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void checkoutCancelsOrderWhenReservationServiceIsUnavailable() {
        when(cartClient.getCurrentCart(user())).thenReturn(cartWithOneItem());
        when(orderRepository.findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDescIdDesc(
            USER_ID,
            CART_ID,
            List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
        )).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0), ORDER_ID));
        when(inventoryReservationClient.reserve(eq(ORDER_ID), any()))
            .thenThrow(new InventoryServiceUnavailableException());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> orderService.checkout(user()))
            .isInstanceOf(InventoryServiceUnavailableException.class);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void findCurrentUserOrdersCallsUserScopedRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Order order = assignId(sampleOrder(), ORDER_ID);
        when(orderRepository.findByUserId(USER_ID, pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        Page<OrderResponse> response = orderService.findCurrentUserOrders(USER_ID, pageable);

        assertThat(response.getContent()).extracting(OrderResponse::orderId).containsExactly(ORDER_ID);
    }

    @Test
    void findCurrentUserOrderHidesOtherUsersOrders() {
        when(orderRepository.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findCurrentUserOrder(USER_ID, ORDER_ID))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void findAdminOrdersFiltersByStatusWhenStatusIsPresent() {
        Pageable pageable = PageRequest.of(0, 20);
        Order order = assignId(sampleOrder(), ORDER_ID);
        when(orderRepository.findByStatus(OrderStatus.PENDING, pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        Page<OrderResponse> response = orderService.findAdminOrders(OrderStatus.PENDING, pageable);

        assertThat(response.getContent()).extracting(OrderResponse::status).containsExactly(OrderStatus.PENDING);
        verify(orderRepository, never()).findAll(pageable);
    }

    @Test
    void findAdminOrdersUsesFindAllWhenStatusIsAbsent() {
        Pageable pageable = PageRequest.of(0, 20);
        Order order = assignId(sampleOrder(), ORDER_ID);
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        Page<OrderResponse> response = orderService.findAdminOrders(null, pageable);

        assertThat(response.getContent()).extracting(OrderResponse::orderId).containsExactly(ORDER_ID);
    }

    @Test
    void responseTimestampsTreatEntityTimestampsAsUtc() {
        Order order = assignId(sampleOrder(), ORDER_ID);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.parse("2026-05-13T12:00:00"));
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.parse("2026-05-13T12:05:00"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

            OrderResponse response = orderService.findAdminOrder(ORDER_ID);

            assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-13T12:00:00Z"));
            assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-05-13T12:05:00Z"));
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    void adminCancelReleasesStockForReservedOrder() {
        Order order = assignId(sampleOrder(), ORDER_ID);
        order.markStockReserved();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(inventoryReservationClient.release(ORDER_ID))
            .thenReturn(new InventoryReservationResult(ORDER_ID, InventoryReservationStatus.RELEASED));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelAsAdmin(ORDER_ID, "Customer requested cancellation");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryReservationClient).release(ORDER_ID);
    }

    @Test
    void adminCancelRejectsTerminalOrders() {
        Order order = assignId(sampleOrder(), ORDER_ID);
        order.cancel("Already cancelled");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelAsAdmin(ORDER_ID, "Second cancellation"))
            .isInstanceOf(InvalidOrderOperationException.class);

        verifyNoInteractions(inventoryReservationClient);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void adminCancelLeavesOrderUnchangedWhenReleaseThrows() {
        Order order = assignId(sampleOrder(), ORDER_ID);
        order.markStockReserved();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(inventoryReservationClient.release(ORDER_ID)).thenThrow(new InventoryServiceUnavailableException());

        assertThatThrownBy(() -> orderService.cancelAsAdmin(ORDER_ID, "Customer requested cancellation"))
            .isInstanceOf(InventoryServiceUnavailableException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
        verify(orderRepository, never()).save(any());
    }

    private static GatewayUser user() {
        return new GatewayUser(USER_ID, "user@example.com", List.of("USER"));
    }

    private static CartSnapshot cartWithOneItem() {
        return new CartSnapshot(
            CART_ID,
            USER_ID,
            "ACTIVE",
            List.of(new CartItemSnapshot(
                100L,
                "Pour Over",
                new BigDecimal("19.99"),
                2,
                new BigDecimal("39.98")
            )),
            new BigDecimal("39.98")
        );
    }

    private static Order sampleOrder() {
        return Order.createFromCart(USER_ID, CART_ID, List.of(orderItem()));
    }

    private static OrderItem orderItem() {
        return OrderItem.create(100L, "Pour Over", new BigDecimal("19.99"), 2);
    }

    private static Order assignId(Order order, Long id) {
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
