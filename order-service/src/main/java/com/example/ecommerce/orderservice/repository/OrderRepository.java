package com.example.ecommerce.orderservice.repository;

import com.example.ecommerce.orderservice.entity.Order;
import com.example.ecommerce.orderservice.entity.OrderStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Optional<Order> findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDesc(
        Long userId,
        Long sourceCartId,
        Collection<OrderStatus> statuses
    );
}
