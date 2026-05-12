package com.example.ecommerce.productservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.productservice.dto.ProductRequest;
import com.example.ecommerce.productservice.dto.ProductResponse;
import com.example.ecommerce.productservice.exception.ResourceNotFoundException;
import com.example.ecommerce.productservice.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @Test
    void searchProductsReturnsPageAndPassesSearchParameters() throws Exception {
        PageRequest pageRequest = PageRequest.of(1, 5);
        when(productService.search("drip", "coffee", pageRequest))
            .thenReturn(new PageImpl<>(List.of(productResponse()), pageRequest, 1));

        mockMvc.perform(get("/api/products")
                .param("keyword", "drip")
                .param("categorySlug", "coffee")
                .param("page", "1")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].slug").value("pour-over"));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).search(eq("drip"), eq("coffee"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
    }

    @Test
    void productDetailReturnsProductBySlug() throws Exception {
        when(productService.getBySlug("pour-over")).thenReturn(productResponse());

        mockMvc.perform(get("/api/products/pour-over"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Pour Over"));
    }

    @Test
    void productDetailByIdReturnsProduct() throws Exception {
        when(productService.getById(20L)).thenReturn(productResponse());

        mockMvc.perform(get("/api/products/id/20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(20))
            .andExpect(jsonPath("$.name").value("Pour Over"));

        verify(productService).getById(20L);
    }

    @Test
    void createProductWithValidRequestReturnsCreated() throws Exception {
        ProductRequest request = productRequest(new BigDecimal("19.99"));
        when(productService.create(request)).thenReturn(productResponse());

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("pour-over"));
    }

    @Test
    void createProductWithInvalidPriceReturnsBadRequest() throws Exception {
        ProductRequest request = productRequest(BigDecimal.ZERO);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.details[?(@.field == 'price')]").exists());
    }

    @Test
    void missingProductReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Product not found")).when(productService).getBySlug("missing");

        mockMvc.perform(get("/api/products/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void missingProductByIdReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Product not found")).when(productService).getById(99L);

        mockMvc.perform(get("/api/products/id/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void deleteProductReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/products/20"))
            .andExpect(status().isNoContent());

        verify(productService).deactivate(20L);
    }

    private static ProductRequest productRequest(BigDecimal price) {
        return new ProductRequest(
            10L,
            "Pour Over",
            "pour-over",
            "Slow brewed coffee",
            price,
            "https://example.com/pour-over.jpg"
        );
    }

    private static ProductResponse productResponse() {
        Instant now = Instant.parse("2026-05-11T00:00:00Z");
        return new ProductResponse(
            20L,
            10L,
            "Coffee",
            "coffee",
            "Pour Over",
            "pour-over",
            "Slow brewed coffee",
            new BigDecimal("19.99"),
            "https://example.com/pour-over.jpg",
            true,
            now,
            now
        );
    }
}
