package com.example.ecommerce.productservice.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.productservice.dto.CategoryRequest;
import com.example.ecommerce.productservice.dto.CategoryResponse;
import com.example.ecommerce.productservice.exception.DuplicateSlugException;
import com.example.ecommerce.productservice.exception.ResourceNotFoundException;
import com.example.ecommerce.productservice.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void listCategoriesReturnsActiveCategories() throws Exception {
        when(categoryService.listActive()).thenReturn(List.of(categoryResponse()));

        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("coffee"));
    }

    @Test
    void categoryDetailReturnsCategoryBySlug() throws Exception {
        when(categoryService.getBySlug("coffee")).thenReturn(categoryResponse());

        mockMvc.perform(get("/api/categories/coffee"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Coffee"));
    }

    @Test
    void createCategoryWithValidRequestReturnsCreated() throws Exception {
        CategoryRequest request = categoryRequest("Coffee", "coffee");
        when(categoryService.create(request)).thenReturn(categoryResponse());

        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("coffee"));
    }

    @Test
    void createCategoryWithInvalidRequestReturnsFieldDetails() throws Exception {
        CategoryRequest request = categoryRequest("", "Coffee!");

        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.details[?(@.field == 'name')]").exists())
            .andExpect(jsonPath("$.details[?(@.field == 'slug')]").exists());
    }

    @Test
    void duplicateCategorySlugReturnsConflict() throws Exception {
        CategoryRequest request = categoryRequest("Coffee", "coffee");
        when(categoryService.create(request)).thenThrow(new DuplicateSlugException());

        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Slug is already in use"));
    }

    @Test
    void missingCategoryReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Category not found")).when(categoryService).getBySlug("missing");

        mockMvc.perform(get("/api/categories/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Category not found"));
    }

    private static CategoryRequest categoryRequest(String name, String slug) {
        return new CategoryRequest(name, slug, "Beans and brewers");
    }

    private static CategoryResponse categoryResponse() {
        Instant now = Instant.parse("2026-05-11T00:00:00Z");
        return new CategoryResponse(10L, "Coffee", "coffee", "Beans and brewers", true, now, now);
    }
}
