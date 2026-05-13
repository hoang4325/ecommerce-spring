package com.example.ecommerce.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class RestClientConfig {

    @Bean
    @LoadBalanced
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
}
