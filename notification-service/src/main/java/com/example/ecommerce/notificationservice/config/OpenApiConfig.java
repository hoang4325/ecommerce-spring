package com.example.ecommerce.notificationservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Notification Service API")
                .version("v1")
                .description("Notification APIs for the e-commerce microservices system"));
    }
}
