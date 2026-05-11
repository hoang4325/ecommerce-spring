package com.example.ecommerce.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ecommerce.productservice.dto.CategoryRequest;
import com.example.ecommerce.productservice.dto.CategoryResponse;
import com.example.ecommerce.productservice.entity.Category;
import com.example.ecommerce.productservice.exception.DuplicateSlugException;
import com.example.ecommerce.productservice.exception.ResourceNotFoundException;
import com.example.ecommerce.productservice.repository.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTests {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SlugNormalizer slugNormalizer;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createNormalizesSlugSavesCategoryAndReturnsResponse() {
        CategoryRequest request = new CategoryRequest("Coffee", " Coffee-Gear ", "Brewing gear");
        when(slugNormalizer.normalize(" Coffee-Gear ")).thenReturn("coffee-gear");
        when(categoryRepository.existsBySlug("coffee-gear")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            ReflectionTestUtils.setField(category, "id", 10L);
            return category;
        });

        CategoryResponse response = categoryService.create(request);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        Category saved = categoryCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("Coffee");
        assertThat(saved.getSlug()).isEqualTo("coffee-gear");
        assertThat(saved.getDescription()).isEqualTo("Brewing gear");
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.slug()).isEqualTo("coffee-gear");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createRejectsDuplicateSlug() {
        CategoryRequest request = new CategoryRequest("Coffee", "coffee", "Brewing gear");
        when(slugNormalizer.normalize("coffee")).thenReturn("coffee");
        when(categoryRepository.existsBySlug("coffee")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(request))
            .isInstanceOf(DuplicateSlugException.class)
            .hasMessage("Slug is already in use");
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updateRejectsDuplicateSlugOwnedByAnotherCategory() {
        Category current = categoryWithId(1L, "Coffee", "coffee", true);
        Category other = categoryWithId(2L, "Tea", "tea", true);
        CategoryRequest request = new CategoryRequest("Coffee", "tea", "Updated");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(current));
        when(slugNormalizer.normalize("tea")).thenReturn("tea");
        when(categoryRepository.findBySlug("tea")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> categoryService.update(1L, request))
            .isInstanceOf(DuplicateSlugException.class)
            .hasMessage("Slug is already in use");
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void deactivateMarksCategoryInactive() {
        Category category = categoryWithId(1L, "Coffee", "coffee", true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.deactivate(1L);

        assertThat(category.isActive()).isFalse();
        verify(categoryRepository).save(category);
    }

    @Test
    void getBySlugRejectsInactiveOrMissingCategory() {
        when(slugNormalizer.normalize(" Coffee ")).thenReturn("coffee");
        when(categoryRepository.findBySlugAndActiveTrue("coffee")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getBySlug(" Coffee "))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Category not found");
    }

    @Test
    void listActiveReturnsActiveCategoryResponses() {
        Category coffee = categoryWithId(1L, "Coffee", "coffee", true);
        when(categoryRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(coffee));

        assertThat(categoryService.listActive())
            .extracting(CategoryResponse::slug)
            .containsExactly("coffee");
    }

    private static Category categoryWithId(Long id, String name, String slug, boolean active) {
        Category category = Category.create(name, slug, "Description");
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "active", active);
        return category;
    }
}
