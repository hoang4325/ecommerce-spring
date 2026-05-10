package com.example.ecommerce.apigateway.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import com.example.ecommerce.apigateway.ApiGatewayApplication;

@SpringBootTest(
    classes = ApiGatewayApplication.class,
    properties = {
        "eureka.client.enabled=false",
        "security.jwt.secret=01234567890123456789012345678901"
    }
)
class GatewayRoutesTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void exposesExpectedServiceRoutes() {
        List<String> routeIds = routeDefinitionLocator.getRouteDefinitions()
            .map(routeDefinition -> routeDefinition.getId())
            .collectList()
            .block();

        assertThat(routeIds)
            .containsExactlyInAnyOrder(
                "auth-service",
                "user-service",
                "product-service-products",
                "product-service-categories",
                "inventory-service",
                "cart-service",
                "order-service",
                "payment-service",
                "notification-service"
            );
    }
}
