package com.example.ecommerce.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:order_context;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "clients.cart-service.base-url=http://cart-service",
        "clients.inventory-service.base-url=http://inventory-service"
    }
)
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
