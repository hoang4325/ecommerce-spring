# API Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the `api-gateway` Spring Boot service as the system entry point with Eureka discovery, route definitions, CORS, JWT validation, and safe identity header forwarding.

**Architecture:** The gateway is a Spring Cloud Gateway Server WebFlux application registered with Eureka. It defines static gateway routes for all planned backend services, validates JWTs with Spring Security resource server support, strips spoofed identity headers, and adds trusted identity headers after JWT validation.

**Tech Stack:** Java 21, Maven, Spring Boot 3.5.14, Spring Cloud 2025.0.2, Spring Cloud Gateway Server WebFlux, Spring Cloud Netflix Eureka Client, Spring Security WebFlux, OAuth2 Resource Server, Actuator, JUnit 5, Reactor Test, Docker Compose.

---

## Scope Check

This plan covers only the second executable slice:

- Add `api-gateway` module.
- Register it in the Maven parent.
- Configure gateway routes for planned service paths.
- Configure CORS for local frontend development.
- Validate JWTs at the gateway.
- Forward trusted identity headers after validation.
- Add Dockerfile and Docker Compose service entry.
- Update README commands for the current milestone.

This plan does not implement `auth-service`, token issuing, product APIs, databases, Kafka, Redis, or rate limiting. Rate limiting is deferred until Redis is introduced because Spring Cloud Gateway's production-ready rate limiter depends naturally on Redis.

## Reference Notes

- Official Spring Cloud Gateway documentation says the WebFlux gateway starter is `org.springframework.cloud:spring-cloud-starter-gateway-server-webflux`.
- Official Spring Cloud Gateway route examples use the `spring.cloud.gateway.routes` configuration namespace.
- Official Spring Cloud Gateway CORS documentation supports global CORS through `spring.cloud.gateway.globalcors.cors-configurations`.

## File Structure

Repository root: `D:/spring/.worktrees/api-gateway`

Files created by this plan:

- `api-gateway/pom.xml`: gateway module build.
- `api-gateway/src/main/java/com/example/ecommerce/apigateway/ApiGatewayApplication.java`: Spring Boot app entrypoint.
- `api-gateway/src/main/java/com/example/ecommerce/apigateway/config/SecurityConfig.java`: WebFlux security and JWT decoder.
- `api-gateway/src/main/java/com/example/ecommerce/apigateway/filter/JwtIdentityHeadersFilter.java`: safe identity header propagation.
- `api-gateway/src/main/resources/application.yml`: gateway routes, CORS, Eureka, actuator, JWT secret config.
- `api-gateway/src/test/java/com/example/ecommerce/apigateway/ApiGatewayApplicationTests.java`: context test.
- `api-gateway/src/test/java/com/example/ecommerce/apigateway/config/SecurityConfigTests.java`: public/protected path security tests.
- `api-gateway/src/test/java/com/example/ecommerce/apigateway/filter/JwtIdentityHeadersFilterTests.java`: identity header filter unit tests.
- `api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`: configured route IDs test.
- `api-gateway/Dockerfile`: gateway container image.

Files modified by this plan:

- `pom.xml`: add `api-gateway` module.
- `docker-compose.yml`: add `api-gateway` service.
- `README.md`: update current milestone and commands.

---

## Task 1: Gateway Module and Red Context Test

**Files:**

- Create: `D:/spring/.worktrees/api-gateway/api-gateway/pom.xml`
- Create: `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/ApiGatewayApplicationTests.java`
- Modify: `D:/spring/.worktrees/api-gateway/pom.xml`

- [ ] **Step 1: Create the gateway module build file**

Create directory `D:/spring/.worktrees/api-gateway/api-gateway`.

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.example.ecommerce</groupId>
        <artifactId>ecommerce-microservices</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>api-gateway</artifactId>
    <name>api-gateway</name>
    <description>API Gateway for the e-commerce microservices system</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Register `api-gateway` in the root parent**

Update the root `pom.xml` so the modules section is exactly:

```xml
    <modules>
        <module>eureka-server</module>
        <module>api-gateway</module>
    </modules>
```

- [ ] **Step 3: Write the failing gateway context test**

