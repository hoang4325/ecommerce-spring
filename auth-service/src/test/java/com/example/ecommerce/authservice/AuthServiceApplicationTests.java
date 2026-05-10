package com.example.ecommerce.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = AuthServiceApplication.class, properties = {
    "eureka.client.enabled=false",
    "security.jwt.secret=01234567890123456789012345678901",
    "spring.datasource.url=jdbc:h2:mem:auth_service_context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
