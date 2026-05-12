package com.example.ecommerce.cartservice.client;

import com.example.ecommerce.cartservice.exception.ProductCatalogUnavailableException;
import com.example.ecommerce.cartservice.exception.ProductNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestClientProductCatalogClient implements ProductCatalogClient {

    private final RestClient productServiceRestClient;

    public RestClientProductCatalogClient(RestClient productServiceRestClient) {
        this.productServiceRestClient = productServiceRestClient;
    }

    @Override
    public ProductCatalogItem getProduct(Long productId) {
        try {
            ProductCatalogItem item = productServiceRestClient.get()
                .uri("/api/products/id/{id}", productId)
                .retrieve()
                .body(ProductCatalogItem.class);

            if (item == null) {
                throw new ProductCatalogUnavailableException();
            }

            return item;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException();
        } catch (RestClientException ex) {
            throw new ProductCatalogUnavailableException();
        }
    }
}
