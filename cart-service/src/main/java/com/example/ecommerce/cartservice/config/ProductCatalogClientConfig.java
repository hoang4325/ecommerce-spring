package com.example.ecommerce.cartservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ProductCatalogClientConfig {

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient productServiceRestClient(
        RestClient.Builder loadBalancedRestClientBuilder,
        @Value("${clients.product-service.base-url}") String productServiceBaseUrl
    ) {
        return loadBalancedRestClientBuilder
            .baseUrl(productServiceBaseUrl)
            .build();
    }
}
