# Inventory Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `inventory-service` with admin stock management APIs, reservation/release/deduction business logic, PostgreSQL persistence, OpenAPI docs, Docker Compose wiring, and focused tests.

**Architecture:** `inventory-service` is a standalone Spring Boot MVC service registered with Eureka and routed through `api-gateway` at `/api/inventory/**`. It owns `inventory_db`, stores external `productId` and `orderId` references only, and keeps reservation logic in service classes so later Kafka consumers can call the same methods. Kafka wiring is deferred until the dedicated event-flow milestone.

**Tech Stack:** Java 21, Maven, Spring Boot 3.5.14, Spring Cloud 2025.0.2, Spring Web MVC, Spring Security, Spring Data JPA, Spring Validation, PostgreSQL, H2 for tests, Springdoc OpenAPI 2.8.17, Eureka Client, Actuator, JUnit 5, Mockito, Docker Compose.

---

## Scope Check

This plan covers only `inventory-service`:

- Inventory item persistence per product ID.
- Admin stock set/adjust/read APIs.
- Stock reservation service logic.
- Reservation release and deduction logic.
- Reservation simulation APIs for local testing.
- Admin-only security for all `/api/inventory/**` routes.
- PostgreSQL database wiring, Dockerfile, Docker Compose, README.

This plan does not implement Kafka consumers/producers, product-service REST validation, order-service integration, payment-service integration, Flyway/Liquibase, or distributed saga orchestration.

## API Contract

Admin-only endpoints:

- `GET /api/inventory/items`
- `GET /api/inventory/items/{productId}`
- `PUT /api/inventory/items/{productId}`
- `POST /api/inventory/items/{productId}/adjust`
- `POST /api/inventory/reservations`
- `POST /api/inventory/reservations/{orderId}/release`
- `POST /api/inventory/reservations/{orderId}/deduct`

`GET /api/inventory/items` supports:

- `page`: default `0`.
- `size`: default `20`.

Request body examples:

```json
{
  "availableQuantity": 25
}
```

```json
{
  "delta": -3
}
```

```json
{
  "orderId": 1001,
  "items": [
    { "productId": 10, "quantity": 2 },
    { "productId": 11, "quantity": 1 }
  ]
}
```

## Data Model

Table: `inventory_items`

- `id`: generated primary key.
- `product_id`: unique, required external product reference.
- `available_quantity`: integer, required, non-negative.
- `reserved_quantity`: integer, required, non-negative.
- `created_at`: creation timestamp.
- `updated_at`: update timestamp.

Table: `stock_reservations`

- `id`: generated primary key.
- `order_id`: required external order reference.
- `product_id`: required external product reference.
- `quantity`: integer, required, positive.
- `status`: enum string: `RESERVED`, `RELEASED`, `DEDUCTED`, `FAILED`.
- `failure_reason`: optional, max 500.
- `created_at`: creation timestamp.
- `updated_at`: update timestamp.

## File Structure

Repository root: `D:/spring/.worktrees/inventory-service`

Files created by this plan:

