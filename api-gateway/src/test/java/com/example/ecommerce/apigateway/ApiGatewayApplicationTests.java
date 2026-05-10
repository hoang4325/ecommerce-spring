package com.example.ecommerce.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = ApiGatewayApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.enabled=false",
        "security.jwt.secret=01234567890123456789012345678901"
    }
)
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