Create directory `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway`.

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/ApiGatewayApplicationTests.java`:

```java
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
```

- [ ] **Step 4: Run the failing test**

Run:

```powershell
mvn -pl api-gateway -am test
```

Expected: `COMPILATION ERROR` because `ApiGatewayApplication` does not exist yet.

- [ ] **Step 5: Keep the failing test as an uncommitted checkpoint**

Run:

```powershell
git status --short
```

Expected: output includes root `pom.xml`, `api-gateway/pom.xml`, and `ApiGatewayApplicationTests.java`.

---

## Task 2: Gateway Application, Routes, and CORS

**Files:**

- Create: `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/ApiGatewayApplication.java`
- Create: `D:/spring/.worktrees/api-gateway/api-gateway/src/main/resources/application.yml`
- Create: `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`

- [ ] **Step 1: Create the gateway application class**

Create directory `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway`.

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/ApiGatewayApplication.java`:

```java
package com.example.ecommerce.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

- [ ] **Step 2: Create gateway route and CORS configuration**

Create directory `D:/spring/.worktrees/api-gateway/api-gateway/src/main/resources`.

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins:
              - "http://localhost:3000"
              - "http://localhost:5173"
            allowed-methods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: true
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/users/**
        - id: product-service-products
          uri: lb://product-service
          predicates:
            - Path=/api/products/**
        - id: product-service-categories
          uri: lb://product-service
          predicates:
            - Path=/api/categories/**
        - id: inventory-service
          uri: lb://inventory-service
          predicates:
            - Path=/api/inventory/**
        - id: cart-service
          uri: lb://cart-service
          predicates:
            - Path=/api/cart/**
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**
        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/api/notifications/**

server:
  port: 8080

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true

security:
  jwt:
    secret: ${JWT_SECRET:01234567890123456789012345678901}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
```

- [ ] **Step 3: Add route configuration test**

Create directory `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/route`.

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`:

```java
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
```

- [ ] **Step 4: Run tests**

Run:

```powershell
mvn -pl api-gateway -am test
```

Expected: tests still fail because security configuration has not been added yet and protected path behavior is not implemented.

Do not commit yet.

---

## Task 3: JWT Security Configuration

**Files:**

- Create: `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/config/SecurityConfig.java`
- Create: `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/config/SecurityConfigTests.java`

- [ ] **Step 1: Write security behavior tests**

Create directory `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/config`.

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/config/SecurityConfigTests.java`:

```java
package com.example.ecommerce.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.example.ecommerce.apigateway.ApiGatewayApplication;

@SpringBootTest(
    classes = ApiGatewayApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.enabled=false",
        "security.jwt.secret=01234567890123456789012345678901"
    }
)
class SecurityConfigTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpointIsPublic() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk();
    }

    @Test
    void authRoutesArePublic() {
        webTestClient.post()
            .uri("/api/auth/login")
            .exchange()
            .expectStatus()
            .value(status -> assertThat(status)
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
                .isNotEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void productReadRoutesArePublic() {
        webTestClient.get()
            .uri("/api/products")
            .exchange()
            .expectStatus()
            .value(status -> assertThat(status)
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
                .isNotEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void protectedRoutesRequireAuthentication() {
        webTestClient.get()
            .uri("/api/cart")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }
}
```

- [ ] **Step 2: Run the failing security tests**

Run:

```powershell
mvn -pl api-gateway -Dtest=SecurityConfigTests test
```

Expected: fails because `SecurityConfig` and JWT decoder are not implemented.

- [ ] **Step 3: Create WebFlux security configuration**