- `inventory-service/pom.xml`
- `inventory-service/Dockerfile`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/InventoryServiceApplication.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/GatewayIdentityAuthenticationFilter.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/OpenApiConfig.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/SecurityConfig.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/controller/InventoryController.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/controller/StockReservationController.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/dto/InventoryAdjustmentRequest.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/dto/InventoryItemResponse.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/dto/ReservationItemRequest.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/dto/ReserveStockRequest.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/dto/StockLevelRequest.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/dto/StockReservationResponse.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/dto/StockReservationResultResponse.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/entity/InventoryItem.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/entity/ReservationStatus.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/entity/StockReservation.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/exception/ApiErrorResponse.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/exception/DuplicateReservationException.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/exception/GlobalExceptionHandler.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/exception/InvalidStockOperationException.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/exception/ResourceNotFoundException.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/repository/InventoryItemRepository.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/repository/StockReservationRepository.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/service/InventoryService.java`
- `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/service/StockReservationService.java`
- `inventory-service/src/main/resources/application.yml`
- focused tests under `inventory-service/src/test/java/com/example/ecommerce/inventoryservice/**`

Files modified by this plan:

- `pom.xml`
- `api-gateway/Dockerfile`
- `auth-service/Dockerfile`
- `eureka-server/Dockerfile`
- `product-service/Dockerfile`
- `docker-compose.yml`
- `README.md`

---

## Task 1: Module Skeleton, Configuration, and Security Foundation

**Files:**

- Create: `inventory-service/pom.xml`
- Create: `inventory-service/src/test/java/com/example/ecommerce/inventoryservice/InventoryServiceApplicationTests.java`
- Create: `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/InventoryServiceApplication.java`
- Create: `inventory-service/src/main/resources/application.yml`
- Create: `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/GatewayIdentityAuthenticationFilter.java`
- Create: `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/SecurityConfig.java`
- Create: `inventory-service/src/main/java/com/example/ecommerce/inventoryservice/config/OpenApiConfig.java`
- Create tests under `inventory-service/src/test/java/com/example/ecommerce/inventoryservice/config`
- Modify: `pom.xml`

- [ ] **Step 1: Create `inventory-service/pom.xml`**

Use the same parent as `product-service`:

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

- [ ] **Step 2: Register `inventory-service` in root `pom.xml`**

Set modules to:

```xml
<modules>
    <module>eureka-server</module>
    <module>api-gateway</module>
    <module>auth-service</module>
    <module>product-service</module>
    <module>inventory-service</module>
</modules>
```

- [ ] **Step 3: Write the red context test**

Create `InventoryServiceApplicationTests.java`:

```java
package com.example.ecommerce.inventoryservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = InventoryServiceApplication.class,
    properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:inventory_service_context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
class InventoryServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

Run:

```powershell
mvn -pl inventory-service -am test
```

Expected: compilation fails because `InventoryServiceApplication` does not exist.

- [ ] **Step 4: Create application and runtime config**

`InventoryServiceApplication` is a standard `@SpringBootApplication`.

`application.yml`:

```yaml
spring:
  application:
    name: inventory-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5434/inventory_db}
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
  port: 8083

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

---
spring:
  config:
    activate:
      on-profile: local
  jpa:
    hibernate:
      ddl-auto: update
```

- [ ] **Step 5: Create security foundation and tests**

`GatewayIdentityAuthenticationFilter` mirrors product-service behavior:

- Reads `X-User-Roles`.
- Splits comma-separated roles.
- Trims blank segments.
- Creates `ROLE_<role>` authorities, preserving already-prefixed `ROLE_` values.
- Leaves missing or empty roles anonymous.

`SecurityConfig`:

- disables CSRF
- uses stateless sessions
- permits health/info/docs/swagger
- requires `ADMIN` for all `/api/inventory/**`
- denies everything else unless authenticated

Security tests with a nested test controller under `/api/inventory/probe` must cover:

- `GET /api/inventory/probe` without role header is forbidden.
- `GET /api/inventory/probe` with `X-User-Roles: USER` is forbidden.
- `GET /api/inventory/probe` with `X-User-Roles: ADMIN` succeeds.
- `/actuator/health` succeeds without role headers.

- [ ] **Step 6: Create OpenAPI config and endpoint test**

`OpenApiConfig` title: `Inventory Service API`, version `v1`.

OpenAPI test must load context with H2 and disabled Eureka, then assert `/v3/api-docs` contains `Inventory Service API`.

- [ ] **Step 7: Run tests and commit**

Run:

```powershell
mvn -pl inventory-service -am test
```

Commit:

```powershell
git add pom.xml inventory-service
git commit -m "feat: add inventory service foundation"
```

---

## Task 2: Domain Model, Repositories, and Repository Tests

**Files:**

- Create entities and repositories under `inventory-service/src/main/java/com/example/ecommerce/inventoryservice`
- Create repository tests under `inventory-service/src/test/java/com/example/ecommerce/inventoryservice/repository`

- [ ] **Step 1: Create entities**

`ReservationStatus` enum:

```java
package com.example.ecommerce.inventoryservice.entity;

public enum ReservationStatus {
    RESERVED,
    RELEASED,
    DEDUCTED,
    FAILED
}
```

`InventoryItem` requirements:

- Table `inventory_items`.
- Unique required `product_id`.
- Required non-negative `availableQuantity`.
- Required non-negative `reservedQuantity`, default `0`.
- `createdAt` and `updatedAt` maintained by lifecycle callbacks.
- Static factory `create(Long productId, int availableQuantity)`.
- Method `setAvailableQuantity(int availableQuantity)`.
- Method `adjustAvailableQuantity(int delta)`.
- Method `reserve(int quantity)`.
- Method `release(int quantity)`.
- Method `deductReserved(int quantity)`.
- Getters.
- Throw `IllegalArgumentException` for negative quantities or operations that would make available/reserved negative.

`StockReservation` requirements:

- Table `stock_reservations`.
- Required `orderId`, `productId`, positive `quantity`.
- Required `ReservationStatus status`.
- Optional `failureReason` length 500.
- Timestamps via lifecycle callbacks.
- Static factory `reserved(Long orderId, Long productId, int quantity)`.
- Static factory `failed(Long orderId, Long productId, int quantity, String failureReason)`.
- Methods `release()` and `deduct()`.
- Getters.

- [ ] **Step 2: Create repositories**

`InventoryItemRepository`:

```java
Optional<InventoryItem> findByProductId(Long productId);
boolean existsByProductId(Long productId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select i from InventoryItem i where i.productId in :productIds")
List<InventoryItem> findAllByProductIdInForUpdate(@Param("productIds") Collection<Long> productIds);
```

`StockReservationRepository`:

```java
List<StockReservation> findAllByOrderIdOrderByProductIdAsc(Long orderId);
List<StockReservation> findAllByOrderIdAndStatusOrderByProductIdAsc(Long orderId, ReservationStatus status);
boolean existsByOrderIdAndStatusIn(Long orderId, Collection<ReservationStatus> statuses);
```

- [ ] **Step 3: Create repository tests**

Use `@DataJpaTest` with H2 PostgreSQL mode. Cover:

- product ID uniqueness for `InventoryItem`.
- `findByProductId` returns an item.
- `findAllByProductIdInForUpdate` returns only requested products.
- reservation lookup by order and status.
- long `failureReason` up to 500 characters is persisted.

- [ ] **Step 4: Run tests and commit**

Run:

```powershell
mvn -pl inventory-service -am test
```

Commit:

```powershell
git add inventory-service
git commit -m "feat: add inventory persistence model"
```

---

## Task 3: DTOs, Exceptions, and Business Services

**Files:**

- Create DTOs, exceptions, services, service tests.

- [ ] **Step 1: Create DTOs**

`StockLevelRequest`:

- `@NotNull @PositiveOrZero Integer availableQuantity`

`InventoryAdjustmentRequest`:

- `@NotNull Integer delta`

`ReservationItemRequest`:

- `@NotNull Long productId`
- `@NotNull @Positive Integer quantity`

`ReserveStockRequest`:

- `@NotNull Long orderId`
- `@NotEmpty @Valid List<ReservationItemRequest> items`

`InventoryItemResponse`:

- `Long id`
- `Long productId`
- `int availableQuantity`
- `int reservedQuantity`
- `Instant createdAt`
- `Instant updatedAt`

`StockReservationResponse`:

- `Long id`
- `Long orderId`
- `Long productId`
- `int quantity`
- `ReservationStatus status`
- `String failureReason`
- `Instant createdAt`
- `Instant updatedAt`

`StockReservationResultResponse`:

- `Long orderId`
- `ReservationStatus status`
- `List<StockReservationResponse> reservations`

- [ ] **Step 2: Create exceptions and error response**

Create:

- `ResourceNotFoundException`
- `DuplicateReservationException`
- `InvalidStockOperationException`
- `ApiErrorResponse`
- `GlobalExceptionHandler`

Messages:

- Missing stock: `Inventory item not found`
- Duplicate reservation: `Order already has an active reservation`
- Insufficient stock: `Insufficient stock for product <productId>`
- Duplicate product in request: `Duplicate product in reservation request`
- Invalid transition: `Invalid stock reservation transition`

`GlobalExceptionHandler` mirrors product-service shape:

- validation -> 400 with sorted field details
- `ResourceNotFoundException` -> 404
- `DuplicateReservationException` and `InvalidStockOperationException` -> 409
- `AccessDeniedException` -> 403
- common MVC request exceptions -> 400/405/415 preserving `Allow` on 405
- fallback -> 500 with stable non-leaking message and logging

- [ ] **Step 3: Create `InventoryService`**

Methods:

```java
InventoryItemResponse setStock(Long productId, StockLevelRequest request);
InventoryItemResponse adjustStock(Long productId, InventoryAdjustmentRequest request);
InventoryItemResponse getByProductId(Long productId);
Page<InventoryItemResponse> list(Pageable pageable);
```

Rules:

- `setStock` creates an item when missing, updates available quantity when present, and leaves reserved quantity unchanged.
- `adjustStock` rejects `delta == 0` with `InvalidStockOperationException("Stock adjustment delta must not be zero")`.
- `adjustStock` rejects adjustments that make available quantity negative.
- `getByProductId` throws `ResourceNotFoundException("Inventory item not found")`.

- [ ] **Step 4: Create `StockReservationService`**

Methods:

```java
StockReservationResultResponse reserve(ReserveStockRequest request);
StockReservationResultResponse release(Long orderId);
StockReservationResultResponse deduct(Long orderId);
```

Rules:

- Reject duplicate product IDs in a request.
- Reject a reservation when the order already has `RESERVED` or `DEDUCTED` reservations.
- Reserve all items only when every product exists and has enough available quantity.
- On successful reserve: decrease available, increase reserved, create `RESERVED` rows, return status `RESERVED`.
- On failed reserve: create `FAILED` rows for requested items, do not move stock, return status `FAILED`.
- Release: `RESERVED` -> `RELEASED`, available increases, reserved decreases.
- Release is idempotent for `RELEASED` or `FAILED` rows.
- Release rejects `DEDUCTED` rows.
- Deduct: `RESERVED` -> `DEDUCTED`, reserved decreases, available unchanged.
- Deduct is idempotent for `DEDUCTED` rows.
- Deduct rejects `RELEASED` or `FAILED` rows.

- [ ] **Step 5: Create service tests**

`InventoryServiceTests`:

- setStock creates a new item.
- setStock updates available and preserves reserved.
- adjustStock increases available.
- adjustStock rejects zero delta.
- adjustStock rejects negative result.
- getByProductId rejects missing item.

`StockReservationServiceTests`:

- reserve succeeds and moves available/reserved quantities.
- reserve rejects duplicate product IDs in one request.
- reserve fails without stock movement when item is missing.
- reserve fails without stock movement when quantity is insufficient.
- reserve rejects an order with an active reservation.
- release returns stock and marks reservations released.
- release rejects deducted reservations.
- deduct clears reserved quantity and marks reservations deducted.
- deduct rejects released reservations.

- [ ] **Step 6: Run tests and commit**

Run:

```powershell
mvn -pl inventory-service -am test
```

Commit:

```powershell
git add inventory-service
git commit -m "feat: add inventory business logic"
```

---

## Task 4: Controllers and API Tests

**Files:**

- Create controllers and controller/security tests.

- [ ] **Step 1: Create controllers**

`InventoryController`:

- base `/api/inventory/items`
- `GET /api/inventory/items` -> page of stock items
- `GET /api/inventory/items/{productId}` -> stock detail
- `PUT /api/inventory/items/{productId}` -> set stock
- `POST /api/inventory/items/{productId}/adjust` -> adjust available stock

`StockReservationController`:

- base `/api/inventory/reservations`
- `POST /api/inventory/reservations` -> reserve stock
- `POST /api/inventory/reservations/{orderId}/release` -> release stock
- `POST /api/inventory/reservations/{orderId}/deduct` -> deduct reserved stock

Controllers contain only HTTP mapping, validation, status codes, and service delegation.

- [ ] **Step 2: Create controller tests**

Use `@WebMvcTest`, mocked services, and `@AutoConfigureMockMvc(addFilters = false)`.

Cover:

- list inventory items returns 200 and passes pageable.
- get item returns 200.
- missing item returns 404.
- set stock valid request returns 200.
- set stock invalid negative quantity returns 400 field details.
- adjust stock valid request returns 200.
- invalid stock operation returns 409.
- reserve stock valid request returns 200.
- reserve stock invalid empty items returns 400 field details.
- duplicate reservation returns 409.
- release reservation returns 200.
- deduct reservation returns 200.

- [ ] **Step 3: Replace temporary security probe tests with real endpoint tests**

After real controllers exist, security tests must use real `/api/inventory/**` endpoints with mocked services:

- `GET /api/inventory/items` without `X-User-Roles` is forbidden.
- `GET /api/inventory/items` with `X-User-Roles: USER` is forbidden.
- `GET /api/inventory/items` with `X-User-Roles: ADMIN` reaches the controller.
- `PUT /api/inventory/items/{productId}` with `X-User-Roles: ADMIN` reaches the controller.
- `/actuator/health` remains public.

- [ ] **Step 4: Run tests and commit**

Run:

```powershell
mvn -pl inventory-service -am test
```

Commit:

```powershell
git add inventory-service
git commit -m "feat: expose inventory endpoints"
```

---

## Task 5: Docker Compose, Dockerfiles, and README

**Files:**

- Create `inventory-service/Dockerfile`
- Modify root service Dockerfiles, Compose, README

- [ ] **Step 1: Create `inventory-service/Dockerfile`**

The Dockerfile mirrors `product-service/Dockerfile`, copies all module POMs including `inventory-service/pom.xml`, copies `inventory-service/src`, packages `inventory-service`, exposes `8083`, and runs `/app/app.jar`.

- [ ] **Step 2: Update existing Dockerfiles for reactor compatibility**

Because root `pom.xml` now lists `inventory-service`, these Dockerfiles must copy `inventory-service/pom.xml`:

- `eureka-server/Dockerfile`
- `api-gateway/Dockerfile`
- `auth-service/Dockerfile`
- `product-service/Dockerfile`

- [ ] **Step 3: Update Compose**

Add `inventory-postgres`:

- image `postgres:17-alpine`
- database `inventory_db`
- user/password `ecommerce`
- bind `127.0.0.1:5434:5432`
- named volume `inventory-postgres-data`
- healthcheck `pg_isready -U ecommerce -d inventory_db`
- no fixed `container_name`

Add `inventory-service`:

- build from `inventory-service/Dockerfile`
- container `ecommerce-inventory-service`
- depends on healthy `inventory-postgres` and `eureka-server`
- environment:
  - `SPRING_PROFILES_ACTIVE=local`
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://inventory-postgres:5432/inventory_db`
  - `SPRING_DATASOURCE_USERNAME=ecommerce`
  - `SPRING_DATASOURCE_PASSWORD=ecommerce`
  - `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/`
  - `EUREKA_INSTANCE_LEASE_RENEWAL_INTERVAL_IN_SECONDS=5`
  - `EUREKA_INSTANCE_LEASE_EXPIRATION_DURATION_IN_SECONDS=15`
- port `8083:8083`
- healthcheck `http://localhost:8083/actuator/health`

Update `api-gateway`:

- depends on healthy `inventory-service`
- keeps `EUREKA_CLIENT_REGISTRY_FETCH_INTERVAL_SECONDS=5`

- [ ] **Step 4: Update README**

README must include:

- current milestone includes `inventory-service`
- build/test commands for inventory-service
- local run instructions for inventory-service
- Compose command includes `inventory-postgres inventory-service`
- Inventory Swagger URL `http://localhost:8083/swagger-ui.html`
- admin inventory curl examples through gateway using `Authorization: Bearer <token>`
- note that admin token requires `ADMIN` role claim
- local-development-only note for bundled DB credentials/JWT secret

- [ ] **Step 5: Verify and commit**

Run:

```powershell
mvn -pl inventory-service -am test
docker compose config --quiet
docker compose build eureka-server api-gateway auth-service product-service inventory-service
git diff --check
```

Commit:

```powershell
git add inventory-service/Dockerfile eureka-server/Dockerfile api-gateway/Dockerfile auth-service/Dockerfile product-service/Dockerfile docker-compose.yml README.md
git commit -m "chore: wire inventory service into local runtime"
```

---

## Task 6: Final Review and Handoff

- [ ] **Step 1: Run final verification**

Run:

```powershell
git diff --check
mvn -pl inventory-service -am test
mvn -pl inventory-service -am package
docker compose config --quiet
docker compose build eureka-server api-gateway auth-service product-service inventory-service
git status --short
```

Expected:

- no whitespace errors
- inventory-service tests pass
- inventory-service package passes
- Compose config valid
- Docker builds pass when Docker daemon is running
- working tree clean

- [ ] **Step 2: Push branch**

Run:

```powershell
git push -u origin inventory-service
```

- [ ] **Step 3: Report completion**

Include:

- files changed
- why changed
- tests run
- Docker status
- how to run
- next recommended plan: `cart-service` or Kafka event-flow preparation after order/payment exist

## Plan Self-Review

Spec coverage in this plan:

- Covers stock CRUD-like management through set/adjust/read APIs.
- Covers reserve, release, and deduct stock business rules.
- Covers admin-only authorization for inventory APIs.
- Covers separate `inventory_db` database ownership.
- Covers DTO validation and consistent error responses.
- Covers OpenAPI and actuator health endpoints.
- Covers Dockerfile, Docker Compose, README, and tests.

Deferred items:

- Kafka consumers/producers for order/payment events.
- Product-service REST validation.
- Order-service, payment-service, and notification-service integration.
- Database migrations beyond local `ddl-auto=update`.
