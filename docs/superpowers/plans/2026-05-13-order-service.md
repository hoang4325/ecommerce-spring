# Order Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the MVP `order-service` that creates orders from cart snapshots, reserves stock through inventory-service, exposes user/admin order APIs, and runs locally through Docker Compose.

**Architecture:** `order-service` is a Spring Boot MVC/JPA service with its own PostgreSQL database and no shared entities. Checkout reads cart snapshots through a small REST client, persists order snapshots, reserves stock through a second REST client, and exposes scoped user/admin APIs behind gateway identity headers. Kafka, payment, notification, and cart clearing remain outside this branch.

**Tech Stack:** Java 21, Spring Boot 3.5.14, Spring Web MVC, Spring Security, Spring Data JPA, PostgreSQL, H2 tests, Eureka client, Springdoc OpenAPI, Maven, Docker Compose.

---

## File Structure

Create:

- `order-service/pom.xml`: service module dependencies and build plugin.
- `order-service/Dockerfile`: multi-module Docker build for order-service.
- `order-service/src/main/java/com/example/ecommerce/orderservice/OrderServiceApplication.java`: Spring Boot entrypoint.
- `order-service/src/main/resources/application.yml`: datasource, Eureka, client base URLs, actuator, Springdoc.
- `order-service/src/main/java/com/example/ecommerce/orderservice/entity/Order.java`: aggregate root and status transitions.
- `order-service/src/main/java/com/example/ecommerce/orderservice/entity/OrderItem.java`: order item snapshot entity.
- `order-service/src/main/java/com/example/ecommerce/orderservice/entity/OrderStatus.java`: order status enum.
- `order-service/src/main/java/com/example/ecommerce/orderservice/repository/OrderRepository.java`: order queries.
- `order-service/src/main/java/com/example/ecommerce/orderservice/dto/OrderItemResponse.java`: item response DTO.
- `order-service/src/main/java/com/example/ecommerce/orderservice/dto/OrderResponse.java`: order response DTO.
- `order-service/src/main/java/com/example/ecommerce/orderservice/dto/UpdateOrderStatusRequest.java`: admin status change request.
- `order-service/src/main/java/com/example/ecommerce/orderservice/config/GatewayUser.java`: authenticated gateway identity.
- `order-service/src/main/java/com/example/ecommerce/orderservice/config/GatewayIdentityAuthenticationFilter.java`: trust gateway identity headers.
- `order-service/src/main/java/com/example/ecommerce/orderservice/config/SecurityConfig.java`: endpoint authorization and JSON auth entry point.
- `order-service/src/main/java/com/example/ecommerce/orderservice/config/OpenApiConfig.java`: Swagger metadata.
- `order-service/src/main/java/com/example/ecommerce/orderservice/config/RestClientConfig.java`: load-balanced clients.
- `order-service/src/main/java/com/example/ecommerce/orderservice/exception/ApiErrorResponse.java`: standard error body.
- `order-service/src/main/java/com/example/ecommerce/orderservice/exception/GlobalExceptionHandler.java`: error mapping.
- `order-service/src/main/java/com/example/ecommerce/orderservice/exception/CartServiceUnavailableException.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/exception/EmptyCartException.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/exception/InventoryReservationFailedException.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/exception/InventoryServiceUnavailableException.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/exception/InvalidOrderOperationException.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/exception/MissingUserIdentityException.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/exception/OrderNotFoundException.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/cart/CartClient.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/cart/CartItemSnapshot.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/cart/CartSnapshot.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/cart/RestClientCartClient.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationClient.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationItem.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationRequest.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationResult.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationStatus.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/RestClientInventoryReservationClient.java`
- `order-service/src/main/java/com/example/ecommerce/orderservice/service/OrderService.java`
- Tests under `order-service/src/test/java/com/example/ecommerce/orderservice/**`.

Modify:

- `pom.xml`: add `<module>order-service</module>`.
- `.github/workflows/ci.yml`: include order-service in module test/build matrix or Maven command.
- `docker-compose.yml`: add `order-postgres`, `order-service`, volume, gateway dependency.
- `README.md`: document order-service endpoints and local run commands.
- `api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`: assert order route remains valid if current tests need service list updates.
- `*/Dockerfile` for existing service modules: copy `order-service/pom.xml` so Docker builds with `-am` stay valid.
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/SecurityConfig.java`: allow `SERVICE` role for reservation endpoints.
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/GatewayIdentityAuthenticationFilter.java`: clear security context in `finally` if not already done while touching the file.
- `inventory-service/src/test/java/com/example/ecommerce/inventoryservice/config/SecurityConfigTests.java`: cover reservation service role.

---

### Task 1: Module Skeleton

**Files:**

- Modify: `pom.xml`
- Create: `order-service/pom.xml`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/OrderServiceApplication.java`
- Create: `order-service/src/main/resources/application.yml`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/OrderServiceApplicationTests.java`

- [ ] **Step 1: Write failing application context test**

Create `order-service/src/test/java/com/example/ecommerce/orderservice/OrderServiceApplicationTests.java`:

