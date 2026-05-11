package com.example.ecommerce.productservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.productservice.entity.Category;
import com.example.ecommerce.productservice.entity.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:product_service_product_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTests {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void uniqueSlugConstraintPreventsDuplicateProducts() {
        Category category = categoryRepository.saveAndFlush(Category.create("Coffee", "coffee", "Roasted coffee"));
        productRepository.saveAndFlush(product(category, "Morning Roast", "morning-roast", "Bright roast"));

        Product duplicate = product(category, "Evening Roast", "morning-roast", "Duplicate slug");

        assertThatThrownBy(() -> productRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeProductSearchFindsKeywordInNameOrDescription() {
        Category category = categoryRepository.saveAndFlush(Category.create("Coffee", "coffee", "Roasted coffee"));
        productRepository.save(product(category, "Morning Roast", "morning-roast", "Chocolate notes"));
        productRepository.save(product(category, "Colombia Beans", "colombia-beans", "Balanced caramel cup"));
        productRepository.save(product(category, "Green Tea", "green-tea", "Fresh leaves"));
        productRepository.flush();

        assertThat(productRepository.searchActiveProducts("caramel", null, PageRequest.of(0, 10)).getContent())
            .extracting(Product::getSlug)
            .containsExactly("colombia-beans");
        assertThat(productRepository.searchActiveProducts("morning", null, PageRequest.of(0, 10)).getContent())
            .extracting(Product::getSlug)
            .containsExactly("morning-roast");
    }

    @Test
    void activeProductSearchFiltersByCategorySlug() {
        Category coffee = categoryRepository.save(Category.create("Coffee", "coffee", "Roasted coffee"));
        Category tea = categoryRepository.save(Category.create("Tea", "tea", "Loose leaf tea"));
        productRepository.save(product(coffee, "Coffee Sampler", "coffee-sampler", "Starter pack"));
        productRepository.save(product(tea, "Tea Sampler", "tea-sampler", "Starter pack"));
        productRepository.flush();

        assertThat(productRepository.searchActiveProducts("sampler", "tea", PageRequest.of(0, 10)).getContent())
            .extracting(Product::getSlug)
            .containsExactly("tea-sampler");
    }

    @Test
    void productDetailLookupIgnoresInactiveProducts() {
        Category category = categoryRepository.saveAndFlush(Category.create("Coffee", "coffee", "Roasted coffee"));
        Product product = productRepository.save(product(category, "Morning Roast", "morning-roast", "Bright roast"));
        product.deactivate();
        productRepository.flush();

        assertThat(productRepository.findBySlugAndActiveTrue("morning-roast")).isEmpty();
    }

    private static Product product(Category category, String name, String slug, String description) {
        return Product.create(category, name, slug, description, BigDecimal.valueOf(12.50), "https://example.test/image.jpg");
    }
}
