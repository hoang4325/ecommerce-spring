package com.example.ecommerce.cartservice.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.cartservice.config.GatewayUser;
import com.example.ecommerce.cartservice.dto.AddCartItemRequest;
import com.example.ecommerce.cartservice.dto.CartItemResponse;
import com.example.ecommerce.cartservice.dto.CartResponse;
import com.example.ecommerce.cartservice.dto.UpdateCartItemRequest;
import com.example.ecommerce.cartservice.entity.CartStatus;
import com.example.ecommerce.cartservice.exception.CartItemNotFoundException;
import com.example.ecommerce.cartservice.exception.InvalidCartOperationException;
import com.example.ecommerce.cartservice.exception.ProductCatalogUnavailableException;
import com.example.ecommerce.cartservice.exception.ProductNotFoundException;
import com.example.ecommerce.cartservice.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTests {

    private static final Long USER_ID = 10L;
    private static final Long PRODUCT_ID = 20L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    @Test
    void getCartReturnsCurrentUserCart() throws Exception {
        when(cartService.getCurrentCart(USER_ID)).thenReturn(cartResponse(2));

        mockMvc.perform(get("/api/cart")
                .principal(authentication(USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(10))
            .andExpect(jsonPath("$.items[0].productId").value(20))
            .andExpect(jsonPath("$.subtotal").value(39.98));

        verify(cartService).getCurrentCart(USER_ID);
    }

    @Test
    void addItemWithValidRequestReturnsUpdatedCart() throws Exception {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, 2);
        when(cartService.addItem(USER_ID, request)).thenReturn(cartResponse(2));

        mockMvc.perform(post("/api/cart/items")
                .principal(authentication(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(2));

        verify(cartService).addItem(USER_ID, request);
    }

    @Test
    void updateItemWithValidRequestCallsServiceAndReturnsUpdatedCart() throws Exception {
        UpdateCartItemRequest request = new UpdateCartItemRequest(3);
        when(cartService.updateItem(USER_ID, PRODUCT_ID, request)).thenReturn(cartResponse(3));

        mockMvc.perform(put("/api/cart/items/{productId}", PRODUCT_ID)
                .principal(authentication(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].quantity").value(3))
            .andExpect(jsonPath("$.subtotal").value(59.97));

        verify(cartService).updateItem(USER_ID, PRODUCT_ID, request);
    }

    @Test
    void deleteItemReturnsNoContentAndCallsRemove() throws Exception {
        mockMvc.perform(delete("/api/cart/items/{productId}", PRODUCT_ID)
                .principal(authentication(USER_ID)))
            .andExpect(status().isNoContent());

        verify(cartService).removeItem(USER_ID, PRODUCT_ID);
    }

    @Test
    void deleteAllItemsReturnsNoContentAndCallsClear() throws Exception {
        mockMvc.perform(delete("/api/cart/items")
                .principal(authentication(USER_ID)))
            .andExpect(status().isNoContent());

        verify(cartService).clearCart(USER_ID);
    }

    @Test
    void addItemWithInvalidQuantityReturnsBadRequestWithQuantityDetail() throws Exception {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, 0);

        mockMvc.perform(post("/api/cart/items")
                .principal(authentication(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.details[?(@.field == 'quantity')]").exists());
    }

    @Test
    void missingCartItemMapsToNotFound() throws Exception {
        doThrow(new CartItemNotFoundException()).when(cartService).removeItem(USER_ID, PRODUCT_ID);

        mockMvc.perform(delete("/api/cart/items/{productId}", PRODUCT_ID)
                .principal(authentication(USER_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Cart item not found"));
    }

    @Test
    void productNotFoundMapsToNotFound() throws Exception {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, 1);
        doThrow(new ProductNotFoundException()).when(cartService).addItem(eq(USER_ID), eq(request));

        mockMvc.perform(post("/api/cart/items")
                .principal(authentication(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void productCatalogUnavailableMapsToServiceUnavailable() throws Exception {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, 1);
        doThrow(new ProductCatalogUnavailableException()).when(cartService).addItem(eq(USER_ID), eq(request));

        mockMvc.perform(post("/api/cart/items")
                .principal(authentication(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value("Product catalog unavailable"));
    }

    @Test
    void invalidCartOperationMapsToConflict() throws Exception {
        AddCartItemRequest request = new AddCartItemRequest(PRODUCT_ID, 1);
        doThrow(new InvalidCartOperationException("Quantity must be positive"))
            .when(cartService).addItem(eq(USER_ID), eq(request));

        mockMvc.perform(post("/api/cart/items")
                .principal(authentication(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Quantity must be positive"));
    }

    @Test
    void missingAndNonGatewayAuthenticationMapToUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cart"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing user identity"));

        Authentication stringPrincipal = new UsernamePasswordAuthenticationToken("gateway-user", null, List.of());
        mockMvc.perform(get("/api/cart")
                .principal(stringPrincipal))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Missing user identity"));
    }

    @Test
    void malformedRequestBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                .principal(authentication(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":20,\"quantity\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Malformed or missing request body"));
    }

    @Test
    void invalidPathVariableTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/cart/items/not-a-number")
                .principal(authentication(USER_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                .principal(authentication(USER_ID))
                .contentType(MediaType.TEXT_PLAIN)
                .content("productId=20&quantity=1"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.message").value("Content type is not supported"));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowedWithAllowHeader() throws Exception {
        mockMvc.perform(patch("/api/cart/items/{productId}", PRODUCT_ID)
                .principal(authentication(USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateCartItemRequest(2))))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", Matchers.allOf(
                Matchers.containsString("PUT"),
                Matchers.containsString("DELETE"))))
            .andExpect(jsonPath("$.message").value("HTTP method is not supported"));
    }

    @Test
    void accessDeniedReturnsForbidden() throws Exception {
        doThrow(new AccessDeniedException("Forbidden")).when(cartService).getCurrentCart(USER_ID);

        mockMvc.perform(get("/api/cart")
                .principal(authentication(USER_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access is denied"));
    }

    @Test
    void unexpectedExceptionReturnsInternalServerError() throws Exception {
        doThrow(new RuntimeException("Database is on fire")).when(cartService).getCurrentCart(USER_ID);

        mockMvc.perform(get("/api/cart")
                .principal(authentication(USER_ID)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Unexpected server error"));
    }

    private static Authentication authentication(Long userId) {
        GatewayUser user = new GatewayUser(userId, "customer@example.com", List.of("USER"));
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    private static CartResponse cartResponse(int quantity) {
        BigDecimal unitPrice = new BigDecimal("19.99");
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        return new CartResponse(
            100L,
            USER_ID,
            CartStatus.ACTIVE,
            List.of(new CartItemResponse(PRODUCT_ID, "Pour Over", unitPrice, quantity, lineTotal)),
            lineTotal
        );
    }
}
