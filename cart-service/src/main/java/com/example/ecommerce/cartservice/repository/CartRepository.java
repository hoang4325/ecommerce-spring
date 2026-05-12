package com.example.ecommerce.cartservice.repository;

import com.example.ecommerce.cartservice.entity.Cart;
import com.example.ecommerce.cartservice.entity.CartStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByUserIdAndStatus(Long userId, CartStatus status);

    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByActiveCartKey(Long activeCartKey);
}
