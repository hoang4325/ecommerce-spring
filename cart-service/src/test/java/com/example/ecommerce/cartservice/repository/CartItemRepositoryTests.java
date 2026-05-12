package com.example.ecommerce.cartservice.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.cartservice.entity.Cart;
import com.example.ecommerce.cartservice.entity.CartItem;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cart_item_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CartItemRepositoryTests {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void duplicateProductInSameCartViolatesConstraint() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 1);
        Cart savedCart = cartRepository.saveAndFlush(cart);

        CartItem duplicate = CartItem.create(savedCart, 20L, "Pour Over", new BigDecimal("19.99"), 1);

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
