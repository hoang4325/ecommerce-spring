package com.example.ecommerce.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_context;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "notification.internal-token=test-internal-token"
    }
)
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
