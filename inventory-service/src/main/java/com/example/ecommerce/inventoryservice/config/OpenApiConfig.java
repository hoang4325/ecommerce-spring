package com.example.ecommerce.inventoryservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI inventoryServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Inventory Service API")
                .version("v1"));
    }
}