Create directory `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/config`.

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/config/SecurityConfig.java`:

```java
package com.example.ecommerce.apigateway.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(Customizer.withDefaults())
            .authorizeExchange(exchange -> exchange
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .pathMatchers("/api/auth/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(@Value("${security.jwt.secret}") String jwtSecret) {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }
}
```

- [ ] **Step 4: Run gateway tests**

Run:

```powershell
mvn -pl api-gateway -am test
```

Expected: `BUILD SUCCESS`.

Do not commit yet because identity forwarding is still part of the gateway security slice.

---

## Task 4: Trusted Identity Header Forwarding

**Files:**

- Create: `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/filter/JwtIdentityHeadersFilter.java`
- Create: `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/filter/JwtIdentityHeadersFilterTests.java`

- [ ] **Step 1: Write identity header filter tests**

Create directory `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/filter`.

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/filter/JwtIdentityHeadersFilterTests.java`:

```java
package com.example.ecommerce.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import reactor.core.publisher.Mono;

class JwtIdentityHeadersFilterTests {

    private final JwtIdentityHeadersFilter filter = new JwtIdentityHeadersFilter();

    @Test
    void removesSpoofedIdentityHeadersWhenNoJwtIsPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/cart")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Email", "spoofed@example.com")
                .header("X-User-Roles", "ADMIN")
        );
        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            forwardedRequest.set(chainExchange.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwardedRequest.get().getHeaders();
        assertThat(headers.containsKey("X-User-Id")).isFalse();
        assertThat(headers.containsKey("X-User-Email")).isFalse();
        assertThat(headers.containsKey("X-User-Roles")).isFalse();
    }

    @Test
    void forwardsTrustedIdentityHeadersFromJwtClaims() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/cart")
                .header("X-User-Id", "spoofed-user")
        );
        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            forwardedRequest.set(chainExchange.getRequest());
            return Mono.empty();
        };
        Jwt jwt = new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "HS256"),
            Map.of(
                "sub", "user-123",
                "email", "user@example.com",
                "roles", List.of("USER", "ADMIN")
            )
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
            jwt,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        filter.filter(exchange, chain)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block();

        HttpHeaders headers = forwardedRequest.get().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-123");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(headers.getFirst("X-User-Roles")).isEqualTo("USER,ADMIN");
    }
}
```

- [ ] **Step 2: Run the failing identity header tests**

Run:

```powershell
mvn -pl api-gateway -Dtest=JwtIdentityHeadersFilterTests test
```

Expected: compilation fails because `JwtIdentityHeadersFilter` does not exist.

- [ ] **Step 3: Implement identity header filter**

Create directory `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/filter`.

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/filter/JwtIdentityHeadersFilter.java`:

```java
package com.example.ecommerce.apigateway.filter;

import java.util.Collection;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class JwtIdentityHeadersFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.remove(USER_ID_HEADER);
                headers.remove(USER_EMAIL_HEADER);
                headers.remove(USER_ROLES_HEADER);
            });

        return ReactiveSecurityContextHolder.getContext()
            .map(securityContext -> securityContext.getAuthentication())
            .filter(Authentication::isAuthenticated)
            .map(authentication -> addIdentityHeaders(requestBuilder, authentication))
            .defaultIfEmpty(requestBuilder)
            .flatMap(builder -> chain.filter(exchange.mutate().request(builder.build()).build()));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private ServerHttpRequest.Builder addIdentityHeaders(
        ServerHttpRequest.Builder requestBuilder,
        Authentication authentication
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            return requestBuilder;
        }

        Jwt jwt = jwtAuthenticationToken.getToken();
        requestBuilder.header(USER_ID_HEADER, jwt.getSubject());

        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            requestBuilder.header(USER_EMAIL_HEADER, email);
        }

        String roles = rolesAsHeader(jwt.getClaim("roles"));
        if (!roles.isBlank()) {
            requestBuilder.header(USER_ROLES_HEADER, roles);
        }

        return requestBuilder;
    }

    private String rolesAsHeader(Object rolesClaim) {
        if (rolesClaim instanceof Collection<?> roles) {
            return roles.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        }
        if (rolesClaim instanceof String roles) {
            return roles;
        }
        return "";
    }
}
```

- [ ] **Step 4: Run all gateway tests**

Run:

```powershell
mvn -pl api-gateway -am test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Package gateway and Eureka together**

Run:

```powershell
mvn -pl api-gateway -am package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit gateway module, routes, security, and tests**

Run:

```powershell
git add pom.xml api-gateway
git commit -m "feat: add api gateway"
```

Expected: commit succeeds.

---

## Task 5: Docker Compose and README Updates

**Files:**

- Create: `D:/spring/.worktrees/api-gateway/api-gateway/Dockerfile`
- Modify: `D:/spring/.worktrees/api-gateway/docker-compose.yml`
- Modify: `D:/spring/.worktrees/api-gateway/README.md`

- [ ] **Step 1: Create gateway Dockerfile**

Write this exact content to `D:/spring/.worktrees/api-gateway/api-gateway/Dockerfile`:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY eureka-server/src eureka-server/src
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY api-gateway/src api-gateway/src

RUN mvn -pl api-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/api-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: Update Docker Compose**

Replace `D:/spring/.worktrees/api-gateway/docker-compose.yml` with this exact content:

```yaml
services:
  eureka-server:
    build:
      context: .
      dockerfile: eureka-server/Dockerfile
    container_name: ecommerce-eureka-server
    ports:
      - "8761:8761"

  api-gateway:
    build:
      context: .
      dockerfile: api-gateway/Dockerfile
    container_name: ecommerce-api-gateway
    depends_on:
      - eureka-server
    environment:
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      JWT_SECRET: 01234567890123456789012345678901
    ports:
      - "8080:8080"