```java
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
```

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
mvn -pl order-service test
```

Expected: FAIL because module `order-service` does not exist in the Maven reactor.

- [ ] **Step 3: Add module POM and app skeleton**

Modify root `pom.xml` modules:

```xml
<modules>
    <module>eureka-server</module>
    <module>api-gateway</module>
    <module>auth-service</module>
    <module>product-service</module>
    <module>inventory-service</module>
    <module>cart-service</module>
    <module>order-service</module>
</modules>
```

Create `order-service/pom.xml` using the same dependency set as `cart-service/pom.xml`, with artifact id and description changed:

```xml
<artifactId>order-service</artifactId>
<name>order-service</name>
<description>Order service for the e-commerce microservices system</description>
```

Create `order-service/src/main/java/com/example/ecommerce/orderservice/OrderServiceApplication.java`:

```java
package com.example.ecommerce.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

Create `order-service/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: order-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5436/order_db}
    username: ${SPRING_DATASOURCE_USERNAME:ecommerce}
    password: ${SPRING_DATASOURCE_PASSWORD:ecommerce}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

server:
  port: 8085

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true

clients:
  cart-service:
    base-url: ${CART_SERVICE_BASE_URL:http://cart-service}
  inventory-service:
    base-url: ${INVENTORY_SERVICE_BASE_URL:http://inventory-service}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true

springdoc:
  swagger-ui:
    path: /swagger-ui.html

---
spring:
  config:
    activate:
      on-profile: local
  jpa:
    hibernate:
      ddl-auto: update
```

- [ ] **Step 4: Run test to verify GREEN**

Run:

```powershell
mvn -pl order-service test
```

Expected: PASS with 1 test.

- [ ] **Step 5: Commit**

```powershell
git add pom.xml order-service
git commit -m "feat: add order service module skeleton"
```

---

### Task 2: Order Domain And Persistence

**Files:**

- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/entity/OrderStatus.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/entity/OrderItem.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/entity/Order.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/repository/OrderRepository.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/entity/OrderTests.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/repository/OrderRepositoryTests.java`

- [ ] **Step 1: Write failing domain tests**

Create `OrderTests` with these tests:

```java
@Test
void createFromCartSnapshotsComputesLineTotalsAndSubtotal() {
    Order order = Order.createFromCart(10L, 20L, List.of(
        OrderItem.create(100L, "Pour Over", new BigDecimal("19.99"), 2),
        OrderItem.create(101L, "Filters", new BigDecimal("4.50"), 3)
    ));

    assertThat(order.getUserId()).isEqualTo(10L);
    assertThat(order.getSourceCartId()).isEqualTo(20L);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getSubtotal()).isEqualByComparingTo("53.48");
    assertThat(order.getItems()).extracting(OrderItem::getLineTotal)
        .containsExactly(new BigDecimal("39.98"), new BigDecimal("13.50"));
}

