package com.example.ecommerce.cartservice.repository;

import com.example.ecommerce.cartservice.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
