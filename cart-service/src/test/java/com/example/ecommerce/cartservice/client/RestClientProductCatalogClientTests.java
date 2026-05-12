package com.example.ecommerce.cartservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.ecommerce.cartservice.exception.ProductCatalogUnavailableException;
import com.example.ecommerce.cartservice.exception.ProductNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientProductCatalogClientTests {

    private MockRestServiceServer server;
    private ProductCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://product-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientProductCatalogClient(builder.build());
    }

    @Test
    void getProductReturnsCatalogItemSnapshot() {
        server.expect(once(), requestTo("http://product-service/api/products/id/20"))
            .andRespond(withSuccess("""
                { "id":20, "name":"Pour Over", "price":19.99 }
                """, APPLICATION_JSON));

        ProductCatalogItem item = client.getProduct(20L);

        assertThat(item.id()).isEqualTo(20L);
        assertThat(item.name()).isEqualTo("Pour Over");
        assertThat(item.price()).isEqualByComparingTo(new BigDecimal("19.99"));
        server.verify();
    }

    @Test
    void getProductMapsNotFoundToProductNotFoundException() {
        server.expect(once(), requestTo("http://product-service/api/products/id/404"))
            .andRespond(withStatus(NOT_FOUND));

        assertThatThrownBy(() -> client.getProduct(404L))
            .isInstanceOf(ProductNotFoundException.class);
        server.verify();
    }

    @Test
    void getProductMapsServerErrorToProductCatalogUnavailableException() {
        server.expect(once(), requestTo("http://product-service/api/products/id/20"))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getProduct(20L))
            .isInstanceOf(ProductCatalogUnavailableException.class);
        server.verify();
    }

    @Test
    void getProductMapsEmptySuccessfulBodyToProductCatalogUnavailableException() {
        server.expect(once(), requestTo("http://product-service/api/products/id/20"))
            .andRespond(withSuccess("", APPLICATION_JSON));

        assertThatThrownBy(() -> client.getProduct(20L))
            .isInstanceOf(ProductCatalogUnavailableException.class);
        server.verify();
    }
}
