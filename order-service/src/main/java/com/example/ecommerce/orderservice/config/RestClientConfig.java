package com.example.ecommerce.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

@Configuration
class RestClientConfig {

    @Bean
    @LoadBalanced
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient cartServiceRestClient(
        RestClient.Builder loadBalancedRestClientBuilder,
        @Value("${clients.cart-service.base-url}") String cartServiceBaseUrl
    ) {
        return loadBalancedRestClientBuilder
            .baseUrl(cartServiceBaseUrl)
            .build();
    }

    @Bean
    RestClient inventoryServiceRestClient(
        RestClient.Builder loadBalancedRestClientBuilder,
        @Value("${clients.inventory-service.base-url}") String inventoryServiceBaseUrl
    ) {
        return loadBalancedRestClientBuilder
            .baseUrl(inventoryServiceBaseUrl)
            .build();
    }
}
