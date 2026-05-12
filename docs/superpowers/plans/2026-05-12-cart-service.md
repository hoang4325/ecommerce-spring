# Cart Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the PostgreSQL-backed MVP `cart-service` and wire it into the existing Spring Boot microservices system.

**Architecture:** `cart-service` owns cart data in `cart_db`, receives user identity through gateway headers, and calls `product-service` through a small `ProductCatalogClient` adapter to snapshot product name and price. It exposes REST endpoints under `/api/cart`, registers with Eureka, and follows the same controller/service/repository/config/exception layout as the existing services.

**Tech Stack:** Java 21, Spring Boot 3.5.14, Spring Web MVC, Spring Security, Spring Data JPA, Spring Cloud Eureka, Spring Cloud LoadBalancer, PostgreSQL, H2 tests, Docker Compose, Maven, OpenAPI.

---

## Scope Check

This plan implements one service plus the single product lookup endpoint that the service requires. It does not implement checkout, Redis, Kafka, payment, notification, or order orchestration.

## File Structure

### Product-Service Touchpoint

- Modify `product-service/src/main/java/com/example/ecommerce/productservice/controller/ProductController.java`
  - Add `GET /api/products/id/{id}`.
- Modify `product-service/src/main/java/com/example/ecommerce/productservice/service/ProductService.java`
  - Add `getById(Long id)` using active product and active category rules.
- Modify `product-service/src/main/java/com/example/ecommerce/productservice/repository/ProductRepository.java`
  - Add repository query by id with active product/category.
- Modify tests:
  - `product-service/src/test/java/com/example/ecommerce/productservice/controller/ProductControllerTests.java`
  - `product-service/src/test/java/com/example/ecommerce/productservice/service/ProductServiceTests.java`

### Cart-Service Module

- Modify root `pom.xml`
  - Add `<module>cart-service</module>`.
- Create `cart-service/pom.xml`
  - Match `inventory-service` dependencies plus test dependencies.
- Create `cart-service/src/main/java/com/example/ecommerce/cartservice/CartServiceApplication.java`
- Create `cart-service/src/main/resources/application.yml`
- Create `cart-service/Dockerfile`

### Cart Domain

- Create `cart-service/src/main/java/com/example/ecommerce/cartservice/entity/CartStatus.java`
- Create `cart-service/src/main/java/com/example/ecommerce/cartservice/entity/Cart.java`
- Create `cart-service/src/main/java/com/example/ecommerce/cartservice/entity/CartItem.java`
- Create repositories:
  - `cart-service/src/main/java/com/example/ecommerce/cartservice/repository/CartRepository.java`
  - `cart-service/src/main/java/com/example/ecommerce/cartservice/repository/CartItemRepository.java`
- Create tests:
  - `cart-service/src/test/java/com/example/ecommerce/cartservice/entity/CartTests.java`
  - `cart-service/src/test/java/com/example/ecommerce/cartservice/repository/CartRepositoryTests.java`
  - `cart-service/src/test/java/com/example/ecommerce/cartservice/repository/CartItemRepositoryTests.java`

### Cart API And Logic

- Create DTOs:
  - `AddCartItemRequest`
  - `UpdateCartItemRequest`
  - `CartItemResponse`
  - `CartResponse`
- Create exceptions:
  - `ApiErrorResponse`
  - `CartItemNotFoundException`
  - `InvalidCartOperationException`
  - `ProductCatalogUnavailableException`
  - `ProductNotFoundException`
  - `MissingUserIdentityException`
  - `GlobalExceptionHandler`
- Create product client:
  - `ProductCatalogClient`
  - `ProductCatalogItem`
  - `RestClientProductCatalogClient`
  - `ProductCatalogClientConfig`
- Create service:
  - `CartService`
- Create controller:
  - `CartController`
- Create security:
  - `GatewayUser`
  - `GatewayIdentityAuthenticationFilter`
  - `SecurityConfig`
  - `OpenApiConfig`

### Runtime Wiring

- Modify `docker-compose.yml`
  - Add `cart-postgres` and `cart-service`.
  - Add `cart-service` dependency to `api-gateway`.
- Modify `.github/workflows/ci.yml`
  - Build `cart-service`.
- Modify `README.md`
  - Document local and Docker Compose cart commands.
- Modify `api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`
  - Keep route coverage passing after runtime wiring changes.

---

## Task 1: Add Product Lookup By Id

**Files:**
- Modify: `product-service/src/test/java/com/example/ecommerce/productservice/service/ProductServiceTests.java`
- Modify: `product-service/src/test/java/com/example/ecommerce/productservice/controller/ProductControllerTests.java`
- Modify: `product-service/src/main/java/com/example/ecommerce/productservice/repository/ProductRepository.java`
- Modify: `product-service/src/main/java/com/example/ecommerce/productservice/service/ProductService.java`
- Modify: `product-service/src/main/java/com/example/ecommerce/productservice/controller/ProductController.java`

- [ ] **Step 1: Write failing service tests**

Add these tests to `ProductServiceTests`:

```java
@Test
void getByIdReturnsActiveProductWithActiveCategory() {
    Category category = categoryWithId(10L, "Coffee", "coffee", true);
    Product product = productWithId(20L, category, "Pour Over", "pour-over", true);
    when(productRepository.findByIdAndActiveTrueAndCategoryActiveTrue(20L)).thenReturn(Optional.of(product));

    ProductResponse response = productService.getById(20L);

    assertThat(response.id()).isEqualTo(20L);
    assertThat(response.name()).isEqualTo("Pour Over");
    assertThat(response.price()).isEqualByComparingTo("19.99");
}

@Test
void getByIdRejectsMissingOrInactiveProduct() {
    when(productRepository.findByIdAndActiveTrueAndCategoryActiveTrue(20L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getById(20L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Product not found");
}
```

- [ ] **Step 2: Run service tests and verify red**

Run:

```powershell
mvn -pl product-service -Dtest=ProductServiceTests test
```

Expected: compilation fails because `findByIdAndActiveTrueAndCategoryActiveTrue` and `getById` do not exist.

- [ ] **Step 3: Add repository and service implementation**

