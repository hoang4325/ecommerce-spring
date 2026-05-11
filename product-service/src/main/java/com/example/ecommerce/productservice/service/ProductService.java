package com.example.ecommerce.productservice.service;

import com.example.ecommerce.productservice.dto.ProductRequest;
import com.example.ecommerce.productservice.dto.ProductResponse;
import com.example.ecommerce.productservice.entity.Category;
import com.example.ecommerce.productservice.entity.Product;
import com.example.ecommerce.productservice.exception.DuplicateSlugException;
import com.example.ecommerce.productservice.exception.ResourceNotFoundException;
import com.example.ecommerce.productservice.repository.CategoryRepository;
import com.example.ecommerce.productservice.repository.ProductRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ProductService {

    private static final String CATEGORY_NOT_FOUND_MESSAGE = "Category not found";
    private static final String PRODUCT_NOT_FOUND_MESSAGE = "Product not found";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SlugNormalizer slugNormalizer;

    public ProductService(
        ProductRepository productRepository,
        CategoryRepository categoryRepository,
        SlugNormalizer slugNormalizer
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.slugNormalizer = slugNormalizer;
    }

    public ProductResponse create(ProductRequest request) {
        Category category = findActiveCategory(request.categoryId());
        String slug = slugNormalizer.normalize(request.slug());
        if (productRepository.existsBySlug(slug)) {
            throw new DuplicateSlugException();
        }

        Product product = Product.create(
            category,
            request.name(),
            slug,
            request.description(),
            request.price(),
            request.imageUrl()
        );
        return toResponse(productRepository.save(product));
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProduct(id);
        Category category = findActiveCategory(request.categoryId());
        String slug = slugNormalizer.normalize(request.slug());
        productRepository.findBySlug(slug)
            .filter(existing -> !existing.getId().equals(product.getId()))
            .ifPresent(existing -> {
                throw new DuplicateSlugException();
            });

        product.update(category, request.name(), slug, request.description(), request.price(), request.imageUrl());
        return toResponse(productRepository.save(product));
    }

    public void deactivate(Long id) {
        Product product = findProduct(id);
        product.deactivate();
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getBySlug(String slug) {
        String normalizedSlug = slugNormalizer.normalize(slug);
        return productRepository.findBySlugAndActiveTrueAndCategoryActiveTrue(normalizedSlug)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException(PRODUCT_NOT_FOUND_MESSAGE));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String keyword, String categorySlug, Pageable pageable) {
        String normalizedCategorySlug = StringUtils.hasText(categorySlug) ? slugNormalizer.normalize(categorySlug) : null;
        return productRepository.searchActiveProducts(keyword, normalizedCategorySlug, pageable).map(this::toResponse);
    }

    private Category findActiveCategory(Long id) {
        return categoryRepository.findById(id)
            .filter(Category::isActive)
            .orElseThrow(() -> new ResourceNotFoundException(CATEGORY_NOT_FOUND_MESSAGE));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(PRODUCT_NOT_FOUND_MESSAGE));
    }

    private ProductResponse toResponse(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
            product.getId(),
            category.getId(),
            category.getName(),
            category.getSlug(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getPrice(),
            product.getImageUrl(),
            product.isActive(),
            toInstant(product.getCreatedAt()),
            toInstant(product.getUpdatedAt())
        );
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
