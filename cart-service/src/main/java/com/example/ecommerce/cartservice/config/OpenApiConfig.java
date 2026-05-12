package com.example.ecommerce.cartservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI cartServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Cart Service API")
                .version("0.0.1"));
    }
}