In `ProductRepository`, add:

```java
Optional<Product> findByIdAndActiveTrueAndCategoryActiveTrue(Long id);
```

In `ProductService`, add:

```java
@Transactional(readOnly = true)
public ProductResponse getById(Long id) {
    return productRepository.findByIdAndActiveTrueAndCategoryActiveTrue(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException(PRODUCT_NOT_FOUND_MESSAGE));
}
```

- [ ] **Step 4: Run service tests and verify green**

Run:

```powershell
mvn -pl product-service -Dtest=ProductServiceTests test
```

Expected: `ProductServiceTests` passes.

- [ ] **Step 5: Write failing controller tests**

Add these tests to `ProductControllerTests`:

```java
@Test
void productDetailByIdReturnsProduct() throws Exception {
    when(productService.getById(20L)).thenReturn(productResponse());

    mockMvc.perform(get("/api/products/id/20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(20))
        .andExpect(jsonPath("$.name").value("Pour Over"));

    verify(productService).getById(20L);
}

@Test
void missingProductByIdReturnsNotFound() throws Exception {
    doThrow(new ResourceNotFoundException("Product not found")).when(productService).getById(99L);

    mockMvc.perform(get("/api/products/id/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Product not found"));
}
```

- [ ] **Step 6: Run controller tests and verify red**

Run:

```powershell
mvn -pl product-service -Dtest=ProductControllerTests test
```

Expected: status is `404` because `/api/products/id/{id}` is not mapped.

- [ ] **Step 7: Add controller endpoint**

In `ProductController`, add this method before `getBySlug` so `/id/{id}` is not interpreted as a slug:

```java
@GetMapping("/id/{id}")
ProductResponse getById(@PathVariable Long id) {
    return productService.getById(id);
}
```

- [ ] **Step 8: Run product-service tests**

Run:

```powershell
mvn -pl product-service test
```

Expected: product-service tests pass.

- [ ] **Step 9: Commit**

Run:

```powershell
git add product-service/src/main/java/com/example/ecommerce/productservice product-service/src/test/java/com/example/ecommerce/productservice
git commit -m "feat: expose product lookup by id"
```

---

## Task 2: Create Cart Module Skeleton

**Files:**
- Modify: `pom.xml`
- Create: `cart-service/pom.xml`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/CartServiceApplication.java`
- Create: `cart-service/src/main/resources/application.yml`
- Create: `cart-service/src/test/java/com/example/ecommerce/cartservice/CartServiceApplicationTests.java`

- [ ] **Step 1: Add module to parent POM**

Add this module after `inventory-service` in root `pom.xml`:

```xml
<module>cart-service</module>
```

- [ ] **Step 2: Create cart-service Maven POM**

Create `cart-service/pom.xml` with:

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

    <artifactId>cart-service</artifactId>
    <name>cart-service</name>
    <description>Cart service for the e-commerce microservices system</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.17</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
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

- [ ] **Step 3: Create application class**

Create `CartServiceApplication.java`:

```java
package com.example.ecommerce.cartservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Create application configuration**

Create `application.yml`:

```yaml
spring:
  application:
    name: cart-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5435/cart_db}
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
  port: 8084

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true

clients:
  product-service:
    base-url: ${PRODUCT_SERVICE_BASE_URL:http://product-service}

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

- [ ] **Step 5: Write application context test**

Create `CartServiceApplicationTests.java`:

```java
package com.example.ecommerce.cartservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:cart_service_context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CartServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 6: Run test and verify initial module compiles**

Run:

```powershell
mvn -pl cart-service -am test
```

Expected: cart-service context test passes.

- [ ] **Step 7: Commit**

Run:

```powershell
git add pom.xml cart-service
git commit -m "feat: add cart service module skeleton"
```

---

## Task 3: Add Cart Entities And Repositories

**Files:**
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/entity/CartStatus.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/entity/Cart.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/entity/CartItem.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/repository/CartRepository.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/repository/CartItemRepository.java`
- Create: `cart-service/src/test/java/com/example/ecommerce/cartservice/entity/CartTests.java`
- Create: `cart-service/src/test/java/com/example/ecommerce/cartservice/repository/CartRepositoryTests.java`
- Create: `cart-service/src/test/java/com/example/ecommerce/cartservice/repository/CartItemRepositoryTests.java`

- [ ] **Step 1: Write failing entity tests**

Create `CartTests.java`:

```java
package com.example.ecommerce.cartservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CartTests {

    @Test
    void createActiveCartUsesUserIdAsActiveKey() {
        Cart cart = Cart.createActive(10L);

        assertThat(cart.getUserId()).isEqualTo(10L);
        assertThat(cart.getActiveCartKey()).isEqualTo(10L);
        assertThat(cart.getStatus()).isEqualTo(CartStatus.ACTIVE);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void addItemStoresSnapshotAndCalculatesSubtotal() {
        Cart cart = Cart.createActive(10L);

        cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 2);

        assertThat(cart.getItems()).hasSize(1);
        CartItem item = cart.getItems().getFirst();
        assertThat(item.getProductId()).isEqualTo(20L);
        assertThat(item.getProductNameSnapshot()).isEqualTo("Pour Over");
        assertThat(item.getUnitPriceSnapshot()).isEqualByComparingTo("19.99");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(cart.subtotal()).isEqualByComparingTo("39.98");
    }

    @Test
    void addExistingProductIncrementsQuantityAndRefreshesSnapshot() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Old Name", new BigDecimal("10.00"), 1);

        cart.addOrIncrementItem(20L, "New Name", new BigDecimal("12.50"), 3);

        CartItem item = cart.getItems().getFirst();
        assertThat(item.getQuantity()).isEqualTo(4);
        assertThat(item.getProductNameSnapshot()).isEqualTo("New Name");
        assertThat(item.getUnitPriceSnapshot()).isEqualByComparingTo("12.50");
    }

    @Test
    void updateItemQuantityReplacesQuantityAndSnapshot() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Old Name", new BigDecimal("10.00"), 1);

        cart.updateItem(20L, "New Name", new BigDecimal("12.50"), 5);

        CartItem item = cart.getItems().getFirst();
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getProductNameSnapshot()).isEqualTo("New Name");
        assertThat(item.getUnitPriceSnapshot()).isEqualByComparingTo("12.50");
    }

    @Test
    void invalidQuantityIsRejected() {
        Cart cart = Cart.createActive(10L);

        assertThatThrownBy(() -> cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Quantity must be positive");
    }
}
```

- [ ] **Step 2: Run entity tests and verify red**

Run:

```powershell
mvn -pl cart-service -Dtest=CartTests test
```

Expected: compilation fails because `Cart`, `CartItem`, and `CartStatus` do not exist.

- [ ] **Step 3: Implement entities**

Create `CartStatus.java`:

```java
package com.example.ecommerce.cartservice.entity;

