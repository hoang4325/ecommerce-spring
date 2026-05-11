package com.example.ecommerce.inventoryservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.inventoryservice.entity.InventoryItem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:inventory_service_item_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryItemRepositoryTests {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Test
    void uniqueProductIdConstraintPreventsDuplicateInventoryItems() {
        inventoryItemRepository.saveAndFlush(InventoryItem.create(10L, 5));

        InventoryItem duplicate = InventoryItem.create(10L, 9);

        assertThatThrownBy(() -> inventoryItemRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByProductIdReturnsItem() {
        inventoryItemRepository.saveAndFlush(InventoryItem.create(10L, 5));

        assertThat(inventoryItemRepository.findByProductId(10L))
            .isPresent()
            .get()
            .extracting(InventoryItem::getAvailableQuantity)
            .isEqualTo(5);
    }

    @Test
    void findAllByProductIdInForUpdateReturnsOnlyRequestedProducts() {
        inventoryItemRepository.save(InventoryItem.create(10L, 5));
        inventoryItemRepository.save(InventoryItem.create(11L, 6));
        inventoryItemRepository.save(InventoryItem.create(12L, 7));
        inventoryItemRepository.flush();

        List<InventoryItem> items = inventoryItemRepository.findAllByProductIdInForUpdate(List.of(10L, 12L));

        assertThat(items)
            .extracting(InventoryItem::getProductId)
            .containsExactlyInAnyOrder(10L, 12L);
    }
}
