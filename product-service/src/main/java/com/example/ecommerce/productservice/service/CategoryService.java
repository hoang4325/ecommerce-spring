package com.example.ecommerce.productservice.service;

import com.example.ecommerce.productservice.dto.CategoryRequest;
import com.example.ecommerce.productservice.dto.CategoryResponse;
import com.example.ecommerce.productservice.entity.Category;
import com.example.ecommerce.productservice.exception.DuplicateSlugException;
import com.example.ecommerce.productservice.exception.ResourceNotFoundException;
import com.example.ecommerce.productservice.repository.CategoryRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {

    private static final String CATEGORY_NOT_FOUND_MESSAGE = "Category not found";

    private final CategoryRepository categoryRepository;
    private final SlugNormalizer slugNormalizer;

    public CategoryService(CategoryRepository categoryRepository, SlugNormalizer slugNormalizer) {
        this.categoryRepository = categoryRepository;
        this.slugNormalizer = slugNormalizer;
    }

    public CategoryResponse create(CategoryRequest request) {
        String slug = slugNormalizer.normalize(request.slug());
        if (categoryRepository.existsBySlug(slug)) {
            throw new DuplicateSlugException();
        }

        return toResponse(categoryRepository.save(Category.create(request.name(), slug, request.description())));
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        String slug = slugNormalizer.normalize(request.slug());
        categoryRepository.findBySlug(slug)
            .filter(existing -> !existing.getId().equals(category.getId()))
            .ifPresent(existing -> {
                throw new DuplicateSlugException();
            });

        category.update(request.name(), slug, request.description());
        return toResponse(categoryRepository.save(category));
    }

    public void deactivate(Long id) {
        Category category = findCategory(id);
        category.deactivate();
        categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getBySlug(String slug) {
        String normalizedSlug = slugNormalizer.normalize(slug);
        return categoryRepository.findBySlugAndActiveTrue(normalizedSlug)
            .map(this::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException(CATEGORY_NOT_FOUND_MESSAGE));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
            .map(this::toResponse)
            .toList();
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(CATEGORY_NOT_FOUND_MESSAGE));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getSlug(),
            category.getDescription(),
            category.isActive(),
            toInstant(category.getCreatedAt()),
            toInstant(category.getUpdatedAt())
        );
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
