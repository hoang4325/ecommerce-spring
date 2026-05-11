package com.example.ecommerce.productservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.productservice.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:product_service_category_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRepositoryTests {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void uniqueSlugConstraintPreventsDuplicateCategories() {
        categoryRepository.saveAndFlush(Category.create("Coffee", "coffee", "Roasted coffee"));

        Category duplicate = Category.create("More Coffee", "coffee", "Duplicate slug");

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeCategoryListingExcludesInactiveCategoriesAndSortsByName() {
        Category coffee = categoryRepository.save(Category.create("Coffee", "coffee", "Roasted coffee"));
        Category tea = categoryRepository.save(Category.create("Tea", "tea", "Loose leaf tea"));
        Category inactive = categoryRepository.save(Category.create("Appliances", "appliances", "Machines"));
        inactive.deactivate();
        categoryRepository.flush();

        assertThat(categoryRepository.findAllByActiveTrueOrderByNameAsc())
            .extracting(Category::getName)
            .containsExactly(coffee.getName(), tea.getName());
    }

    @Test
    void categoryDescriptionAllowsDtoMaximumLength() {
        String description = "a".repeat(1000);

        Category saved = categoryRepository.saveAndFlush(Category.create("Coffee Gear", "coffee-gear", description));

        assertThat(saved.getDescription()).hasSize(1000);
    }
}
