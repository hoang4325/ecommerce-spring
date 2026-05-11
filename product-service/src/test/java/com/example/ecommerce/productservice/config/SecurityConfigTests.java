package com.example.ecommerce.productservice.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.productservice.ProductServiceApplication;
import com.example.ecommerce.productservice.dto.CategoryRequest;
import com.example.ecommerce.productservice.dto.CategoryResponse;
import com.example.ecommerce.productservice.dto.ProductRequest;
import com.example.ecommerce.productservice.dto.ProductResponse;
import com.example.ecommerce.productservice.service.CategoryService;
import com.example.ecommerce.productservice.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ProductServiceApplication.class, properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:product_service_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private ProductService productService;

    @Test
    void publicProductAndCategoryGetsSucceedWithoutGatewayRoleHeaders() throws Exception {
        when(productService.getBySlug("pour-over")).thenReturn(productResponse());
        when(categoryService.listActive()).thenReturn(List.of(categoryResponse()));

        mockMvc.perform(get("/api/products/pour-over"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk());
    }

    @Test
    void adminCategoryAndProductWritesWithoutGatewayRoleHeadersAreForbidden() throws Exception {
        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(categoryRequest())))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productRequest())))
            .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService, productService);
    }

    @Test
    void adminCategoryAndProductWritesWithAdminGatewayRoleReachControllers() throws Exception {
        CategoryRequest categoryRequest = categoryRequest();
        ProductRequest productRequest = productRequest();
        when(categoryService.create(categoryRequest)).thenReturn(categoryResponse());
        when(productService.create(productRequest)).thenReturn(productResponse());

        mockMvc.perform(post("/api/categories")
                .header("X-User-Roles", " USER, ADMIN ,, ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(categoryRequest)))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/products")
                .header("X-User-Roles", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productRequest)))
            .andExpect(status().isCreated());

        verify(categoryService).create(categoryRequest);
        verify(productService).create(productRequest);
    }

    @Test
    void userGatewayRoleCannotWriteCategoryOrProduct() throws Exception {
        mockMvc.perform(post("/api/categories")
                .header("X-User-Roles", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(categoryRequest())))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/products")
                .header("X-User-Roles", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(productRequest())))
            .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService, productService);
    }

    private static CategoryRequest categoryRequest() {
        return new CategoryRequest("Coffee", "coffee", "Beans and brewers");
    }

    private static ProductRequest productRequest() {
        return new ProductRequest(
            10L,
            "Pour Over",
            "pour-over",
            "Slow brewed coffee",
            new BigDecimal("19.99"),
            "https://example.com/pour-over.jpg"
        );
    }

    private static CategoryResponse categoryResponse() {
        Instant now = Instant.parse("2026-05-11T00:00:00Z");
        return new CategoryResponse(10L, "Coffee", "coffee", "Beans and brewers", true, now, now);
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
