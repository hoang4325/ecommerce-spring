package com.example.ecommerce.apigateway.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
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
        List<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
            .collectList()
            .block();
        assertThat(routes).isNotNull();

        Map<String, RouteDefinition> routesById = routes.stream()
            .collect(Collectors.toMap(RouteDefinition::getId, routeDefinition -> routeDefinition));

        assertThat(routesById.keySet())
            .containsExactlyInAnyOrder(
                "auth-service",
                "user-service",
                "product-service-products",
                "product-service-categories",
                "inventory-service",
                "cart-service",
                "order-service",
                "order-service-admin",
                "payment-service",
                "payment-service-admin",
                "notification-service"
            );

        assertThat(pathPredicates(routesById.get("payment-service")))
            .contains("/api/payments/**");
        assertThat(pathPredicates(routesById.get("payment-service-admin")))
            .contains("/api/admin/payments/**");
    }

    private List<String> pathPredicates(RouteDefinition routeDefinition) {
        assertThat(routeDefinition).isNotNull();

        return routeDefinition.getPredicates().stream()
            .filter(predicate -> "Path".equals(predicate.getName()))
            .map(PredicateDefinition::getArgs)
            .flatMap(args -> args.values().stream())
            .toList();
    }
}
