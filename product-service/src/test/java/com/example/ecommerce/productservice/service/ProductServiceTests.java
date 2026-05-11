package com.example.ecommerce.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ecommerce.productservice.dto.ProductRequest;
import com.example.ecommerce.productservice.dto.ProductResponse;
import com.example.ecommerce.productservice.entity.Category;
import com.example.ecommerce.productservice.entity.Product;
import com.example.ecommerce.productservice.exception.DuplicateSlugException;
import com.example.ecommerce.productservice.exception.ResourceNotFoundException;
import com.example.ecommerce.productservice.repository.CategoryRepository;
import com.example.ecommerce.productservice.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SlugNormalizer slugNormalizer;

    @InjectMocks
    private ProductService productService;

    @Test
    void createRejectsMissingCategory() {
        ProductRequest request = productRequest(10L, "Pour Over", "pour-over");
        when(categoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Category not found");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createRejectsInactiveCategory() {
        Category inactive = categoryWithId(10L, "Coffee", "coffee", false);
        ProductRequest request = productRequest(10L, "Pour Over", "pour-over");
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> productService.create(request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Category not found");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createSavesProductWithNormalizedSlugAndCategory() {
        Category category = categoryWithId(10L, "Coffee", "coffee", true);
        ProductRequest request = productRequest(10L, "Pour Over", " Pour-Over ");
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(slugNormalizer.normalize(" Pour-Over ")).thenReturn("pour-over");
        when(productRepository.existsBySlug("pour-over")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", 20L);
            return product;
        });

        ProductResponse response = productService.create(request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product saved = productCaptor.getValue();
        assertThat(saved.getCategory()).isSameAs(category);
        assertThat(saved.getSlug()).isEqualTo("pour-over");
        assertThat(saved.getPrice()).isEqualByComparingTo("19.99");
        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.categoryId()).isEqualTo(10L);
        assertThat(response.categoryName()).isEqualTo("Coffee");
        assertThat(response.categorySlug()).isEqualTo("coffee");
    }

    @Test
    void updateRejectsDuplicateSlugOwnedByAnotherProduct() {
        Category category = categoryWithId(10L, "Coffee", "coffee", true);
        Product current = productWithId(20L, category, "Pour Over", "pour-over", true);
        Product other = productWithId(21L, category, "French Press", "french-press", true);
        ProductRequest request = productRequest(10L, "Pour Over", "french-press");
        when(productRepository.findById(20L)).thenReturn(Optional.of(current));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(slugNormalizer.normalize("french-press")).thenReturn("french-press");
        when(productRepository.findBySlug("french-press")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> productService.update(20L, request))
            .isInstanceOf(DuplicateSlugException.class)
            .hasMessage("Slug is already in use");
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deactivateMarksProductInactive() {
        Category category = categoryWithId(10L, "Coffee", "coffee", true);
        Product product = productWithId(20L, category, "Pour Over", "pour-over", true);
        when(productRepository.findById(20L)).thenReturn(Optional.of(product));

        productService.deactivate(20L);

        assertThat(product.isActive()).isFalse();
        verify(productRepository).save(product);
    }

    @Test
    void getBySlugRejectsMissingOrInactiveProduct() {
        when(slugNormalizer.normalize(" Pour-Over ")).thenReturn("pour-over");
        when(productRepository.findBySlugAndActiveTrueAndCategoryActiveTrue("pour-over")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getBySlug(" Pour-Over "))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Product not found");
    }

    @Test
    void searchNormalizesCategorySlugAndDelegatesPageableQuery() {
        Category category = categoryWithId(10L, "Coffee", "coffee", true);
        Product product = productWithId(20L, category, "Pour Over", "pour-over", true);
        PageRequest pageable = PageRequest.of(1, 5);
        when(slugNormalizer.normalize(" Coffee ")).thenReturn("coffee");
        when(productRepository.searchActiveProducts("drip", "coffee", pageable))
            .thenReturn(new PageImpl<>(List.of(product), pageable, 1));

        Page<ProductResponse> response = productService.search("drip", " Coffee ", pageable);

        verify(productRepository).searchActiveProducts("drip", "coffee", pageable);
        assertThat(response.getContent())
            .extracting(ProductResponse::slug)
            .containsExactly("pour-over");
    }

    @Test
    void searchNormalizesBlankKeywordToNullBeforeDelegating() {
        Category category = categoryWithId(10L, "Coffee", "coffee", true);
        Product product = productWithId(20L, category, "Pour Over", "pour-over", true);
        PageRequest pageable = PageRequest.of(0, 10);
        when(productRepository.searchActiveProducts(null, null, pageable))
            .thenReturn(new PageImpl<>(List.of(product), pageable, 1));

        Page<ProductResponse> response = productService.search("   ", null, pageable);

        verify(productRepository).searchActiveProducts(null, null, pageable);
        assertThat(response.getContent())
            .extracting(ProductResponse::slug)
            .containsExactly("pour-over");
    }

    private static ProductRequest productRequest(Long categoryId, String name, String slug) {
        return new ProductRequest(
            categoryId,
            name,
            slug,
            "Description",
            new BigDecimal("19.99"),
            "https://example.com/image.jpg"
        );
    }

    private static Category categoryWithId(Long id, String name, String slug, boolean active) {
        Category category = Category.create(name, slug, "Description");
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "active", active);
        return category;
    }

    private static Product productWithId(Long id, Category category, String name, String slug, boolean active) {
        Product product = Product.create(
            category,
            name,
            slug,
            "Description",
            new BigDecimal("19.99"),
            "https://example.com/image.jpg"
        );
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "active", active);
        return product;
    }
}
