package com.example.ecommerce.inventoryservice.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.inventoryservice.dto.ReservationItemRequest;
import com.example.ecommerce.inventoryservice.dto.ReserveStockRequest;
import com.example.ecommerce.inventoryservice.dto.StockReservationResponse;
import com.example.ecommerce.inventoryservice.dto.StockReservationResultResponse;
import com.example.ecommerce.inventoryservice.entity.ReservationStatus;
import com.example.ecommerce.inventoryservice.exception.DuplicateReservationException;
import com.example.ecommerce.inventoryservice.service.StockReservationService;
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

@WebMvcTest(StockReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockReservationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StockReservationService stockReservationService;

    @Test
    void reserveStockWithValidRequestReturnsReservationResult() throws Exception {
        ReserveStockRequest request = reserveStockRequest();
        when(stockReservationService.reserve(request)).thenReturn(reservationResult(ReservationStatus.RESERVED));

        mockMvc.perform(post("/api/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESERVED"))
            .andExpect(jsonPath("$.reservations[0].productId").value(10));
    }

    @Test
    void reserveStockWithEmptyItemsReturnsBadRequest() throws Exception {
        ReserveStockRequest request = new ReserveStockRequest(1001L, List.of());

        mockMvc.perform(post("/api/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.details[?(@.field == 'items')]").exists());
    }

    @Test
    void duplicateReservationReturnsConflict() throws Exception {
        ReserveStockRequest request = reserveStockRequest();
        doThrow(new DuplicateReservationException()).when(stockReservationService).reserve(request);

        mockMvc.perform(post("/api/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Order already has an active reservation"));
    }

    @Test
    void releaseReservationReturnsReservationResult() throws Exception {
        when(stockReservationService.release(1001L)).thenReturn(reservationResult(ReservationStatus.RELEASED));

        mockMvc.perform(post("/api/inventory/reservations/1001/release"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    void deductReservationReturnsReservationResult() throws Exception {
        when(stockReservationService.deduct(1001L)).thenReturn(reservationResult(ReservationStatus.DEDUCTED));

        mockMvc.perform(post("/api/inventory/reservations/1001/deduct"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEDUCTED"));
    }

    private static ReserveStockRequest reserveStockRequest() {
        return new ReserveStockRequest(1001L, List.of(new ReservationItemRequest(10L, 2)));
    }

    private static StockReservationResultResponse reservationResult(ReservationStatus status) {
        Instant now = Instant.parse("2026-05-11T00:00:00Z");
        return new StockReservationResultResponse(
            1001L,
            status,
            List.of(new StockReservationResponse(1L, 1001L, 10L, 2, status, null, now, now))
        );
    }
}