public enum CartStatus {
    ACTIVE,
    CHECKED_OUT
}
```

Create `Cart.java` with fields, JPA annotations, and domain methods:

```java
package com.example.ecommerce.cartservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "active_cart_key", unique = true)
    private Long activeCartKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CartStatus status;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId ASC")
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    Cart() {
    }

    private Cart(Long userId) {
        requireReference(userId, "User id must not be null");
        this.userId = userId;
        this.activeCartKey = userId;
        this.status = CartStatus.ACTIVE;
    }

    public static Cart createActive(Long userId) {
        return new Cart(userId);
    }

    public void addOrIncrementItem(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        requireActive();
        requirePositive(quantity);
        Optional<CartItem> existingItem = findItem(productId);
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.updateSnapshot(productName, unitPrice);
            item.setQuantity(Math.addExact(item.getQuantity(), quantity));
            return;
        }
        items.add(CartItem.create(this, productId, productName, unitPrice, quantity));
    }

    public void updateItem(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        requireActive();
        requirePositive(quantity);
        CartItem item = findItem(productId).orElseThrow(() -> new IllegalArgumentException("Cart item not found"));
        item.updateSnapshot(productName, unitPrice);
        item.setQuantity(quantity);
    }

    public boolean removeItem(Long productId) {
        requireActive();
        return items.removeIf(item -> item.getProductId().equals(productId));
    }

    public void clearItems() {
        requireActive();
        items.clear();
    }

    public BigDecimal subtotal() {
        return items.stream()
            .map(CartItem::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Optional<CartItem> findItem(Long productId) {
        return items.stream().filter(item -> item.getProductId().equals(productId)).findFirst();
    }

    private void requireActive() {
        if (status != CartStatus.ACTIVE) {
            throw new IllegalStateException("Cart is not active");
        }
    }

    private static void requireReference(Long value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getActiveCartKey() {
        return activeCartKey;
    }

    public CartStatus getStatus() {
        return status;
    }

    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
```

Create `CartItem.java`:

```java
package com.example.ecommerce.cartservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = @UniqueConstraint(name = "uk_cart_items_cart_product", columnNames = {"cart_id", "product_id"})
)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    CartItem() {
    }

    private CartItem(Cart cart, Long productId, String productNameSnapshot, BigDecimal unitPriceSnapshot, int quantity) {
        requireReference(cart, "Cart must not be null");
        requireReference(productId, "Product id must not be null");
        requireText(productNameSnapshot);
        requireReference(unitPriceSnapshot, "Unit price must not be null");
        setQuantity(quantity);
        this.cart = cart;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public static CartItem create(
        Cart cart,
        Long productId,
        String productNameSnapshot,
        BigDecimal unitPriceSnapshot,
        int quantity
    ) {
        return new CartItem(cart, productId, productNameSnapshot, unitPriceSnapshot, quantity);
    }

    public void updateSnapshot(String productNameSnapshot, BigDecimal unitPriceSnapshot) {
        requireText(productNameSnapshot);
        requireReference(unitPriceSnapshot, "Unit price must not be null");
        this.productNameSnapshot = productNameSnapshot;
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.quantity = quantity;
    }

    public BigDecimal lineTotal() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }

    private static void requireReference(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Cart getCart() {
        return cart;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public BigDecimal getUnitPriceSnapshot() {
        return unitPriceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
```

- [ ] **Step 4: Run entity tests and verify green**

Run:

```powershell
mvn -pl cart-service -Dtest=CartTests test
```

Expected: `CartTests` passes.

- [ ] **Step 5: Create repositories**

Create `CartRepository.java`:

```java
package com.example.ecommerce.cartservice.repository;

import com.example.ecommerce.cartservice.entity.Cart;
import com.example.ecommerce.cartservice.entity.CartStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByUserIdAndStatus(Long userId, CartStatus status);
}
```

Create `CartItemRepository.java`:

```java
package com.example.ecommerce.cartservice.repository;

import com.example.ecommerce.cartservice.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
```

- [ ] **Step 6: Write repository tests**

Create `CartRepositoryTests.java`:

```java
package com.example.ecommerce.cartservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.cartservice.entity.Cart;
import com.example.ecommerce.cartservice.entity.CartStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cart_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CartRepositoryTests {

    @Autowired
    private CartRepository cartRepository;

    @Test
    void findByUserIdAndStatusReturnsActiveCartWithItems() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 2);
        cartRepository.saveAndFlush(cart);

        Cart found = cartRepository.findByUserIdAndStatus(10L, CartStatus.ACTIVE).orElseThrow();

        assertThat(found.getUserId()).isEqualTo(10L);
        assertThat(found.getItems())
            .extracting(item -> item.getProductId())
            .containsExactly(20L);
    }

    @Test
    void savePopulatesTimestamps() {
        Cart cart = Cart.createActive(10L);

        Cart saved = cartRepository.saveAndFlush(cart);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
```

Create `CartItemRepositoryTests.java`:

```java
package com.example.ecommerce.cartservice.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.cartservice.entity.Cart;
import com.example.ecommerce.cartservice.entity.CartItem;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cart_item_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CartItemRepositoryTests {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void duplicateProductInSameCartViolatesConstraint() {
        Cart cart = Cart.createActive(10L);
        cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 1);
        Cart savedCart = cartRepository.saveAndFlush(cart);

        CartItem duplicate = CartItem.create(savedCart, 20L, "Pour Over", new BigDecimal("19.99"), 1);

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 7: Run repository tests**

Run:

```powershell
mvn -pl cart-service -Dtest=CartRepositoryTests,CartItemRepositoryTests test
```

Expected: repository tests pass.

- [ ] **Step 8: Commit**

Run:

```powershell
git add cart-service/src/main/java/com/example/ecommerce/cartservice/entity cart-service/src/main/java/com/example/ecommerce/cartservice/repository cart-service/src/test/java/com/example/ecommerce/cartservice/entity cart-service/src/test/java/com/example/ecommerce/cartservice/repository
git commit -m "feat: add cart domain persistence"
```

---

## Task 4: Add DTOs And Exceptions

**Files:**
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/dto/AddCartItemRequest.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/dto/UpdateCartItemRequest.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/dto/CartItemResponse.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/dto/CartResponse.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/exception/*.java`
- Create: `cart-service/src/test/java/com/example/ecommerce/cartservice/dto/CartRequestValidationTests.java`
- Create: `cart-service/src/test/java/com/example/ecommerce/cartservice/exception/ApiErrorResponseTests.java`

- [ ] **Step 1: Write DTO validation tests**

Create `CartRequestValidationTests.java`:

```java
package com.example.ecommerce.cartservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class CartRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void addRequestRejectsMissingProductIdAndNonPositiveQuantity() {
        AddCartItemRequest request = new AddCartItemRequest(null, 0);

        assertThat(validator.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("productId", "quantity");
    }

    @Test
    void updateRequestRejectsNonPositiveQuantity() {
        UpdateCartItemRequest request = new UpdateCartItemRequest(-1);

        assertThat(validator.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("quantity");
    }
}
```

- [ ] **Step 2: Run validation tests and verify red**

Run:

```powershell
mvn -pl cart-service -Dtest=CartRequestValidationTests test
```

Expected: compilation fails because DTOs do not exist.

- [ ] **Step 3: Create DTOs**

Create:

```java
package com.example.ecommerce.cartservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
    @NotNull Long productId,
    @NotNull @Positive Integer quantity
) {
}
```

```java
package com.example.ecommerce.cartservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateCartItemRequest(
    @NotNull @Positive Integer quantity
) {
}
```

```java
package com.example.ecommerce.cartservice.dto;

import java.math.BigDecimal;

public record CartItemResponse(
    Long productId,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal lineTotal
) {
}
```

```java
package com.example.ecommerce.cartservice.dto;

import com.example.ecommerce.cartservice.entity.CartStatus;
import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
    Long cartId,
    Long userId,
    CartStatus status,
    List<CartItemResponse> items,
    BigDecimal subtotal
) {
}
```

- [ ] **Step 4: Create exception response model and exceptions**

Mirror the existing `ApiErrorResponse` shape from product-service:

```java
package com.example.ecommerce.cartservice.exception;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldErrorDetail> details
) {
    public record FieldErrorDetail(String field, String message) {
    }
}
```

Create runtime exception classes with fixed messages:

```java
package com.example.ecommerce.cartservice.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException() {
        super("Cart item not found");
    }
}
```

```java
package com.example.ecommerce.cartservice.exception;

public class InvalidCartOperationException extends RuntimeException {
    public InvalidCartOperationException(String message) {
        super(message);
    }
}
```

```java
package com.example.ecommerce.cartservice.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException() {
        super("Product not found");
    }
}
```

```java
package com.example.ecommerce.cartservice.exception;

public class ProductCatalogUnavailableException extends RuntimeException {
    public ProductCatalogUnavailableException() {
        super("Product catalog unavailable");
    }
}
```

```java
package com.example.ecommerce.cartservice.exception;

public class MissingUserIdentityException extends RuntimeException {
    public MissingUserIdentityException() {
        super("Missing user identity");
    }
}
```

- [ ] **Step 5: Run DTO tests**

Run:

```powershell
mvn -pl cart-service -Dtest=CartRequestValidationTests test
```

Expected: validation tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add cart-service/src/main/java/com/example/ecommerce/cartservice/dto cart-service/src/main/java/com/example/ecommerce/cartservice/exception cart-service/src/test/java/com/example/ecommerce/cartservice/dto
git commit -m "feat: add cart API DTOs and exceptions"
```

---

## Task 5: Add Product Catalog Client

**Files:**
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/client/ProductCatalogClient.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/client/ProductCatalogItem.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/client/RestClientProductCatalogClient.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/config/ProductCatalogClientConfig.java`
- Create: `cart-service/src/test/java/com/example/ecommerce/cartservice/client/RestClientProductCatalogClientTests.java`

- [ ] **Step 1: Write client tests using MockRestServiceServer**

Create `RestClientProductCatalogClientTests.java`:

```java
package com.example.ecommerce.cartservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.ecommerce.cartservice.exception.ProductCatalogUnavailableException;
import com.example.ecommerce.cartservice.exception.ProductNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientProductCatalogClientTests {

    private MockRestServiceServer server;
    private RestClientProductCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://product-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientProductCatalogClient(builder.build());
    }

    @Test
    void getProductReturnsCatalogItem() {
        server.expect(once(), requestTo("http://product-service/api/products/id/20"))
            .andRespond(withSuccess("""
                {"id":20,"name":"Pour Over","price":19.99}
                """, MediaType.APPLICATION_JSON));

        ProductCatalogItem item = client.getProduct(20L);

        assertThat(item.id()).isEqualTo(20L);
        assertThat(item.name()).isEqualTo("Pour Over");
        assertThat(item.price()).isEqualByComparingTo(new BigDecimal("19.99"));
    }

    @Test
    void getProductMapsNotFound() {
        server.expect(once(), requestTo("http://product-service/api/products/id/99"))
            .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.getProduct(99L))
            .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getProductMapsUnexpectedError() {
        server.expect(once(), requestTo("http://product-service/api/products/id/20"))
            .andRespond(withServerError());

        assertThatThrownBy(() -> client.getProduct(20L))
            .isInstanceOf(ProductCatalogUnavailableException.class);
    }
}
```

- [ ] **Step 2: Run client tests and verify red**

Run:

```powershell
mvn -pl cart-service -Dtest=RestClientProductCatalogClientTests test
```

Expected: compilation fails because product client classes do not exist.

- [ ] **Step 3: Create client types and implementation**

Create `ProductCatalogItem.java`:

```java
package com.example.ecommerce.cartservice.client;

import java.math.BigDecimal;

public record ProductCatalogItem(Long id, String name, BigDecimal price) {
}
```

Create `ProductCatalogClient.java`:

```java
package com.example.ecommerce.cartservice.client;

public interface ProductCatalogClient {

    ProductCatalogItem getProduct(Long productId);
}
```

Create `RestClientProductCatalogClient.java`:

```java
package com.example.ecommerce.cartservice.client;

import com.example.ecommerce.cartservice.exception.ProductCatalogUnavailableException;
import com.example.ecommerce.cartservice.exception.ProductNotFoundException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestClientProductCatalogClient implements ProductCatalogClient {

    private final RestClient productServiceRestClient;

    public RestClientProductCatalogClient(RestClient productServiceRestClient) {
        this.productServiceRestClient = productServiceRestClient;
    }

    @Override
    public ProductCatalogItem getProduct(Long productId) {
        try {
            ProductCatalogResponse response = productServiceRestClient.get()
                .uri("/api/products/id/{id}", productId)
                .retrieve()
                .onStatus(status -> status == HttpStatus.NOT_FOUND, (request, clientResponse) -> {
                    throw new ProductNotFoundException();
                })
                .body(ProductCatalogResponse.class);
            if (response == null) {
                throw new ProductCatalogUnavailableException();
            }
            return new ProductCatalogItem(response.id(), response.name(), response.price());
        } catch (ProductNotFoundException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new ProductCatalogUnavailableException();
        } catch (RestClientException ex) {
            throw new ProductCatalogUnavailableException();
        }
    }

    private record ProductCatalogResponse(Long id, String name, BigDecimal price) {
    }
}
```

Create `ProductCatalogClientConfig.java`:

```java
package com.example.ecommerce.cartservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class ProductCatalogClientConfig {

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient productServiceRestClient(
        RestClient.Builder loadBalancedRestClientBuilder,
        @Value("${clients.product-service.base-url}") String productServiceBaseUrl
    ) {
        return loadBalancedRestClientBuilder.baseUrl(productServiceBaseUrl).build();
    }
}
```

- [ ] **Step 4: Run client tests and verify green**

Run:

```powershell
mvn -pl cart-service -Dtest=RestClientProductCatalogClientTests test
```

Expected: client tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add cart-service/src/main/java/com/example/ecommerce/cartservice/client cart-service/src/main/java/com/example/ecommerce/cartservice/config/ProductCatalogClientConfig.java cart-service/src/test/java/com/example/ecommerce/cartservice/client
git commit -m "feat: add cart product catalog client"
```

---

## Task 6: Add Cart Service Behavior

**Files:**
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/service/CartService.java`
- Create: `cart-service/src/test/java/com/example/ecommerce/cartservice/service/CartServiceTests.java`

- [ ] **Step 1: Write failing service tests**

Create `CartServiceTests.java` with this skeleton and the test bodies below:

```java
@ExtendWith(MockitoExtension.class)
class CartServiceTests {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductCatalogClient productCatalogClient;

    @InjectMocks
    private CartService cartService;
}
```

Add these test bodies:

```java
@Test
void getCurrentCartReturnsEmptyCartWhenNoActiveCartExists() {
    when(cartRepository.findByUserIdAndStatus(10L, CartStatus.ACTIVE)).thenReturn(Optional.empty());

    CartResponse response = cartService.getCurrentCart(10L);

    assertThat(response.cartId()).isNull();
    assertThat(response.userId()).isEqualTo(10L);
    assertThat(response.items()).isEmpty();
    assertThat(response.subtotal()).isEqualByComparingTo("0");
}

@Test
void addItemCreatesCartAndStoresProductSnapshot() {
    when(cartRepository.findByUserIdAndStatus(10L, CartStatus.ACTIVE)).thenReturn(Optional.empty());
    when(productCatalogClient.getProduct(20L))
        .thenReturn(new ProductCatalogItem(20L, "Pour Over", new BigDecimal("19.99")));
    when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CartResponse response = cartService.addItem(10L, new AddCartItemRequest(20L, 2));

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().productName()).isEqualTo("Pour Over");
    assertThat(response.items().getFirst().lineTotal()).isEqualByComparingTo("39.98");
}

@Test
void addItemIncrementsExistingProductQuantity() {
    Cart cart = Cart.createActive(10L);
    cart.addOrIncrementItem(20L, "Pour Over", new BigDecimal("19.99"), 1);
    when(cartRepository.findByUserIdAndStatus(10L, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
    when(productCatalogClient.getProduct(20L))
        .thenReturn(new ProductCatalogItem(20L, "Pour Over", new BigDecimal("19.99")));
    when(cartRepository.save(cart)).thenReturn(cart);

    CartResponse response = cartService.addItem(10L, new AddCartItemRequest(20L, 3));

    assertThat(response.items().getFirst().quantity()).isEqualTo(4);
}

@Test
void updateItemRejectsMissingItem() {
    Cart cart = Cart.createActive(10L);
    when(cartRepository.findByUserIdAndStatus(10L, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
    when(productCatalogClient.getProduct(20L))
        .thenReturn(new ProductCatalogItem(20L, "Pour Over", new BigDecimal("19.99")));

    assertThatThrownBy(() -> cartService.updateItem(10L, 20L, new UpdateCartItemRequest(2)))
        .isInstanceOf(CartItemNotFoundException.class);
}

@Test
void clearCartIsIdempotentWhenNoCartExists() {
    when(cartRepository.findByUserIdAndStatus(10L, CartStatus.ACTIVE)).thenReturn(Optional.empty());

    cartService.clearCart(10L);

    verify(cartRepository, never()).save(any(Cart.class));
}

@Test
void productCatalogUnavailablePropagatesAsProductCatalogUnavailable() {
    when(productCatalogClient.getProduct(20L)).thenThrow(new ProductCatalogUnavailableException());

    assertThatThrownBy(() -> cartService.addItem(10L, new AddCartItemRequest(20L, 1)))
        .isInstanceOf(ProductCatalogUnavailableException.class);
}
```

- [ ] **Step 2: Run service tests and verify red**

Run:

```powershell
mvn -pl cart-service -Dtest=CartServiceTests test
```

Expected: compilation fails because `CartService` does not exist.

- [ ] **Step 3: Implement CartService**

Create `CartService.java`:

```java
package com.example.ecommerce.cartservice.service;

import com.example.ecommerce.cartservice.client.ProductCatalogClient;
import com.example.ecommerce.cartservice.client.ProductCatalogItem;
import com.example.ecommerce.cartservice.dto.AddCartItemRequest;
import com.example.ecommerce.cartservice.dto.CartItemResponse;
import com.example.ecommerce.cartservice.dto.CartResponse;
import com.example.ecommerce.cartservice.dto.UpdateCartItemRequest;
import com.example.ecommerce.cartservice.entity.Cart;
import com.example.ecommerce.cartservice.entity.CartItem;
import com.example.ecommerce.cartservice.entity.CartStatus;
import com.example.ecommerce.cartservice.exception.CartItemNotFoundException;
import com.example.ecommerce.cartservice.exception.InvalidCartOperationException;
import com.example.ecommerce.cartservice.repository.CartRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final ProductCatalogClient productCatalogClient;

    public CartService(CartRepository cartRepository, ProductCatalogClient productCatalogClient) {
        this.cartRepository = cartRepository;
        this.productCatalogClient = productCatalogClient;
    }

    @Transactional(readOnly = true)
    public CartResponse getCurrentCart(Long userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .map(this::toResponse)
            .orElseGet(() -> emptyCart(userId));
    }

    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        ProductCatalogItem product = productCatalogClient.getProduct(request.productId());
        Cart cart = findOrCreateActiveCart(userId);
        try {
            cart.addOrIncrementItem(product.id(), product.name(), product.price(), request.quantity());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InvalidCartOperationException(ex.getMessage());
        }
        return toResponse(cartRepository.save(cart));
    }

    public CartResponse updateItem(Long userId, Long productId, UpdateCartItemRequest request) {
        ProductCatalogItem product = productCatalogClient.getProduct(productId);
        Cart cart = findActiveCart(userId);
        boolean exists = cart.getItems().stream().anyMatch(item -> item.getProductId().equals(productId));
        if (!exists) {
            throw new CartItemNotFoundException();
        }
        try {
            cart.updateItem(product.id(), product.name(), product.price(), request.quantity());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new InvalidCartOperationException(ex.getMessage());
        }
        return toResponse(cartRepository.save(cart));
    }

    public void removeItem(Long userId, Long productId) {
        Cart cart = findActiveCart(userId);
        if (!cart.removeItem(productId)) {
            throw new CartItemNotFoundException();
        }
        cartRepository.save(cart);
    }

    public void clearCart(Long userId) {
        cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .ifPresent(cart -> {
                cart.clearItems();
                cartRepository.save(cart);
            });
    }

    private Cart findOrCreateActiveCart(Long userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .orElseGet(() -> Cart.createActive(userId));
    }

    private Cart findActiveCart(Long userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
            .orElseThrow(CartItemNotFoundException::new);
    }

    private CartResponse emptyCart(Long userId) {
        return new CartResponse(null, userId, CartStatus.ACTIVE, List.of(), BigDecimal.ZERO);
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
            .map(this::toItemResponse)
            .toList();
        return new CartResponse(cart.getId(), cart.getUserId(), cart.getStatus(), items, cart.subtotal());
    }

    private CartItemResponse toItemResponse(CartItem item) {
        return new CartItemResponse(
            item.getProductId(),
            item.getProductNameSnapshot(),
            item.getUnitPriceSnapshot(),
            item.getQuantity(),
            item.lineTotal()
        );
    }
}
```

- [ ] **Step 4: Run service tests and verify green**

Run:

```powershell
mvn -pl cart-service -Dtest=CartServiceTests test
```

Expected: service tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add cart-service/src/main/java/com/example/ecommerce/cartservice/service cart-service/src/test/java/com/example/ecommerce/cartservice/service
git commit -m "feat: add cart service behavior"
```

---

## Task 7: Add Cart Controller, Security, And Error Handling

**Files:**
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/controller/CartController.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/config/GatewayUser.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/config/GatewayIdentityAuthenticationFilter.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/config/SecurityConfig.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/config/OpenApiConfig.java`
- Create: `cart-service/src/main/java/com/example/ecommerce/cartservice/exception/GlobalExceptionHandler.java`
- Create tests:
  - `CartControllerTests`
  - `SecurityConfigTests`
  - `OpenApiEndpointTests`

- [ ] **Step 1: Write controller tests**

Create `CartControllerTests.java` with this skeleton:

```java
@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    private static Authentication authentication(Long userId) {
        GatewayUser user = new GatewayUser(userId, "customer@example.com", List.of("USER"));
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }
}
```

Add these test bodies:

```java
@Test
void getCurrentCartReturnsCurrentUserCart() throws Exception {
    when(cartService.getCurrentCart(10L)).thenReturn(cartResponse());

    mockMvc.perform(get("/api/cart").principal(authentication(10L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(10))
        .andExpect(jsonPath("$.items[0].productId").value(20));
}

@Test
void addItemWithValidRequestReturnsUpdatedCart() throws Exception {
    AddCartItemRequest request = new AddCartItemRequest(20L, 2);
    when(cartService.addItem(10L, request)).thenReturn(cartResponse());

    mockMvc.perform(post("/api/cart/items")
            .principal(authentication(10L))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotal").value(39.98));
}

@Test
void addItemWithInvalidQuantityReturnsBadRequest() throws Exception {
    AddCartItemRequest request = new AddCartItemRequest(20L, 0);

    mockMvc.perform(post("/api/cart/items")
            .principal(authentication(10L))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
}

@Test
void missingItemReturnsNotFound() throws Exception {
    doThrow(new CartItemNotFoundException()).when(cartService).removeItem(10L, 20L);

    mockMvc.perform(delete("/api/cart/items/20").principal(authentication(10L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Cart item not found"));
}

@Test
void unavailableProductCatalogReturnsServiceUnavailable() throws Exception {
    AddCartItemRequest request = new AddCartItemRequest(20L, 1);
    doThrow(new ProductCatalogUnavailableException()).when(cartService).addItem(10L, request);

    mockMvc.perform(post("/api/cart/items")
            .principal(authentication(10L))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value("Product catalog unavailable"));
}
```

Use this helper:

```java
private static CartResponse cartResponse() {
    return new CartResponse(
        1L,
        10L,
        CartStatus.ACTIVE,
        List.of(new CartItemResponse(20L, "Pour Over", new BigDecimal("19.99"), 2, new BigDecimal("39.98"))),
        new BigDecimal("39.98")
    );
}
```

- [ ] **Step 2: Run controller tests and verify red**

Run:

```powershell
mvn -pl cart-service -Dtest=CartControllerTests test
```

Expected: compilation fails because controller and handler do not exist.

- [ ] **Step 3: Implement controller and global handler**

Create controller with this shape:

```java
@Validated
@RestController
@RequestMapping("/api/cart")
class CartController {

    private final CartService cartService;

    CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    CartResponse getCurrentCart(Authentication authentication) {
        return cartService.getCurrentCart(currentUser(authentication).userId());
    }

    @PostMapping("/items")
    CartResponse addItem(Authentication authentication, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(currentUser(authentication).userId(), request);
    }

    @PutMapping("/items/{productId}")
    CartResponse updateItem(
        Authentication authentication,
        @PathVariable Long productId,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateItem(currentUser(authentication).userId(), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeItem(Authentication authentication, @PathVariable Long productId) {
        cartService.removeItem(currentUser(authentication).userId(), productId);
    }

    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearCart(Authentication authentication) {
        cartService.clearCart(currentUser(authentication).userId());
    }

    private GatewayUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof GatewayUser user)) {
            throw new MissingUserIdentityException();
        }
        return user;
    }
}
```

Create `GlobalExceptionHandler` by copying the product-service handler and mapping cart exceptions:

```java
@ExceptionHandler(CartItemNotFoundException.class)
ResponseEntity<ApiErrorResponse> handleCartItemNotFound(CartItemNotFoundException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
}

@ExceptionHandler(ProductNotFoundException.class)
ResponseEntity<ApiErrorResponse> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
}

@ExceptionHandler(ProductCatalogUnavailableException.class)
ResponseEntity<ApiErrorResponse> handleProductCatalogUnavailable(
    ProductCatalogUnavailableException ex,
    HttpServletRequest request
) {
    return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, List.of());
}

@ExceptionHandler({InvalidCartOperationException.class})
ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
}

@ExceptionHandler(MissingUserIdentityException.class)
ResponseEntity<ApiErrorResponse> handleMissingIdentity(MissingUserIdentityException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, List.of());
}
```

- [ ] **Step 4: Run controller tests and verify green**

Run:

```powershell
mvn -pl cart-service -Dtest=CartControllerTests test
```

Expected: controller tests pass.

- [ ] **Step 5: Write security tests**

Create `SecurityConfigTests.java`:

```java
@SpringBootTest(classes = CartServiceApplication.class, properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:cart_service_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class SecurityConfigTests {
}
```

Add tests:

```java
@Test
void cartApiWithoutGatewayUserIdIsUnauthorized()

@Test
void cartApiWithGatewayUserIdReachesController()

@Test
void actuatorHealthSucceedsWithoutGatewayHeaders()
```

- [ ] **Step 6: Implement security**

Create `GatewayUser.java`:

```java
package com.example.ecommerce.cartservice.config;

import java.util.List;

public record GatewayUser(Long userId, String email, List<String> roles) {
}
```

Create `GatewayIdentityAuthenticationFilter`:

```java
package com.example.ecommerce.cartservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class GatewayIdentityAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String ROLES_HEADER = "X-User-Roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Long userId = parseUserId(request.getHeader(USER_ID_HEADER));
        if (userId != null) {
            GatewayUser user = new GatewayUser(userId, request.getHeader(USER_EMAIL_HEADER), roles(request));
            List<SimpleGrantedAuthority> authorities = user.roles().stream()
                .map(GatewayIdentityAuthenticationFilter::toAuthority)
                .toList();
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private static Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> roles(HttpServletRequest request) {
        String rolesHeader = request.getHeader(ROLES_HEADER);
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rolesHeader.split(","))
            .map(String::trim)
            .filter(role -> !role.isBlank())
            .toList();
    }

    private static SimpleGrantedAuthority toAuthority(String role) {
        if (role.startsWith(ROLE_PREFIX)) {
            return new SimpleGrantedAuthority(role);
        }
        return new SimpleGrantedAuthority(ROLE_PREFIX + role);
    }
}
```

Create `SecurityConfig`:

```java
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        GatewayIdentityAuthenticationFilter gatewayIdentityAuthenticationFilter
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/v3/api-docs/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**"
                ).permitAll()
                .requestMatchers("/api/cart/**").authenticated()
                .anyRequest().authenticated())
            .addFilterBefore(gatewayIdentityAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

- [ ] **Step 7: Add OpenAPI config and test**

Create `OpenApiConfig.java`:

```java
package com.example.ecommerce.cartservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI cartServiceOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Cart Service API")
                .version("0.0.1")
                .description("Cart APIs for the e-commerce microservices system"));
    }
}
```

Create `OpenApiEndpointTests.java`:

```java
package com.example.ecommerce.cartservice.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ecommerce.cartservice.CartServiceApplication;
import com.example.ecommerce.cartservice.client.ProductCatalogClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = CartServiceApplication.class, properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:cart_service_openapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class OpenApiEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductCatalogClient productCatalogClient;

    @Test
    void openApiDocsAreAvailable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Cart Service API"));
    }
}
```

- [ ] **Step 8: Run web/config tests**

Run:

```powershell
mvn -pl cart-service -Dtest=CartControllerTests,SecurityConfigTests,OpenApiEndpointTests test
```

Expected: tests pass.

- [ ] **Step 9: Commit**

Run:

```powershell
git add cart-service/src/main/java/com/example/ecommerce/cartservice/controller cart-service/src/main/java/com/example/ecommerce/cartservice/config cart-service/src/main/java/com/example/ecommerce/cartservice/exception/GlobalExceptionHandler.java cart-service/src/test/java/com/example/ecommerce/cartservice/controller cart-service/src/test/java/com/example/ecommerce/cartservice/config
git commit -m "feat: add cart API and security"
```

---

## Task 8: Wire Docker Compose, CI, And Documentation

**Files:**
- Create: `cart-service/Dockerfile`
- Modify: `docker-compose.yml`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Modify: `api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`

- [ ] **Step 1: Create Dockerfile**

Create `cart-service/Dockerfile`:

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
COPY cart-service/src cart-service/src

RUN mvn -pl cart-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/cart-service/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: Update existing Dockerfiles for new module POM**

Add this line to each existing service Dockerfile build stage after `COPY inventory-service/pom.xml ...`:

```dockerfile
COPY cart-service/pom.xml cart-service/pom.xml
```

Files:

- `eureka-server/Dockerfile`
- `api-gateway/Dockerfile`
- `auth-service/Dockerfile`
- `product-service/Dockerfile`
- `inventory-service/Dockerfile`

- [ ] **Step 3: Update Docker Compose**

Add `cart-postgres`:

```yaml
  cart-postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: cart_db
      POSTGRES_USER: ecommerce
      POSTGRES_PASSWORD: ecommerce
    ports:
      - "127.0.0.1:5435:5432"
    volumes:
      - cart-postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ecommerce -d cart_db"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 10s
```

Add `cart-service`:

```yaml
  cart-service:
    build:
      context: .
      dockerfile: cart-service/Dockerfile
    container_name: ecommerce-cart-service
    depends_on:
      cart-postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
      product-service:
        condition: service_healthy
    restart: on-failure
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: jdbc:postgresql://cart-postgres:5432/cart_db
      SPRING_DATASOURCE_USERNAME: ecommerce
      SPRING_DATASOURCE_PASSWORD: ecommerce
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      EUREKA_INSTANCE_LEASE_RENEWAL_INTERVAL_IN_SECONDS: 5
      EUREKA_INSTANCE_LEASE_EXPIRATION_DURATION_IN_SECONDS: 15
      PRODUCT_SERVICE_BASE_URL: http://product-service
    ports:
      - "8084:8084"
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s
```

Add `cart-postgres-data:` to volumes.

Add `cart-service` as a healthy dependency of `api-gateway`.

- [ ] **Step 4: Update CI**

In `.github/workflows/ci.yml`, append `cart-service` to Docker build list:

```yaml
          docker compose build \
            eureka-server \
            api-gateway \
            auth-service \
            product-service \
            inventory-service \
            cart-service
```

- [ ] **Step 5: Update README**

Document:

```powershell
mvn -pl cart-service spring-boot:run
docker compose up --build postgres product-postgres inventory-postgres cart-postgres eureka-server auth-service product-service inventory-service cart-service api-gateway
curl.exe http://localhost:8080/api/cart -H "Authorization: Bearer <token>"
curl.exe -X POST http://localhost:8080/api/cart/items -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"productId":1,"quantity":2}'
```

- [ ] **Step 6: Run config checks**

Run:

```powershell
docker compose config --quiet
mvn -pl api-gateway -Dtest=GatewayRoutesTests test
```

Expected: both commands pass.

- [ ] **Step 7: Commit**

Run:

```powershell
git add cart-service/Dockerfile docker-compose.yml .github/workflows/ci.yml README.md eureka-server/Dockerfile api-gateway/Dockerfile auth-service/Dockerfile product-service/Dockerfile inventory-service/Dockerfile api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java
git commit -m "chore: wire cart service runtime"
```

---

## Task 9: Final Verification And Review

**Files:**
- No new files.

- [ ] **Step 1: Run cart-service tests**

Run:

```powershell
mvn -pl cart-service test
```

Expected: cart-service tests pass.

- [ ] **Step 2: Run full Maven tests**

Run:

```powershell
mvn -B -ntp clean test
```

Expected: reactor `BUILD SUCCESS`.

- [ ] **Step 3: Count tests**

Run:

```powershell
$totals = [ordered]@{tests=0; failures=0; errors=0; skipped=0}; Get-ChildItem -Path . -Recurse -Filter 'TEST-*.xml' | ForEach-Object { [xml]$xml = Get-Content $_.FullName; $suite = $xml.testsuite; $totals.tests += [int]$suite.tests; $totals.failures += [int]$suite.failures; $totals.errors += [int]$suite.errors; $totals.skipped += [int]$suite.skipped }; $totals.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }
```

Expected: failures `0`, errors `0`.

- [ ] **Step 4: Validate Docker Compose**

Run:

```powershell
docker compose config --quiet
```

Expected: exit code `0`.

- [ ] **Step 5: Build Docker images**

Run:

```powershell
docker compose build eureka-server api-gateway auth-service product-service inventory-service cart-service
```

Expected: all six service images build.

- [ ] **Step 6: Inspect git diff**

Run:

```powershell
git status --short --branch
git log --oneline --decorate -5
```

Expected: branch `cart-service` contains small commits for product lookup, module skeleton, persistence, DTOs/exceptions, client, service behavior, API/security, and runtime wiring.

- [ ] **Step 7: Push branch**

Run:

```powershell
git push origin cart-service
```

Expected: remote branch updates.

- [ ] **Step 8: Request code review**

Use `superpowers:requesting-code-review` before merge readiness. Review focus:

- Cart ownership and boundaries.
- Product lookup contract.
- Security header handling.
- Service-layer behavior.
- Docker Compose and CI updates.
