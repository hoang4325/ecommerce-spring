package com.example.ecommerce.inventoryservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.inventoryservice.dto.InventoryAdjustmentRequest;
import com.example.ecommerce.inventoryservice.dto.InventoryItemResponse;
import com.example.ecommerce.inventoryservice.dto.StockLevelRequest;
import com.example.ecommerce.inventoryservice.exception.InvalidStockOperationException;
import com.example.ecommerce.inventoryservice.exception.ResourceNotFoundException;
import com.example.ecommerce.inventoryservice.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void listInventoryItemsReturnsPageAndPassesPageable() throws Exception {
        PageRequest pageRequest = PageRequest.of(1, 5);
        when(inventoryService.list(pageRequest))
            .thenReturn(new PageImpl<>(List.of(inventoryItemResponse()), pageRequest, 1));

        mockMvc.perform(get("/api/inventory/items")
                .param("page", "1")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].productId").value(10));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryService).list(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getItemReturnsInventoryItem() throws Exception {
        when(inventoryService.getByProductId(10L)).thenReturn(inventoryItemResponse());

        mockMvc.perform(get("/api/inventory/items/10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableQuantity").value(25));
    }

    @Test
    void missingItemReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Inventory item not found"))
            .when(inventoryService).getByProductId(10L);

        mockMvc.perform(get("/api/inventory/items/10"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Inventory item not found"));
    }

    @Test
    void setStockWithValidRequestReturnsItem() throws Exception {
        StockLevelRequest request = new StockLevelRequest(30);
        when(inventoryService.setStock(10L, request)).thenReturn(inventoryItemResponse(30, 0));

        mockMvc.perform(put("/api/inventory/items/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableQuantity").value(30));
    }

    @Test
    void setStockWithInvalidQuantityReturnsBadRequest() throws Exception {
        StockLevelRequest request = new StockLevelRequest(-1);

        mockMvc.perform(put("/api/inventory/items/10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.details[?(@.field == 'availableQuantity')]").exists());
    }

    @Test
    void adjustStockWithValidRequestReturnsItem() throws Exception {
        InventoryAdjustmentRequest request = new InventoryAdjustmentRequest(5);
        when(inventoryService.adjustStock(10L, request)).thenReturn(inventoryItemResponse(30, 0));

        mockMvc.perform(post("/api/inventory/items/10/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableQuantity").value(30));
    }

    @Test
    void invalidStockOperationReturnsConflict() throws Exception {
        InventoryAdjustmentRequest request = new InventoryAdjustmentRequest(-30);
        doThrow(new InvalidStockOperationException("Available quantity must not be negative"))
            .when(inventoryService).adjustStock(eq(10L), eq(request));

        mockMvc.perform(post("/api/inventory/items/10/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Available quantity must not be negative"));
    }

    private static InventoryItemResponse inventoryItemResponse() {
        return inventoryItemResponse(25, 0);
    }

    private static InventoryItemResponse inventoryItemResponse(int availableQuantity, int reservedQuantity) {
        Instant now = Instant.parse("2026-05-11T00:00:00Z");
        return new InventoryItemResponse(1L, 10L, availableQuantity, reservedQuantity, now, now);
    }
}
