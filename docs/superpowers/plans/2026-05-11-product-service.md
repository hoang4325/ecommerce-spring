# Product Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `product-service` with product/category catalog APIs, public catalog reads, admin-only catalog management, PostgreSQL persistence, OpenAPI docs, Docker Compose wiring, and focused tests.

**Architecture:** `product-service` is a standalone Spring Boot MVC service registered with Eureka and routed through `api-gateway` at `/api/products/**` and `/api/categories/**`. It owns catalog data in its own `product_db`, never owns stock, and authorizes write operations from gateway-forwarded `X-User-Roles` identity headers. Public `GET` catalog endpoints remain unauthenticated.

**Tech Stack:** Java 21, Maven, Spring Boot 3.5.14, Spring Cloud 2025.0.2, Spring Web MVC, Spring Security, Spring Data JPA, PostgreSQL, H2 for tests, Spring Validation, Springdoc OpenAPI 2.8.17, Eureka Client, Actuator, JUnit 5, Mockito, Docker Compose.

---

## Scope Check

This plan covers only `product-service`:

- Category CRUD.
- Product CRUD.
- Product listing and detail.
- Search product by keyword.
- Filter products by category slug.
- Public read endpoints.
- Admin-only write endpoints.
- Product-owned PostgreSQL database wiring.
- Dockerfile and Docker Compose wiring.
- README updates for product-service.

This plan does not implement inventory stock, product images upload/storage, Kafka events, product recommendations, advanced full-text search, Elasticsearch, audit history, or migration tooling. Flyway/Liquibase remains deferred until the database model stabilizes beyond MVP.

## API Contract

Public endpoints:

- `GET /api/categories`
- `GET /api/categories/{slug}`
- `GET /api/products`
- `GET /api/products/{slug}`

Admin endpoints:

