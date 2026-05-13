package com.example.ecommerce.orderservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.orderservice.entity.Order;
import com.example.ecommerce.orderservice.entity.OrderItem;
import com.example.ecommerce.orderservice.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:order_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTests {

    @Autowired
    private OrderRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsOrderWithItems() {
        Order order = repository.saveAndFlush(sampleOrder(10L, 20L));

        assertThat(order.getId()).isNotNull();
        assertThat(repository.findById(order.getId()).orElseThrow().getItems()).hasSize(1);
    }

    @Test
    void findsByIdAndUserId() {
        Order order = repository.saveAndFlush(sampleOrder(10L, 20L));

        assertThat(repository.findByIdAndUserId(order.getId(), 10L)).isPresent();
        assertThat(repository.findByIdAndUserId(order.getId(), 11L)).isEmpty();
    }

    @Test
    void findsExistingNonTerminalOrderByUserAndCart() {
        Order cancelled = sampleOrder(10L, 20L);
        cancelled.cancel("Stock failed");
        repository.saveAndFlush(cancelled);

        assertThat(repository.findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDescIdDesc(
            10L,
            20L,
            List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
        )).isEmpty();

        Order pending = repository.saveAndFlush(sampleOrder(10L, 20L));

        assertThat(repository.findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDescIdDesc(
            10L,
            20L,
            List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
        )).contains(pending);
    }

    @Test
    void findsNewestExistingNonTerminalOrderByUserAndCart() {
        Order olderPending = repository.saveAndFlush(sampleOrder(10L, 20L));
        Order newerPending = repository.saveAndFlush(sampleOrder(10L, 20L));
        setOrderTimestamps(olderPending, LocalDateTime.of(2026, 5, 13, 15, 29));
        setOrderTimestamps(newerPending, LocalDateTime.of(2026, 5, 13, 15, 30));
        entityManager.clear();

        assertThat(repository.findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDescIdDesc(
            10L,
            20L,
            List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
        )).map(Order::getId).contains(newerPending.getId());
    }

    @Test
    void findsNewestExistingNonTerminalOrderByUserAndCartBreaksCreatedAtTiesById() {
        Order lowerIdPending = repository.saveAndFlush(sampleOrder(10L, 20L));
        Order higherIdPending = repository.saveAndFlush(sampleOrder(10L, 20L));
        LocalDateTime sameTimestamp = LocalDateTime.of(2026, 5, 13, 15, 30);
        setOrderTimestamps(lowerIdPending, sameTimestamp);
        setOrderTimestamps(higherIdPending, sameTimestamp);
        entityManager.clear();

        assertThat(repository.findFirstByUserIdAndSourceCartIdAndStatusNotInOrderByCreatedAtDescIdDesc(
            10L,
            20L,
            List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
        )).map(Order::getId).contains(higherIdPending.getId());
    }

    @Test
    void filtersByStatusNewestFirst() {
        repository.saveAndFlush(sampleOrder(10L, 20L));
        Order olderReserved = sampleOrder(11L, 21L);
        olderReserved.markStockReserved();
        repository.saveAndFlush(olderReserved);
        Order newerReserved = sampleOrder(12L, 22L);
        newerReserved.markStockReserved();
        repository.saveAndFlush(newerReserved);
        setOrderTimestamps(olderReserved, LocalDateTime.of(2026, 5, 13, 15, 29));
        setOrderTimestamps(newerReserved, LocalDateTime.of(2026, 5, 13, 15, 30));
        entityManager.clear();

        Page<Order> result = repository.findByStatus(
            OrderStatus.STOCK_RESERVED,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(result.getContent()).extracting(Order::getStatus)
            .containsOnly(OrderStatus.STOCK_RESERVED);
        assertThat(result.getContent()).extracting(Order::getId)
            .containsExactly(newerReserved.getId(), olderReserved.getId());
    }

    private static Order sampleOrder(Long userId, Long sourceCartId) {
        return Order.createFromCart(userId, sourceCartId, List.of(
            OrderItem.create(100L, "Pour Over", new BigDecimal("19.99"), 1)
        ));
    }

    private void setOrderTimestamps(Order order, LocalDateTime timestamp) {
        entityManager.getEntityManager()
            .createNativeQuery("update orders set created_at = ?, updated_at = ? where id = ?")
            .setParameter(1, timestamp)
            .setParameter(2, timestamp)
            .setParameter(3, order.getId())
            .executeUpdate();
    }
}
