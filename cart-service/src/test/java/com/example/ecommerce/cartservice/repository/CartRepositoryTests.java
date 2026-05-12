package com.example.ecommerce.cartservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.cartservice.entity.Cart;
import com.example.ecommerce.cartservice.entity.CartStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cart_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CartRepositoryTests {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void findByUserIdAndStatusReturnsActiveCartWithItems() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 2);
        cartRepository.saveAndFlush(cart);

        Cart found = cartRepository.findByUserIdAndStatus(10L, CartStatus.ACTIVE).orElseThrow();

        assertThat(found.getUserId()).isEqualTo(10L);
        assertThat(found.getItems())
            .extracting(item -> item.getProductId())
            .containsExactly(20L);
    }

    @Test
    void findByActiveCartKeyReturnsActiveCartWithItems() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 2);
        cartRepository.saveAndFlush(cart);

        Cart found = cartRepository.findByActiveCartKey(10L).orElseThrow();

        assertThat(found.getUserId()).isEqualTo(10L);
        assertThat(found.getItems())
            .extracting(item -> item.getProductId())
            .containsExactly(20L);
    }

    @Test
    void savePopulatesTimestamps() {
        Cart cart = Cart.createActive(10L);

        Cart saved = cartRepository.saveAndFlush(cart);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void twoActiveCartsForSameUserViolateActiveCartKeyConstraint() {
        cartRepository.saveAndFlush(Cart.createActive(10L));

        assertThatThrownBy(() -> cartRepository.saveAndFlush(Cart.createActive(10L)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removeItemDeletesOrphanedCartItemOnFlush() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 1);
        Cart saved = cartRepository.saveAndFlush(cart);
        assertThat(cartItemRepository.count()).isEqualTo(1);

        saved.removeItem(20L);
        cartRepository.saveAndFlush(saved);

        assertThat(cartItemRepository.count()).isZero();
    }
}