```

- [ ] **Step 3: Update README**

Replace `D:/spring/.worktrees/api-gateway/README.md` with this exact content:

```markdown
# E-Commerce Microservices

Java Spring Boot microservices e-commerce system.

## Current Milestone

The repository currently contains the Maven parent project, `eureka-server`, and `api-gateway`.

## Stack

- Java 21
- Spring Boot 3.5.14
- Spring Cloud 2025.0.2
- Maven
- Docker Compose
- Eureka Server
- Spring Cloud Gateway Server WebFlux
- Spring Security JWT resource server

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop or Docker Engine with Docker Compose

## Build

```powershell
mvn -pl api-gateway -am clean package
```

## Test

```powershell
mvn -pl api-gateway -am test
```

## Run Eureka Locally

```powershell
mvn -pl eureka-server spring-boot:run
```

Eureka dashboard:

```text
http://localhost:8761
```

## Run API Gateway Locally

Start Eureka first, then run:

```powershell
mvn -pl api-gateway spring-boot:run
```

Gateway health endpoint:

```text
http://localhost:8080/actuator/health
```

## Run with Docker Compose

```powershell
docker compose up --build eureka-server api-gateway
```
```

- [ ] **Step 4: Verify tests and Compose config**

Run:

```powershell
mvn -pl api-gateway -am test
```

Expected: `BUILD SUCCESS`.

Run:

```powershell
docker compose config
```

Expected: Compose file parses and includes `eureka-server` and `api-gateway`.

- [ ] **Step 5: Try Docker build if Docker daemon is available**

Run:

```powershell
docker compose build api-gateway
```

Expected when Docker is running: image build succeeds.

If Docker daemon is unavailable, record the daemon error as a concern and continue only if Maven tests and `docker compose config` pass.

- [ ] **Step 6: Commit container and README updates**

Run:

```powershell
git add api-gateway/Dockerfile docker-compose.yml README.md
git commit -m "chore: wire api gateway into local runtime"
```

Expected: commit succeeds.

---

## Task 6: Final Review and Handoff

**Files:**

- Inspect: `D:/spring/.worktrees/api-gateway/pom.xml`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/pom.xml`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/ApiGatewayApplication.java`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/config/SecurityConfig.java`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/src/main/java/com/example/ecommerce/apigateway/filter/JwtIdentityHeadersFilter.java`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/src/main/resources/application.yml`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/ApiGatewayApplicationTests.java`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/config/SecurityConfigTests.java`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/filter/JwtIdentityHeadersFilterTests.java`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`
- Inspect: `D:/spring/.worktrees/api-gateway/api-gateway/Dockerfile`
- Inspect: `D:/spring/.worktrees/api-gateway/docker-compose.yml`
- Inspect: `D:/spring/.worktrees/api-gateway/README.md`

- [ ] **Step 1: Run formatting and diff safety check**

Run:

```powershell
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 2: Run gateway tests**

Run:

```powershell
mvn -pl api-gateway -am test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Build packages**

Run:

```powershell
mvn -pl api-gateway -am package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Validate Compose**

Run:

```powershell
docker compose config
```

Expected: valid Compose output with both `eureka-server` and `api-gateway`.

- [ ] **Step 5: Confirm working tree state**

Run:

```powershell
git status --short
```

Expected: no output.

- [ ] **Step 6: Push branch**

Run:

```powershell
git push -u origin api-gateway
```

Expected: branch pushes to `origin/api-gateway`.

- [ ] **Step 7: Report completion**

In the response to the user, include:

- Files changed.
- Why each file changed.
- Commands run.
- Test results.
- Docker verification status.
- How to run locally.
- Next recommended plan: `auth-service`.

## Plan Self-Review

Spec coverage in this plan:

- Covers `api-gateway` as the single entry point.
- Adds static routes for all planned service paths.
- Adds CORS for local frontend development.
- Adds JWT validation at the gateway.
- Adds safe identity forwarding headers after JWT validation.
- Adds Dockerfile for `api-gateway`.
- Updates Docker Compose for local runtime.
- Updates README commands.
- Adds service-level tests for context, routes, security rules, and identity header forwarding.

Requirements assigned to later plans:

- JWT token issuing in `auth-service`.
- Real backend service routing once each service exists.
- Redis-backed rate limiting after Redis is introduced.
- End-to-end gateway authentication tests after `auth-service` exists.

