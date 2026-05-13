package com.example.ecommerce.cartservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ecommerce.cartservice.client.ProductCatalogClient;
import com.example.ecommerce.cartservice.client.ProductCatalogItem;
import com.example.ecommerce.cartservice.dto.AddCartItemRequest;
import com.example.ecommerce.cartservice.dto.CartResponse;
import com.example.ecommerce.cartservice.dto.UpdateCartItemRequest;
import com.example.ecommerce.cartservice.entity.Cart;
import com.example.ecommerce.cartservice.entity.CartStatus;
import com.example.ecommerce.cartservice.exception.CartItemNotFoundException;
import com.example.ecommerce.cartservice.exception.InvalidCartOperationException;
import com.example.ecommerce.cartservice.exception.ProductCatalogUnavailableException;
import com.example.ecommerce.cartservice.exception.ProductNotFoundException;
import com.example.ecommerce.cartservice.repository.CartRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class CartServiceTests {

    private static final Long USER_ID = 10L;
    private static final Long PRODUCT_ID = 20L;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductCatalogClient productCatalogClient;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(
            cartRepository,
            productCatalogClient,
            new TransactionOperations() {
                @Override
                public <T> T execute(TransactionCallback<T> action) {
                    TransactionStatus status = new SimpleTransactionStatus();
                    return action.doInTransaction(status);
                }
            }
        );
    }

    @Test
    void getCurrentCartReturnsEmptyCartWhenNoActiveCartExists() {
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.empty());

        CartResponse response = cartService.getCurrentCart(USER_ID);

        assertThat(response.cartId()).isNull();
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(response.items()).isEmpty();
        assertThat(response.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getCurrentCartReturnsExistingActiveCartResponse() {
        Cart cart = activeCart(100L);
        cart.addOrIncrementItem(PRODUCT_ID, "Pour Over", new BigDecimal("19.99"), 2);
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));

        CartResponse response = cartService.getCurrentCart(USER_ID);

        assertThat(response.cartId()).isEqualTo(100L);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(response.subtotal()).isEqualByComparingTo("39.98");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(PRODUCT_ID);
            assertThat(item.productName()).isEqualTo("Pour Over");
            assertThat(item.unitPrice()).isEqualByComparingTo("19.99");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.lineTotal()).isEqualByComparingTo("39.98");
        });
    }

    @Test
    void addItemCreatesCartAndStoresProductSnapshot() {
        ProductCatalogItem product = new ProductCatalogItem(PRODUCT_ID, "Pour Over", new BigDecimal("19.99"));
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenReturn(product);
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.empty());
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            ReflectionTestUtils.setField(cart, "id", 100L);
            return cart;
        });

        CartResponse response = cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 2));

        assertThat(response.cartId()).isEqualTo(100L);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.status()).isEqualTo(CartStatus.ACTIVE);
        assertThat(response.subtotal()).isEqualByComparingTo("39.98");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(PRODUCT_ID);
            assertThat(item.productName()).isEqualTo("Pour Over");
            assertThat(item.unitPrice()).isEqualByComparingTo("19.99");
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.lineTotal()).isEqualByComparingTo("39.98");
        });
    }

    @Test
    void addItemIncrementsExistingProductQuantity() {
        Cart cart = activeCart(100L);
        cart.addOrIncrementItem(PRODUCT_ID, "Old Name", new BigDecimal("10.00"), 1);
        ProductCatalogItem product = new ProductCatalogItem(PRODUCT_ID, "New Name", new BigDecimal("12.50"));
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenReturn(product);
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(cart)).thenReturn(cart);

        CartResponse response = cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 3));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("New Name");
            assertThat(item.unitPrice()).isEqualByComparingTo("12.50");
            assertThat(item.quantity()).isEqualTo(4);
            assertThat(item.lineTotal()).isEqualByComparingTo("50.00");
        });
        assertThat(response.subtotal()).isEqualByComparingTo("50.00");
    }

    @Test
    void addItemRetriesWhenActiveCartCreationRaces() {
        Cart existingCart = activeCart(100L);
        ProductCatalogItem product = new ProductCatalogItem(PRODUCT_ID, "Pour Over", new BigDecimal("19.99"));
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenReturn(product);
        when(cartRepository.findByActiveCartKey(USER_ID))
            .thenReturn(Optional.empty(), Optional.of(existingCart));
        when(cartRepository.saveAndFlush(any(Cart.class)))
            .thenThrow(new DataIntegrityViolationException("active cart already exists"))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 2));

        assertThat(response.cartId()).isEqualTo(100L);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(PRODUCT_ID);
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.lineTotal()).isEqualByComparingTo("39.98");
        });
        assertThat(response.subtotal()).isEqualByComparingTo("39.98");
        verify(cartRepository, times(2)).findByActiveCartKey(USER_ID);
        verify(cartRepository, times(2)).saveAndFlush(any(Cart.class));
    }

    @Test
    void updateItemUpdatesQuantityAndSnapshot() {
        Cart cart = activeCart(100L);
        cart.addOrIncrementItem(PRODUCT_ID, "Old Name", new BigDecimal("10.00"), 1);
        ProductCatalogItem product = new ProductCatalogItem(PRODUCT_ID, "New Name", new BigDecimal("12.50"));
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenReturn(product);
        when(cartRepository.save(cart)).thenReturn(cart);

        CartResponse response = cartService.updateItem(USER_ID, PRODUCT_ID, new UpdateCartItemRequest(5));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("New Name");
            assertThat(item.unitPrice()).isEqualByComparingTo("12.50");
            assertThat(item.quantity()).isEqualTo(5);
            assertThat(item.lineTotal()).isEqualByComparingTo("62.50");
        });
        assertThat(response.subtotal()).isEqualByComparingTo("62.50");
    }

    @Test
    void updateItemRejectsMissingItemWithoutCallingProductCatalog() {
        Cart cart = activeCart(100L);
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.updateItem(USER_ID, PRODUCT_ID, new UpdateCartItemRequest(5)))
            .isInstanceOf(CartItemNotFoundException.class);

        verifyNoInteractions(productCatalogClient);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateItemRejectsMissingCartWithoutCallingProductCatalog() {
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(USER_ID, PRODUCT_ID, new UpdateCartItemRequest(5)))
            .isInstanceOf(CartItemNotFoundException.class);

        verifyNoInteractions(productCatalogClient);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateItemPropagatesProductCatalogUnavailable() {
        Cart cart = activeCart(100L);
        cart.addOrIncrementItem(PRODUCT_ID, "Old Name", new BigDecimal("10.00"), 1);
        ProductCatalogUnavailableException exception = new ProductCatalogUnavailableException();
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenThrow(exception);

        assertThatThrownBy(() -> cartService.updateItem(USER_ID, PRODUCT_ID, new UpdateCartItemRequest(5)))
            .isSameAs(exception);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateItemPropagatesProductNotFound() {
        Cart cart = activeCart(100L);
        cart.addOrIncrementItem(PRODUCT_ID, "Old Name", new BigDecimal("10.00"), 1);
        ProductNotFoundException exception = new ProductNotFoundException();
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenThrow(exception);

        assertThatThrownBy(() -> cartService.updateItem(USER_ID, PRODUCT_ID, new UpdateCartItemRequest(5)))
            .isSameAs(exception);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void removeItemRejectsMissingItem() {
        Cart cart = activeCart(100L);
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, PRODUCT_ID))
            .isInstanceOf(CartItemNotFoundException.class);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void removeItemRejectsMissingCart() {
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, PRODUCT_ID))
            .isInstanceOf(CartItemNotFoundException.class);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void removeItemRemovesExistingItemAndSavesCart() {
        Cart cart = activeCart(100L);
        cart.addOrIncrementItem(PRODUCT_ID, "Pour Over", new BigDecimal("19.99"), 2);
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);

        cartService.removeItem(USER_ID, PRODUCT_ID);

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).save(cart);
    }

    @Test
    void updateItemDomainInvalidOperationMapsToInvalidCartOperation() {
        Cart cart = activeCart(100L);
        cart.addOrIncrementItem(PRODUCT_ID, "Old Name", new BigDecimal("10.00"), 1);
        ProductCatalogItem product = new ProductCatalogItem(PRODUCT_ID, "New Name", new BigDecimal("12.50"));
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenReturn(product);

        assertThatThrownBy(() -> cartService.updateItem(USER_ID, PRODUCT_ID, new UpdateCartItemRequest(0)))
            .isInstanceOf(InvalidCartOperationException.class)
            .hasMessage("Quantity must be positive");

        verify(cartRepository, never()).save(any());
    }

    @Test
    void clearCartIsIdempotentWhenNoCartExists() {
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.empty());

        cartService.clearCart(USER_ID);

        verify(cartRepository, never()).save(any());
    }

    @Test
    void clearCartClearsExistingCartAndSaves() {
        Cart cart = activeCart(100L);
        cart.addOrIncrementItem(PRODUCT_ID, "Pour Over", new BigDecimal("19.99"), 2);
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);

        cartService.clearCart(USER_ID);

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).save(cart);
    }

    @Test
    void productCatalogUnavailablePropagatesAsProductCatalogUnavailable() {
        ProductCatalogUnavailableException exception = new ProductCatalogUnavailableException();
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenThrow(exception);

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 1)))
            .isSameAs(exception);

        verify(cartRepository, never()).findByActiveCartKey(any());
        verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    void productNotFoundPropagatesAsProductNotFound() {
        ProductNotFoundException exception = new ProductNotFoundException();
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenThrow(exception);

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 1)))
            .isSameAs(exception);

        verify(cartRepository, never()).findByActiveCartKey(any());
        verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    void domainInvalidOperationMapsToInvalidCartOperation() {
        ProductCatalogItem product = new ProductCatalogItem(PRODUCT_ID, "Pour Over", new BigDecimal("19.99"));
        when(productCatalogClient.getProduct(PRODUCT_ID)).thenReturn(product);
        when(cartRepository.findByActiveCartKey(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(USER_ID, new AddCartItemRequest(PRODUCT_ID, 0)))
            .isInstanceOf(InvalidCartOperationException.class)
            .hasMessage("Quantity must be positive");

        verify(cartRepository, never()).saveAndFlush(any());
    }

    private static Cart activeCart(Long cartId) {
        Cart cart = Cart.createActive(USER_ID);
        ReflectionTestUtils.setField(cart, "id", cartId);
        return cart;
    }
}