- `POST /api/categories`
- `PUT /api/categories/{id}`
- `DELETE /api/categories/{id}` soft-deactivates a category.
- `POST /api/products`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}` soft-deactivates a product.

`GET /api/products` supports:

- `keyword`: optional, searches product name or description case-insensitively.
- `categorySlug`: optional, filters by active category slug.
- `page`: default `0`.
- `size`: default `20`.

Request body examples:

```json
{
  "name": "Electronics",
  "slug": "electronics",
  "description": "Devices and accessories"
}
```

```json
{
  "categoryId": 1,
  "name": "Wireless Mouse",
  "slug": "wireless-mouse",
  "description": "Ergonomic wireless mouse",
  "price": 19.99,
  "imageUrl": "https://example.com/mouse.png"
}
```

## Data Model

Table: `categories`

- `id`: generated primary key.
- `name`: unique enough at service level, required.
- `slug`: unique, required, lower-case kebab-case.
- `description`: optional.
- `active`: boolean.
- `created_at`: creation timestamp.
- `updated_at`: update timestamp.

Table: `products`

- `id`: generated primary key.
- `category_id`: required foreign key to `categories`.
- `name`: required.
- `slug`: unique, required, lower-case kebab-case.
- `description`: optional.
- `price`: decimal, required, positive.
- `image_url`: optional URL string.
- `active`: boolean.
- `created_at`: creation timestamp.
- `updated_at`: update timestamp.

Product service does not store or expose stock quantity. Inventory belongs to `inventory-service`.

## Security Design

The gateway validates JWT and forwards trusted identity headers:

- `X-User-Id`
- `X-User-Email`
- `X-User-Roles`

`product-service` uses a local Spring Security filter that reads `X-User-Roles`, creates authorities like `ROLE_ADMIN`, and applies these rules:

- Public: `GET /api/products/**`, `GET /api/categories/**`, `/actuator/health`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`.
- Admin: `POST`, `PUT`, `PATCH`, `DELETE` on `/api/products/**` and `/api/categories/**`.
- Everything else denied unless authenticated by forwarded identity headers.

Direct service ports are exposed for local development only. In local MVP, identity headers are trusted only inside the Docker/local development boundary.

## File Structure

Repository root: `D:/spring/.worktrees/product-service`

Files created by this plan:

- `product-service/pom.xml`
- `product-service/Dockerfile`
- `product-service/src/main/java/com/example/ecommerce/productservice/ProductServiceApplication.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/config/GatewayIdentityAuthenticationFilter.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/config/OpenApiConfig.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/config/SecurityConfig.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/controller/CategoryController.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/controller/ProductController.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/dto/CategoryRequest.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/dto/CategoryResponse.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/dto/ProductRequest.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/dto/ProductResponse.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/entity/Category.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/entity/Product.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/exception/ApiErrorResponse.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/exception/DuplicateSlugException.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/exception/GlobalExceptionHandler.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/exception/ResourceNotFoundException.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/repository/CategoryRepository.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/repository/ProductRepository.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/service/CategoryService.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/service/ProductService.java`
- `product-service/src/main/java/com/example/ecommerce/productservice/service/SlugNormalizer.java`
- `product-service/src/main/resources/application.yml`
- focused test classes under `product-service/src/test/java/com/example/ecommerce/productservice/**`

Files modified by this plan:

- `pom.xml`
- `api-gateway/Dockerfile`
- `auth-service/Dockerfile`
- `eureka-server/Dockerfile`
- `docker-compose.yml`
- `README.md`

---

## Task 1: Module Skeleton, Configuration, and Red Context Test

**Files:**

- Create: `product-service/pom.xml`
- Create: `product-service/src/test/java/com/example/ecommerce/productservice/ProductServiceApplicationTests.java`
- Modify: `pom.xml`

- [ ] **Step 1: Create `product-service/pom.xml`**

Use parent:

```xml
<parent>
    <groupId>com.example.ecommerce</groupId>
    <artifactId>ecommerce-microservices</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

Dependencies:

- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-actuator`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17`
- `org.postgresql:postgresql` runtime
- `com.h2database:h2` test
- `spring-boot-starter-test` test
- `spring-security-test` test

- [ ] **Step 2: Register `product-service` in root `pom.xml`**

Set modules to:

```xml
<modules>
    <module>eureka-server</module>
    <module>api-gateway</module>
    <module>auth-service</module>
    <module>product-service</module>
</modules>
```

- [ ] **Step 3: Write the red context test**

Create `ProductServiceApplicationTests.java`:

```java
package com.example.ecommerce.productservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = ProductServiceApplication.class,
    properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:product_service_context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
class ProductServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Run the red test**

Run:

```powershell
mvn -pl product-service -am test
```

Expected: compilation fails because `ProductServiceApplication` does not exist.

Do not commit yet.

---

## Task 2: Application Config, Domain Model, Repositories, and Security Foundation

**Files:**

- Create application class, `application.yml`, entities, repositories, repository tests, OpenAPI config, security filter/config, security tests.

- [ ] **Step 1: Create application and runtime config**

`ProductServiceApplication` is a standard `@SpringBootApplication`.

`application.yml` must include:

```yaml
spring:
  application:
    name: product-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5433/product_db}
    username: ${SPRING_DATASOURCE_USERNAME:ecommerce}
    password: ${SPRING_DATASOURCE_PASSWORD:ecommerce}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

---
spring:
  config:
    activate:
      on-profile: local
  jpa:
    hibernate:
      ddl-auto: update

server:
  port: 8082

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true

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
```

- [ ] **Step 2: Create entities**

`Category` requirements:

- Table `categories`.
- Identity `id`.
- Required `name`.
- Unique required `slug`.
- Optional `description`.
- `active` defaults true.
- `createdAt` and `updatedAt` are maintained by lifecycle callbacks.
- Static factory `create(String name, String slug, String description)`.
- Method `update(String name, String slug, String description)`.
- Method `deactivate()`.
- Getters.

`Product` requirements:

- Table `products`.
- Identity `id`.
- Required `Category category` as `@ManyToOne(fetch = FetchType.LAZY)`.
- Required `name`.
- Unique required `slug`.
- Optional `description`.
- Required positive `BigDecimal price`.
- Optional `imageUrl` mapped to `image_url`.
- `active` defaults true.
- Timestamps via lifecycle callbacks.
- Static factory `create(Category category, String name, String slug, String description, BigDecimal price, String imageUrl)`.
- Method `update(Category category, String name, String slug, String description, BigDecimal price, String imageUrl)`.
- Method `deactivate()`.
- Getters.

- [ ] **Step 3: Create repositories and tests**

`CategoryRepository`:

```java
Optional<Category> findBySlug(String slug);
Optional<Category> findBySlugAndActiveTrue(String slug);
boolean existsBySlug(String slug);
List<Category> findAllByActiveTrueOrderByNameAsc();
```

`ProductRepository`:

```java
Optional<Product> findBySlugAndActiveTrue(String slug);
boolean existsBySlug(String slug);
Page<Product> findAllByActiveTrue(Pageable pageable);
Page<Product> findByCategorySlugAndActiveTrueAndCategoryActiveTrue(String categorySlug, Pageable pageable);
```

For keyword search, add a custom `@Query` that searches active products by lower-case name or description and optionally category slug. Use one repository method:

```java
@Query("""
    select p from Product p
    join p.category c
    where p.active = true
      and c.active = true
      and (:keyword is null or lower(p.name) like lower(concat('%', :keyword, '%'))
           or lower(coalesce(p.description, '')) like lower(concat('%', :keyword, '%')))
      and (:categorySlug is null or c.slug = :categorySlug)
    """)
Page<Product> searchActiveProducts(String keyword, String categorySlug, Pageable pageable);
```

Repository tests must cover:

- Category slug uniqueness.
- Product slug uniqueness.
- Active category listing excludes inactive.
- Product search finds keyword in name/description.
- Product search filters by category slug.
- Product detail lookup ignores inactive products.

- [ ] **Step 4: Create security foundation and tests**

`GatewayIdentityAuthenticationFilter` reads `X-User-Roles`, splits comma-separated role names, creates authorities `ROLE_<role>`, and sets an authenticated `UsernamePasswordAuthenticationToken` for the request. Empty or missing roles leaves the request anonymous.

`SecurityConfig`:

- disables CSRF
- stateless sessions
- permits health/info/docs/swagger
- permits `GET /api/products/**` and `GET /api/categories/**`
- requires `ADMIN` role for write methods on `/api/products/**` and `/api/categories/**`
- denies other requests unless authenticated

Security tests with `@SpringBootTest` and `@AutoConfigureMockMvc` must cover:

- public `GET /api/products` succeeds without headers
- admin `POST /api/categories` without `X-User-Roles` is forbidden
- admin `POST /api/categories` with `X-User-Roles: ADMIN` reaches controller validation instead of security denial

- [ ] **Step 5: Run tests**

Run:

```powershell
mvn -pl product-service -am test
```

Expected: context, repository, security, and OpenAPI tests pass.

- [ ] **Step 6: Commit foundation**

Run:

```powershell
git add pom.xml product-service
git commit -m "feat: add product service foundation"
```

---

## Task 3: DTOs, Exceptions, Slug Normalization, and Services

**Files:**

- Create DTOs, exceptions, service classes, unit tests.

- [ ] **Step 1: Create DTOs**

`CategoryRequest`:

- `@NotBlank @Size(max = 120) String name`
- `@NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 140) String slug`
- `@Size(max = 1000) String description`

`CategoryResponse`:

- `Long id`
- `String name`
- `String slug`
- `String description`
- `boolean active`
- `Instant createdAt`
- `Instant updatedAt`

`ProductRequest`:

- `@NotNull Long categoryId`
- `@NotBlank @Size(max = 180) String name`
- `@NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 200) String slug`
- `@Size(max = 4000) String description`
- `@NotNull @DecimalMin(value = "0.01") BigDecimal price`
- `@Size(max = 1000) String imageUrl`

`ProductResponse`:

- `Long id`
- `Long categoryId`
- `String categoryName`
- `String categorySlug`
- `String name`
- `String slug`
- `String description`
- `BigDecimal price`
- `String imageUrl`
- `boolean active`
- `Instant createdAt`
- `Instant updatedAt`

- [ ] **Step 2: Create exceptions and error response**

`ResourceNotFoundException` supports messages:

- `Category not found`
- `Product not found`

`DuplicateSlugException` message:

- `Slug is already in use`

`ApiErrorResponse` matches auth-service shape and defensively copies details.

`GlobalExceptionHandler` handles:

- validation -> 400 with sorted field details
- `ResourceNotFoundException` -> 404
- `DuplicateSlugException` -> 409
- `AccessDeniedException` -> 403
- common MVC request exceptions -> 400/405/415 preserving `Allow` on 405
- fallback -> 500 with stable non-leaking message and logging

- [ ] **Step 3: Create `SlugNormalizer`**

Rules:

- `normalize(String value)` trims and lowercases with `Locale.ROOT`.
- It does not generate slugs from names.
- It rejects blank values with `IllegalArgumentException("Slug must not be blank")`.

- [ ] **Step 4: Create services**

`CategoryService`:

- `create(CategoryRequest request)`
- `update(Long id, CategoryRequest request)`
- `deactivate(Long id)`
- `getBySlug(String slug)`
- `listActive()`

Rules:

- Normalize slug before duplicate checks and persistence.
- Reject duplicate slug on create.
- On update, reject duplicate slug when another category owns it.
- Deactivate category with soft delete.
- Public reads only return active categories.

`ProductService`:

- `create(ProductRequest request)`
- `update(Long id, ProductRequest request)`
- `deactivate(Long id)`
- `getBySlug(String slug)`
- `search(String keyword, String categorySlug, Pageable pageable)`

Rules:

- Normalize slug and category slug.
- Reject duplicate product slug on create/update.
- Reject inactive/missing category for product create/update.
- Soft delete products by setting active false.
- Public reads only return active products in active categories.

- [ ] **Step 5: Create service tests**

`CategoryServiceTests`:

- create normalizes slug and returns response.
- create rejects duplicate slug.
- update rejects duplicate slug owned by another category.
- deactivate marks category inactive.
- getBySlug rejects inactive/missing category.

`ProductServiceTests`:

- create rejects missing category.
- create rejects inactive category.
- create saves product with normalized slug and category.
- update rejects duplicate slug owned by another product.
- deactivate marks product inactive.
- getBySlug rejects missing/inactive product.
- search normalizes category slug and delegates pageable query.

- [ ] **Step 6: Run tests and commit**

Run:

```powershell
mvn -pl product-service -am test
```

Commit:

```powershell
git add product-service
git commit -m "feat: add product catalog business logic"
```

---

## Task 4: Controllers and API Tests

**Files:**

- Create `CategoryController`, `ProductController`, controller tests, OpenAPI endpoint tests.

- [ ] **Step 1: Create controllers**

`CategoryController`:

- base `/api/categories`
- `GET /api/categories` -> list active
- `GET /api/categories/{slug}` -> category detail
- `POST /api/categories` -> 201 create
- `PUT /api/categories/{id}` -> update
- `DELETE /api/categories/{id}` -> 204 deactivate

`ProductController`:

- base `/api/products`
- `GET /api/products` -> paged search
- `GET /api/products/{slug}` -> product detail
- `POST /api/products` -> 201 create
- `PUT /api/products/{id}` -> update
- `DELETE /api/products/{id}` -> 204 deactivate

Controllers contain HTTP mapping only and delegate to services.

- [ ] **Step 2: Create controller tests**

Use `@WebMvcTest`, mocked services, and `@AutoConfigureMockMvc(addFilters = false)` for controller-slice behavior.

Cover:

- category list returns 200
- category detail returns 200
- create category valid request returns 201
- create category invalid request returns 400 field details
- duplicate slug returns 409
- missing category returns 404
- product search returns 200 and passes keyword/category/page/size
- product detail returns 200
- create product valid request returns 201
- create product invalid price returns 400
- missing product returns 404
- delete product returns 204

- [ ] **Step 3: Create security integration tests**

Use full filters with `@SpringBootTest` and mocked services:

- public product/category `GET` works without role headers.
- admin category/product writes without `X-User-Roles` return 403.
- admin category/product writes with `X-User-Roles: ADMIN` reach controller/service.
- `X-User-Roles: USER` cannot write.

- [ ] **Step 4: Run tests and commit**

Run:

```powershell
mvn -pl product-service -am test
```

Commit:

```powershell
git add product-service
git commit -m "feat: expose product catalog endpoints"
```

---

## Task 5: Docker Compose, Dockerfiles, and README

**Files:**

- Create `product-service/Dockerfile`
- Modify root service Dockerfiles, Compose, README

- [ ] **Step 1: Create `product-service/Dockerfile`**

The Dockerfile mirrors `auth-service/Dockerfile`, copies all module POMs including `product-service/pom.xml`, copies `product-service/src`, packages `product-service`, exposes `8082`, and runs `/app/app.jar`.

- [ ] **Step 2: Update existing Dockerfiles for reactor compatibility**

Because root `pom.xml` now lists `product-service`, these Dockerfiles must copy `product-service/pom.xml`:

- `eureka-server/Dockerfile`
- `api-gateway/Dockerfile`
- `auth-service/Dockerfile`

- [ ] **Step 3: Update Compose**

Add `product-postgres`:

- image `postgres:17-alpine`
- database `product_db`
- user/password `ecommerce`
- bind `127.0.0.1:5433:5432`
- named volume `product-postgres-data`
- healthcheck with `pg_isready -U ecommerce -d product_db`

Add `product-service`:

- build from `product-service/Dockerfile`
- container `ecommerce-product-service`
- depends on `product-postgres` and `eureka-server` as healthy
- environment:
  - `SPRING_PROFILES_ACTIVE=local`
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://product-postgres:5432/product_db`
  - `SPRING_DATASOURCE_USERNAME=ecommerce`
  - `SPRING_DATASOURCE_PASSWORD=ecommerce`
  - `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/`
  - `EUREKA_INSTANCE_LEASE_RENEWAL_INTERVAL_IN_SECONDS=5`
  - `EUREKA_INSTANCE_LEASE_EXPIRATION_DURATION_IN_SECONDS=15`
- port `8082:8082`
- healthcheck `http://localhost:8082/actuator/health`

Update `api-gateway`:

- depends on healthy `product-service`
- keeps `EUREKA_CLIENT_REGISTRY_FETCH_INTERVAL_SECONDS=5`

- [ ] **Step 4: Update README**

README must include:

- current milestone includes `product-service`
- build/test commands for product-service
- Compose command includes `product-postgres product-service`
- Product Swagger URL `http://localhost:8082/swagger-ui.html`
- public catalog curl examples through gateway
- admin catalog curl examples through gateway using `Authorization: Bearer <token>`
- note that admin token requires `ADMIN` role claim

- [ ] **Step 5: Verify and commit**

Run:

```powershell
mvn -pl product-service -am test
docker compose config --quiet
docker compose build eureka-server api-gateway auth-service product-service
git diff --check
```

Commit:

```powershell
git add product-service/Dockerfile eureka-server/Dockerfile api-gateway/Dockerfile auth-service/Dockerfile docker-compose.yml README.md
git commit -m "chore: wire product service into local runtime"
```

---

## Task 6: Final Review and Handoff

- [ ] **Step 1: Run final verification**

Run:

```powershell
git diff --check
mvn -pl product-service -am test
mvn -pl product-service -am package
docker compose config --quiet
docker compose build eureka-server api-gateway auth-service product-service
git status --short
```

Expected:

- no whitespace errors
- product-service tests pass
- product-service package passes
- Compose config valid
- Docker builds pass when Docker daemon is running
- working tree clean

- [ ] **Step 2: Push branch**

Run:

```powershell
git push -u origin product-service
```

- [ ] **Step 3: Report completion**

Include:

- files changed
- why changed
- tests run
- Docker status
- how to run
- next recommended plan: `inventory-service`

## Plan Self-Review

Spec coverage in this plan:

- Covers product/category CRUD.
- Covers product listing, detail, keyword search, and category filter.
- Covers public reads and admin-only writes.
- Covers separate product database ownership.
- Covers DTO validation and consistent error responses.
- Covers OpenAPI and actuator health endpoints.
- Covers Dockerfile, Docker Compose, README, and tests.

Deferred items:

- Inventory stock.
- Product image upload/storage.
- Kafka events.
- Redis.
- Database migrations beyond local `ddl-auto=update`.
