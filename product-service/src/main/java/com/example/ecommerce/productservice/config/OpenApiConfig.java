package com.example.ecommerce.productservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI productServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Product Service API")
                .version("v1"));
    }
}