@Test
void createFromCartRejectsEmptyItems() {
    assertThatThrownBy(() -> Order.createFromCart(10L, 20L, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Order must contain at least one item");
}

@Test
void orderItemRejectsInvalidQuantityAndPrice() {
    assertThatThrownBy(() -> OrderItem.create(100L, "Pour Over", BigDecimal.ONE, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Quantity must be positive");

    assertThatThrownBy(() -> OrderItem.create(100L, "Pour Over", new BigDecimal("-0.01"), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unit price must be zero or positive");
}

@Test
void cancelRejectsTerminalOrders() {
    Order order = Order.createFromCart(10L, 20L, List.of(
        OrderItem.create(100L, "Pour Over", new BigDecimal("19.99"), 1)
    ));
    order.cancel("Stock reservation failed");

    assertThatThrownBy(() -> order.cancel("Second cancellation"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Terminal order cannot be changed");
}
```

- [ ] **Step 2: Run domain tests to verify RED**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderTests" test
```

Expected: FAIL because `Order`, `OrderItem`, and `OrderStatus` do not exist.

- [ ] **Step 3: Implement domain entities**

Implement `OrderStatus`:

```java
public enum OrderStatus {
    PENDING,
    STOCK_RESERVED,
    PAYMENT_PENDING,
    COMPLETED,
    CANCELLED
}
```

Implement `OrderItem` with:

- `@Entity`
- table `order_items`
- unique constraint on `order_id, product_id`
- fields from spec
- static factory `create(Long productId, String productName, BigDecimal unitPrice, int quantity)`
- private `calculateLineTotal`
- `@PrePersist` and `@PreUpdate` timestamps.

Implement `Order` with:

- `@Entity`
- table `orders`
- index on `user_id, source_cart_id`
- one-to-many `items` with cascade and orphan removal
- static factory `createFromCart(Long userId, Long sourceCartId, List<OrderItem> items)`
- `markStockReserved()`
- `cancel(String reason)`
- `isTerminal()`
- `recalculateSubtotal()`
- `@PrePersist` and `@PreUpdate` timestamps.

- [ ] **Step 4: Run domain tests to verify GREEN**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderTests" test
```

Expected: PASS.

- [ ] **Step 5: Write failing repository tests**

Create `OrderRepositoryTests` using `@DataJpaTest` and H2 PostgreSQL mode. Include:

```java
@Test
void persistsOrderWithItems() {
    Order order = repository.saveAndFlush(sampleOrder(10L, 20L));

    assertThat(order.getId()).isNotNull();
    assertThat(repository.findById(order.getId()).orElseThrow().getItems()).hasSize(1);
}

@Test
void findsByIdAndUserId() {
    Order order = repository.saveAndFlush(sampleOrder(10L, 20L));

    assertThat(repository.findByIdAndUserId(order.getId(), 10L)).isPresent();
    assertThat(repository.findByIdAndUserId(order.getId(), 11L)).isEmpty();
}

@Test
void findsExistingNonTerminalOrderByUserAndCart() {
    Order pending = repository.saveAndFlush(sampleOrder(10L, 20L));
    Order cancelled = sampleOrder(10L, 21L);
    cancelled.cancel("Stock failed");
    repository.saveAndFlush(cancelled);

    assertThat(repository.findFirstByUserIdAndSourceCartIdAndStatusNotIn(
        10L,
        20L,
        List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
    )).contains(pending);
}

@Test
void filtersByStatusNewestFirst() {
    repository.saveAndFlush(sampleOrder(10L, 20L));
    Order reserved = sampleOrder(11L, 21L);
    reserved.markStockReserved();
    repository.saveAndFlush(reserved);

    Page<Order> result = repository.findByStatus(
        OrderStatus.STOCK_RESERVED,
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
    );

    assertThat(result.getContent()).extracting(Order::getStatus)
        .containsOnly(OrderStatus.STOCK_RESERVED);
}
```

- [ ] **Step 6: Run repository tests to verify RED**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderRepositoryTests" test
```

Expected: FAIL because `OrderRepository` methods do not exist.

- [ ] **Step 7: Implement repository**

Create `OrderRepository`:

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Optional<Order> findFirstByUserIdAndSourceCartIdAndStatusNotIn(
        Long userId,
        Long sourceCartId,
        Collection<OrderStatus> statuses
    );
}
```

- [ ] **Step 8: Run domain and repository tests**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderTests,OrderRepositoryTests" test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add order-service/src/main/java/com/example/ecommerce/orderservice/entity order-service/src/main/java/com/example/ecommerce/orderservice/repository order-service/src/test/java/com/example/ecommerce/orderservice/entity order-service/src/test/java/com/example/ecommerce/orderservice/repository
git commit -m "feat: add order domain persistence"
```

---

### Task 3: DTOs, Exceptions, Security, And OpenAPI

**Files:**

- Create DTO, config, and exception files listed in File Structure.
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/dto/OrderResponseTests.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/dto/UpdateOrderStatusRequestValidationTests.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/config/GatewayUserTests.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/config/SecurityConfigTests.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/config/OpenApiEndpointTests.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/exception/ApiErrorResponseTests.java`

- [ ] **Step 1: Write failing DTO and error response tests**

Use test names:

- `orderResponseCopiesItemsDefensively`
- `apiErrorResponseCopiesDetailsDefensively`
- `updateStatusRequestRejectsTooLongReason`
- `gatewayUserCopiesAndNormalizesRoles`

Expected assertions:

```java
assertThat(response.items()).containsExactly(item);
assertThatThrownBy(() -> response.items().add(item)).isInstanceOf(UnsupportedOperationException.class);
assertThat(new GatewayUser(10L, "user@example.com", List.of("ROLE_ADMIN", "USER")).roles())
    .containsExactly("ADMIN", "USER");
```

- [ ] **Step 2: Run DTO/config tests to verify RED**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderResponseTests,UpdateOrderStatusRequestValidationTests,GatewayUserTests,ApiErrorResponseTests" test
```

Expected: FAIL because DTO/config/error classes do not exist.

- [ ] **Step 3: Implement DTOs and error classes**

Create immutable records:

```java
public record OrderItemResponse(
    Long productId,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal lineTotal
) {
}

public record OrderResponse(
    Long orderId,
    Long userId,
    Long sourceCartId,
    OrderStatus status,
    List<OrderItemResponse> items,
    BigDecimal subtotal,
    String cancellationReason,
    Instant createdAt,
    Instant updatedAt
) {
    public OrderResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

public record UpdateOrderStatusRequest(
    @NotNull OrderStatus status,
    @Size(max = 500) String reason
) {
}
```

Create `ApiErrorResponse` with `List.copyOf(details)` and nested `FieldErrorDetail`.

Create exceptions with exact messages:

- `CartServiceUnavailableException`: `Cart service unavailable`
- `EmptyCartException`: `Cart is empty`
- `InventoryReservationFailedException`: accepts message, default `Stock reservation failed`
- `InventoryServiceUnavailableException`: `Inventory service unavailable`
- `InvalidOrderOperationException`: message constructor
- `MissingUserIdentityException`: `Missing user identity`
- `OrderNotFoundException`: `Order not found`

- [ ] **Step 4: Implement gateway identity and security**

Create `GatewayUser`:

```java
public record GatewayUser(Long id, String email, List<String> roles) {
    public GatewayUser {
        roles = roles == null ? List.of() : roles.stream()
            .map(role -> role == null ? "" : role.trim())
            .filter(role -> !role.isBlank())
            .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
            .distinct()
            .toList();
    }
}
```

Create `GatewayIdentityAuthenticationFilter` matching cart-service behavior:

- read `X-User-Id`, `X-User-Email`, `X-User-Roles`
- reject non-numeric or non-positive ids by leaving unauthenticated
- convert roles to `ROLE_...`
- set principal to `GatewayUser`
- clear `SecurityContextHolder` in `finally`.

Create `SecurityConfig`:

- stateless
- disable CSRF, HTTP basic, form login
- JSON `AuthenticationEntryPoint` with `ApiErrorResponse`
- permit health/info/OpenAPI/Swagger
- require `ADMIN` for `/api/admin/orders/**`
- require authentication for `/api/orders/**`.

Create `OpenApiConfig` with title `Order Service API`.

Create `GlobalExceptionHandler` mirroring cart-service, with order-specific mappings:

- `OrderNotFoundException` -> `404`
- `EmptyCartException`, `InventoryReservationFailedException`, `InvalidOrderOperationException` -> `409`
- `CartServiceUnavailableException`, `InventoryServiceUnavailableException` -> `503`
- `MissingUserIdentityException` -> `401`
- `AccessDeniedException` -> `403`
- validation/malformed/content-type/method/type mismatch handling same as cart-service.

- [ ] **Step 5: Write failing security/OpenAPI tests**

Create `SecurityConfigTests` with these cases using `@SpringBootTest(webEnvironment = RANDOM_PORT)` or `@WebMvcTest` with a dummy test controller:

- health is public: `GET /actuator/health` -> `200`
- liveness is public: `GET /actuator/health/liveness` -> not `401`
- `/api/orders` without headers -> `401` JSON message `Missing user identity`
- `/api/orders` with `X-User-Id: 10` -> not `401`
- `/api/admin/orders` with `X-User-Id: 10`, `X-User-Roles: USER` -> `403`
- `/api/admin/orders` with `X-User-Id: 10`, `X-User-Roles: ADMIN` -> not `403`

Create `OpenApiEndpointTests`:

```java
mockMvc.perform(get("/v3/api-docs"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.info.title").value("Order Service API"));
```

- [ ] **Step 6: Run tests to verify GREEN**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderResponseTests,UpdateOrderStatusRequestValidationTests,GatewayUserTests,ApiErrorResponseTests,SecurityConfigTests,OpenApiEndpointTests" test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add order-service/src/main/java/com/example/ecommerce/orderservice/dto order-service/src/main/java/com/example/ecommerce/orderservice/config order-service/src/main/java/com/example/ecommerce/orderservice/exception order-service/src/test/java/com/example/ecommerce/orderservice/dto order-service/src/test/java/com/example/ecommerce/orderservice/config order-service/src/test/java/com/example/ecommerce/orderservice/exception
git commit -m "feat: add order API support infrastructure"
```

---

### Task 4: Cart Client

**Files:**

- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/cart/CartClient.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/cart/CartSnapshot.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/cart/CartItemSnapshot.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/cart/RestClientCartClient.java`
- Modify: `order-service/src/main/java/com/example/ecommerce/orderservice/config/RestClientConfig.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/client/cart/RestClientCartClientTests.java`

- [ ] **Step 1: Write failing cart client tests**

Use `MockRestServiceServer`. Cover:

```java
@Test
void getCurrentCartSendsGatewayIdentityHeadersAndMapsResponse() {
    server.expect(once(), requestTo("http://cart-service/api/cart"))
        .andExpect(header("X-User-Id", "10"))
        .andExpect(header("X-User-Roles", "USER"))
        .andRespond(withSuccess("""
            {
              "cartId": 20,
              "userId": 10,
              "status": "ACTIVE",
              "items": [
                {
                  "productId": 100,
                  "productName": "Pour Over",
                  "unitPrice": 19.99,
                  "quantity": 2,
                  "lineTotal": 39.98
                }
              ],
              "subtotal": 39.98
            }
            """, MediaType.APPLICATION_JSON));

    CartSnapshot cart = client.getCurrentCart(new GatewayUser(10L, "user@example.com", List.of("USER")));

    assertThat(cart.cartId()).isEqualTo(20L);
    assertThat(cart.items()).hasSize(1);
}

@Test
void getCurrentCartMapsServerErrorToCartUnavailable() {
    server.expect(once(), requestTo("http://cart-service/api/cart"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.getCurrentCart(new GatewayUser(10L, "user@example.com", List.of("USER"))))
        .isInstanceOf(CartServiceUnavailableException.class);
}
```

- [ ] **Step 2: Run cart client tests to verify RED**

Run:

```powershell
mvn -pl order-service "-Dtest=RestClientCartClientTests" test
```

Expected: FAIL because client classes do not exist.

- [ ] **Step 3: Implement cart client**

Create:

```java
public interface CartClient {
    CartSnapshot getCurrentCart(GatewayUser user);
}

public record CartItemSnapshot(
    Long productId,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal lineTotal
) {
}

public record CartSnapshot(
    Long cartId,
    Long userId,
    String status,
    List<CartItemSnapshot> items,
    BigDecimal subtotal
) {
    public CartSnapshot {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
```

Implement `RestClientCartClient`:

- `GET /api/cart`
- set headers `X-User-Id`, `X-User-Email` when not null, `X-User-Roles`
- default roles to `USER` if empty
- null body -> `CartServiceUnavailableException`
- any `RestClientException` -> `CartServiceUnavailableException`.

Add `RestClientConfig` bean:

```java
@Bean
@LoadBalanced
RestClient.Builder loadBalancedRestClientBuilder() {
    return RestClient.builder();
}

@Bean
RestClient cartServiceRestClient(RestClient.Builder builder, @Value("${clients.cart-service.base-url}") String baseUrl) {
    return builder.baseUrl(baseUrl).build();
}
```

- [ ] **Step 4: Run cart client tests to verify GREEN**

Run:

```powershell
mvn -pl order-service "-Dtest=RestClientCartClientTests" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add order-service/src/main/java/com/example/ecommerce/orderservice/client/cart order-service/src/main/java/com/example/ecommerce/orderservice/config/RestClientConfig.java order-service/src/test/java/com/example/ecommerce/orderservice/client/cart
git commit -m "feat: add order cart client"
```

---

### Task 5: Inventory Reservation Client And Inventory Security Support

**Files:**

- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationClient.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationItem.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationRequest.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationResult.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/InventoryReservationStatus.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory/RestClientInventoryReservationClient.java`
- Modify: `order-service/src/main/java/com/example/ecommerce/orderservice/config/RestClientConfig.java`
- Modify: `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/SecurityConfig.java`
- Modify: `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/GatewayIdentityAuthenticationFilter.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/client/inventory/RestClientInventoryReservationClientTests.java`
- Test: `inventory-service/src/test/java/com/example/ecommerce/inventoryservice/config/SecurityConfigTests.java`

- [ ] **Step 1: Write failing inventory client tests**

Use `MockRestServiceServer`. Cover:

- `reserveSendsServiceRoleAndMapsReserved`
- `reserveMapsFailedStatus`
- `reserveMapsServerErrorToInventoryUnavailable`
- `releasePostsToReleaseEndpoint`

Expected request for reserve:

```json
{
  "orderId": 1000,
  "items": [
    {"productId": 100, "quantity": 2}
  ]
}
```

Expected header:

```text
X-User-Roles: SERVICE
```

- [ ] **Step 2: Run inventory client tests to verify RED**

Run:

```powershell
mvn -pl order-service "-Dtest=RestClientInventoryReservationClientTests" test
```

Expected: FAIL because inventory client classes do not exist.

- [ ] **Step 3: Implement inventory client**

Create:

```java
public interface InventoryReservationClient {
    InventoryReservationResult reserve(Long orderId, List<InventoryReservationItem> items);
    InventoryReservationResult release(Long orderId);
}

public record InventoryReservationItem(Long productId, Integer quantity) {
}

public record InventoryReservationRequest(Long orderId, List<InventoryReservationItem> items) {
    public InventoryReservationRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

public enum InventoryReservationStatus {
    RESERVED,
    RELEASED,
    DEDUCTED,
    FAILED
}

public record InventoryReservationResult(
    Long orderId,
    InventoryReservationStatus status
) {
}
```

Implement `RestClientInventoryReservationClient`:

- `POST /api/inventory/reservations`
- `POST /api/inventory/reservations/{orderId}/release`
- send `X-User-Roles: SERVICE`
- null body or any `RestClientException` -> `InventoryServiceUnavailableException`.

Add second RestClient bean:

```java
@Bean
RestClient inventoryServiceRestClient(
    RestClient.Builder builder,
    @Value("${clients.inventory-service.base-url}") String baseUrl
) {
    return builder.baseUrl(baseUrl).build();
}
```

- [ ] **Step 4: Write failing inventory security tests**

In `inventory-service` security tests add:

```java
@Test
void reservationEndpointAllowsServiceRole() throws Exception {
    mockMvc.perform(post("/api/inventory/reservations")
            .header("X-User-Roles", "SERVICE")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":1000,\"items\":[{\"productId\":100,\"quantity\":2}]}"))
        .andExpect(status().isNotForbidden());
}

@Test
void stockManagementEndpointStillRejectsServiceRole() throws Exception {
    mockMvc.perform(post("/api/inventory/items")
            .header("X-User-Roles", "SERVICE")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":100,\"availableQuantity\":5}"))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 5: Run inventory security tests to verify RED**

Run:

```powershell
mvn -pl inventory-service "-Dtest=SecurityConfigTests" test
```

Expected: FAIL because `/api/inventory/reservations/**` currently requires `ADMIN`.

- [ ] **Step 6: Implement inventory security support**

Modify `inventory-service` `SecurityConfig`:

```java
.requestMatchers("/api/inventory/reservations/**").hasAnyRole("ADMIN", "SERVICE")
.requestMatchers("/api/inventory/**").hasRole("ADMIN")
```

Modify `GatewayIdentityAuthenticationFilter` to clear context in `finally`:

```java
try {
    filterChain.doFilter(request, response);
} finally {
    SecurityContextHolder.clearContext();
}
```

- [ ] **Step 7: Run client and inventory security tests**

Run:

```powershell
mvn -pl order-service "-Dtest=RestClientInventoryReservationClientTests" test
mvn -pl inventory-service "-Dtest=SecurityConfigTests" test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add order-service/src/main/java/com/example/ecommerce/orderservice/client/inventory order-service/src/main/java/com/example/ecommerce/orderservice/config/RestClientConfig.java order-service/src/test/java/com/example/ecommerce/orderservice/client/inventory inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config inventory-service/src/test/java/com/example/ecommerce/inventoryservice/config/SecurityConfigTests.java
git commit -m "feat: add order inventory reservation client"
```

---

### Task 6: Order Service Behavior

**Files:**

- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/service/OrderService.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/service/OrderServiceTests.java`

- [ ] **Step 1: Write failing service tests**

Create tests covering:

```java
@Test
void checkoutRejectsEmptyCart() {
    when(cartClient.getCurrentCart(user())).thenReturn(new CartSnapshot(20L, USER_ID, "ACTIVE", List.of(), BigDecimal.ZERO));

    assertThatThrownBy(() -> orderService.checkout(user()))
        .isInstanceOf(EmptyCartException.class);
}

@Test
void checkoutReturnsExistingNonTerminalOrderForSameCart() {
    CartSnapshot cart = cartWithOneItem();
    Order existing = Order.createFromCart(USER_ID, 20L, List.of(orderItem()));
    when(cartClient.getCurrentCart(user())).thenReturn(cart);
    when(orderRepository.findFirstByUserIdAndSourceCartIdAndStatusNotIn(
        USER_ID, 20L, List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED)
    )).thenReturn(Optional.of(existing));

    OrderResponse response = orderService.checkout(user());

    assertThat(response.sourceCartId()).isEqualTo(20L);
    verifyNoInteractions(inventoryReservationClient);
}

@Test
void checkoutCreatesOrderAndMarksStockReserved() {
    when(cartClient.getCurrentCart(user())).thenReturn(cartWithOneItem());
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(inventoryReservationClient.reserve(any(), any()))
        .thenReturn(new InventoryReservationResult(1000L, InventoryReservationStatus.RESERVED));
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    OrderResponse response = orderService.checkout(user());

    assertThat(response.status()).isEqualTo(OrderStatus.STOCK_RESERVED);
}

@Test
void checkoutCancelsOrderWhenReservationFails() {
    when(cartClient.getCurrentCart(user())).thenReturn(cartWithOneItem());
    when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(inventoryReservationClient.reserve(any(), any()))
        .thenReturn(new InventoryReservationResult(1000L, InventoryReservationStatus.FAILED));

    assertThatThrownBy(() -> orderService.checkout(user()))
        .isInstanceOf(InventoryReservationFailedException.class);
}

@Test
void adminCancelReleasesStockForReservedOrder() {
    Order order = Order.createFromCart(USER_ID, 20L, List.of(orderItem()));
    order.markStockReserved();
    when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));
    when(inventoryReservationClient.release(1000L))
        .thenReturn(new InventoryReservationResult(1000L, InventoryReservationStatus.RELEASED));

    OrderResponse response = orderService.cancelAsAdmin(1000L, "Customer requested cancellation");

    assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
    verify(inventoryReservationClient).release(1000L);
}
```

Also add:

- `findCurrentUserOrders` calls `findByUserId`
- `findCurrentUserOrder` hides another user's order as `OrderNotFoundException`
- `findAdminOrders` calls `findByStatus` when status is present, otherwise `findAll`
- admin cancel leaves order unchanged when release throws `InventoryServiceUnavailableException`.

- [ ] **Step 2: Run service tests to verify RED**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderServiceTests" test
```

Expected: FAIL because `OrderService` does not exist.

- [ ] **Step 3: Implement service**

Create `OrderService` with dependencies:

```java
private final OrderRepository orderRepository;
private final CartClient cartClient;
private final InventoryReservationClient inventoryReservationClient;
```

Methods:

```java
@Transactional
public OrderResponse checkout(GatewayUser user)

@Transactional(readOnly = true)
public Page<OrderResponse> findCurrentUserOrders(Long userId, Pageable pageable)

@Transactional(readOnly = true)
public OrderResponse findCurrentUserOrder(Long userId, Long orderId)

@Transactional(readOnly = true)
public Page<OrderResponse> findAdminOrders(OrderStatus status, Pageable pageable)

@Transactional(readOnly = true)
public OrderResponse findAdminOrder(Long orderId)

@Transactional
public OrderResponse cancelAsAdmin(Long orderId, String reason)
```

Checkout algorithm:

1. `CartSnapshot cart = cartClient.getCurrentCart(user);`
2. reject `cart.cartId() == null` or empty `items`.
3. query existing non-terminal order by user and cart id.
4. create `Order` from mapped `OrderItem`s.
5. `saveAndFlush` to get id before reservation.
6. call `inventoryReservationClient.reserve(order.getId(), reservationItems)`.
7. if `RESERVED`, mark stock reserved and save.
8. if `FAILED`, cancel, save, throw `InventoryReservationFailedException`.
9. if unavailable exception, cancel, save, rethrow.

Admin cancellation:

1. load order or throw `OrderNotFoundException`.
2. if terminal, throw `InvalidOrderOperationException`.
3. if status is `STOCK_RESERVED` or `PAYMENT_PENDING`, call release before mutating.
4. cancel and save.

- [ ] **Step 4: Run service tests to verify GREEN**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderServiceTests" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add order-service/src/main/java/com/example/ecommerce/orderservice/service order-service/src/test/java/com/example/ecommerce/orderservice/service
git commit -m "feat: add order service behavior"
```

---

### Task 7: User And Admin Controllers

**Files:**

- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/controller/OrderController.java`
- Create: `order-service/src/main/java/com/example/ecommerce/orderservice/controller/AdminOrderController.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/controller/OrderControllerTests.java`
- Test: `order-service/src/test/java/com/example/ecommerce/orderservice/controller/AdminOrderControllerTests.java`

- [ ] **Step 1: Write failing user controller tests**

Cover:

- `checkoutReturnsCreatedOrder`
- `checkoutMapsEmptyCartToConflict`
- `checkoutMapsInventoryUnavailableToServiceUnavailable`
- `listOrdersReturnsCurrentUserOrders`
- `getOrderReturnsCurrentUserOrder`
- `missingGatewayUserReturnsUnauthorized`
- `unsupportedContentTypeReturnsUnsupportedMediaType`
- `unsupportedMethodReturnsMethodNotAllowed`

Expected checkout test:

```java
when(orderService.checkout(gatewayUser())).thenReturn(orderResponse(OrderStatus.STOCK_RESERVED));

mockMvc.perform(post("/api/orders/checkout").principal(authentication()))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.status").value("STOCK_RESERVED"));
```

- [ ] **Step 2: Run user controller tests to verify RED**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderControllerTests" test
```

Expected: FAIL because `OrderController` does not exist.

- [ ] **Step 3: Implement user controller**

Create:

```java
@RestController
@RequestMapping("/api/orders")
class OrderController {

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse checkout(Authentication authentication) {
        return orderService.checkout(currentUser(authentication));
    }

    @GetMapping
    Page<OrderResponse> list(Authentication authentication, Pageable pageable) {
        return orderService.findCurrentUserOrders(currentUser(authentication).id(), pageable);
    }

    @GetMapping("/{id}")
    OrderResponse get(Authentication authentication, @PathVariable Long id) {
        return orderService.findCurrentUserOrder(currentUser(authentication).id(), id);
    }
}
```

`currentUser` must throw `MissingUserIdentityException` unless principal is `GatewayUser`.

- [ ] **Step 4: Run user controller tests to verify GREEN**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderControllerTests" test
```

Expected: PASS.

- [ ] **Step 5: Write failing admin controller tests**

Cover:

- admin list requires `ADMIN`
- admin list with status delegates filter
- admin detail returns order
- admin cancel accepts only `CANCELLED`
- admin cancel maps terminal order conflict
- user role receives `403`

Expected status update test:

```java
UpdateOrderStatusRequest request = new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Customer requested");
when(orderService.cancelAsAdmin(1000L, "Customer requested")).thenReturn(orderResponse(OrderStatus.CANCELLED));

mockMvc.perform(patch("/api/admin/orders/{id}/status", 1000L)
        .principal(adminAuthentication())
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.status").value("CANCELLED"));
```

- [ ] **Step 6: Run admin controller tests to verify RED**

Run:

```powershell
mvn -pl order-service "-Dtest=AdminOrderControllerTests" test
```

Expected: FAIL because `AdminOrderController` does not exist.

- [ ] **Step 7: Implement admin controller**

Create:

```java
@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController {

    @GetMapping
    Page<OrderResponse> list(@RequestParam(required = false) OrderStatus status, Pageable pageable) {
        return orderService.findAdminOrders(status, pageable);
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable Long id) {
        return orderService.findAdminOrder(id);
    }

    @PatchMapping("/{id}/status")
    OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        if (request.status() != OrderStatus.CANCELLED) {
            throw new InvalidOrderOperationException("Only cancellation is supported");
        }
        return orderService.cancelAsAdmin(id, request.reason());
    }
}
```

- [ ] **Step 8: Run controller tests to verify GREEN**

Run:

```powershell
mvn -pl order-service "-Dtest=OrderControllerTests,AdminOrderControllerTests" test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add order-service/src/main/java/com/example/ecommerce/orderservice/controller order-service/src/test/java/com/example/ecommerce/orderservice/controller
git commit -m "feat: add order APIs"
```

---

### Task 8: Runtime Wiring, Docker, CI, And README

**Files:**

- Create: `order-service/Dockerfile`
- Modify: existing service Dockerfiles to copy `order-service/pom.xml`
- Modify: `docker-compose.yml`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Test: any existing CI/config tests affected by runtime wiring.

- [ ] **Step 1: Write failing runtime checks**

Run:

```powershell
docker compose config --quiet
docker compose build order-service
```

Expected before implementation: FAIL because compose has no `order-service` service and no Dockerfile.

- [ ] **Step 2: Add order-service Dockerfile**

Create `order-service/Dockerfile` based on cart-service:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY cart-service/pom.xml cart-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY order-service/src order-service/src

RUN mvn -pl order-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/order-service/target/*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Add `COPY order-service/pom.xml order-service/pom.xml` to:

- `eureka-server/Dockerfile`
- `api-gateway/Dockerfile`
- `auth-service/Dockerfile`
- `product-service/Dockerfile`
- `inventory-service/Dockerfile`
- `cart-service/Dockerfile`

- [ ] **Step 3: Update Docker Compose**

Add `order-postgres`:

```yaml
  order-postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: order_db
      POSTGRES_USER: ecommerce
      POSTGRES_PASSWORD: ecommerce
    ports:
      - "127.0.0.1:5436:5432"
    volumes:
      - order-postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ecommerce -d order_db"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 10s
```

Add `order-service`:

```yaml
  order-service:
    build:
      context: .
      dockerfile: order-service/Dockerfile
    container_name: ecommerce-order-service
    depends_on:
      order-postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      cart-service:
        condition: service_healthy
      inventory-service:
        condition: service_healthy
    restart: on-failure
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: jdbc:postgresql://order-postgres:5432/order_db
      SPRING_DATASOURCE_USERNAME: ecommerce
      SPRING_DATASOURCE_PASSWORD: ecommerce
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      EUREKA_INSTANCE_LEASE_RENEWAL_INTERVAL_IN_SECONDS: 5
      EUREKA_INSTANCE_LEASE_EXPIRATION_DURATION_IN_SECONDS: 15
      CART_SERVICE_BASE_URL: http://cart-service
      INVENTORY_SERVICE_BASE_URL: http://inventory-service
    ports:
      - "8085:8085"
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s
```

Add `order-service` dependency to `api-gateway`:

```yaml
      order-service:
        condition: service_healthy
```

Add volume:

```yaml
  order-postgres-data:
```

- [ ] **Step 4: Update CI and README**

In `.github/workflows/ci.yml`, ensure the full Maven test and Docker build includes `order-service`.

In `README.md`, document:

- order-service port `8085`
- order database port `5436`
- `POST /api/orders/checkout`
- `GET /api/orders`
- `GET /api/orders/{id}`
- `GET /api/admin/orders`
- `PATCH /api/admin/orders/{id}/status`
- local commands:

```powershell
mvn -B -ntp clean test
docker compose up -d --build
```

- [ ] **Step 5: Run runtime verification**

Run:

```powershell
mvn -pl order-service test
docker compose config --quiet
docker compose build eureka-server api-gateway auth-service product-service inventory-service cart-service order-service
```

Expected: all commands exit `0`.

- [ ] **Step 6: Commit**

```powershell
git add order-service/Dockerfile docker-compose.yml .github/workflows/ci.yml README.md eureka-server/Dockerfile api-gateway/Dockerfile auth-service/Dockerfile product-service/Dockerfile inventory-service/Dockerfile cart-service/Dockerfile
git commit -m "chore: wire order service runtime"
```

---

### Task 9: Full Verification And Review

**Files:**

- No planned production changes unless review finds issues.

- [ ] **Step 1: Run full Maven verification**

Run:

```powershell
mvn -B -ntp clean test
```

Expected:

- Reactor includes `order-service`.
- Build exits `0`.
- Surefire reports `0` failures and `0` errors.

- [ ] **Step 2: Count test reports**

Run:

```powershell
$totals = [ordered]@{tests=0; failures=0; errors=0; skipped=0}
Get-ChildItem -Path . -Recurse -Filter 'TEST-*.xml' | ForEach-Object {
    [xml]$xml = Get-Content $_.FullName
    $suite = $xml.testsuite
    $totals.tests += [int]$suite.tests
    $totals.failures += [int]$suite.failures
    $totals.errors += [int]$suite.errors
    $totals.skipped += [int]$suite.skipped
}
$totals.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }
```

Expected:

```text
failures=0
errors=0
```

- [ ] **Step 3: Run Compose and Docker verification**

Run:

```powershell
docker compose config --quiet
docker compose build eureka-server api-gateway auth-service product-service inventory-service cart-service order-service
```

Expected: both commands exit `0`.

- [ ] **Step 4: Request code review**

Use `superpowers:requesting-code-review` with explicit review scope:

- spec compliance for `order-service`
- TDD coverage quality
- transaction boundaries around checkout and cancellation
- cross-service error mapping
- authorization rules
- Docker Compose and CI wiring

- [ ] **Step 5: Fix Critical and Important findings**

For each review finding:

1. Write or update a failing test that reproduces the issue.
2. Run the targeted test and verify it fails for the expected reason.
3. Implement the smallest fix.
4. Run the targeted test and full relevant module tests.
5. Commit the fix with message `fix: ...`.

- [ ] **Step 6: Push branch**

Run:

```powershell
git status --short --branch
git push -u origin order-service
```

Expected: clean working tree and successful push.
