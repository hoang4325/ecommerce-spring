# Payment Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the MVP `payment-service` that persists deterministic simulated payments, exposes user/admin payment APIs, and runs locally through Docker Compose.

**Architecture:** `payment-service` is a Spring Boot MVC/JPA service with its own PostgreSQL database and no shared entities. The gateway authenticates JWTs and forwards identity headers; payment-service scopes user operations by `X-User-Id`, stores one payment per `orderId`, and leaves Kafka/order status updates for a later branch.

**Tech Stack:** Java 21, Spring Boot 3.5.14, Spring Web MVC, Spring Security, Spring Data JPA, PostgreSQL, H2 tests, Eureka client, Springdoc OpenAPI, Maven, Docker Compose.

---

## File Structure

Create:

- `payment-service/pom.xml`: service module dependencies and build plugin.
- `payment-service/Dockerfile`: multi-module Docker build for payment-service.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/PaymentServiceApplication.java`: Spring Boot entrypoint.
- `payment-service/src/main/resources/application.yml`: datasource, Eureka, actuator, and Springdoc config.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/entity/Payment.java`: payment aggregate root and status transitions.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/entity/PaymentMethod.java`: simulated payment method enum.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/entity/PaymentStatus.java`: payment status enum.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/dto/CreatePaymentRequest.java`: user payment creation request.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/dto/PaymentResponse.java`: payment response DTO.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/dto/SimulatePaymentResult.java`: deterministic simulation enum.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/dto/UpdatePaymentStatusRequest.java`: admin status update request.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/repository/PaymentRepository.java`: payment queries.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/service/PaymentService.java`: payment use cases and mapping.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/controller/PaymentController.java`: user payment API.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/controller/AdminPaymentController.java`: admin payment API.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/config/GatewayUser.java`: authenticated gateway identity.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/config/GatewayIdentityAuthenticationFilter.java`: trusted identity header filter.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/config/SecurityConfig.java`: endpoint authorization and JSON auth entry point.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/config/OpenApiConfig.java`: Swagger metadata.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/exception/ApiErrorResponse.java`: standard error body.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/exception/GlobalExceptionHandler.java`: error mapping.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/exception/DuplicateOrderPaymentException.java`: conflict for another user's existing order payment.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/exception/InvalidPaymentOperationException.java`: invalid transition or terminal update.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/exception/MissingUserIdentityException.java`: missing trusted identity.
- `payment-service/src/main/java/com/example/ecommerce/paymentservice/exception/PaymentNotFoundException.java`: payment lookup miss.
- Tests under `payment-service/src/test/java/com/example/ecommerce/paymentservice/**`.

Modify:

- `pom.xml`: add `<module>payment-service</module>`.
- `api-gateway/src/main/resources/application.yml`: add payment-service user/admin routes.
- `api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`: assert payment routes exist.
- `.github/workflows/ci.yml`: include payment-service in sequential Docker image builds.
- `docker-compose.yml`: add `payment-postgres`, `payment-service`, volume, and gateway dependency.
- `README.md`: document payment-service endpoints and local run commands.
- Existing service Dockerfiles: add `COPY payment-service/pom.xml payment-service/pom.xml` so multi-module Docker builds stay valid.

---

### Task 1: Module Skeleton

**Files:**

- Modify: `pom.xml`
- Create: `payment-service/pom.xml`
- Create: `payment-service/src/main/java/com/example/ecommerce/paymentservice/PaymentServiceApplication.java`
- Create: `payment-service/src/main/resources/application.yml`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/PaymentServiceApplicationTests.java`

- [ ] **Step 1: Write the failing application context test**

Create `payment-service/src/test/java/com/example/ecommerce/paymentservice/PaymentServiceApplicationTests.java`:

```java
package com.example.ecommerce.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:payment_context;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false"
    }
)
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
mvn -pl payment-service test
```

Expected: FAIL because module `payment-service` does not exist in the Maven reactor.

- [ ] **Step 3: Add module POM and application skeleton**

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
    <module>payment-service</module>
</modules>
```

Create `payment-service/pom.xml`:

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

    <artifactId>payment-service</artifactId>
    <name>payment-service</name>
    <description>Payment service for the e-commerce microservices system</description>

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

Create `payment-service/src/main/java/com/example/ecommerce/paymentservice/PaymentServiceApplication.java`:

```java
package com.example.ecommerce.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

Create `payment-service/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: payment-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5437/payment_db}
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
  port: 8086

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

- [ ] **Step 4: Run test to verify GREEN**

Run:

```powershell
mvn -pl payment-service test
```

Expected: PASS with 1 test.

- [ ] **Step 5: Commit**

```powershell
git add pom.xml payment-service
git commit -m "feat: add payment service module skeleton"
```

---

### Task 2: Payment Domain And Persistence

**Files:**

- Create: `payment-service/src/main/java/com/example/ecommerce/paymentservice/entity/PaymentStatus.java`
- Create: `payment-service/src/main/java/com/example/ecommerce/paymentservice/entity/PaymentMethod.java`
- Create: `payment-service/src/main/java/com/example/ecommerce/paymentservice/entity/Payment.java`
- Create: `payment-service/src/main/java/com/example/ecommerce/paymentservice/repository/PaymentRepository.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/entity/PaymentTests.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/repository/PaymentRepositoryTests.java`

- [ ] **Step 1: Write failing domain tests**

Create `PaymentTests`:

```java
package com.example.ecommerce.paymentservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentTests {

    @Test
    void createPendingPayment() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);

        assertThat(payment.getOrderId()).isEqualTo(1000L);
        assertThat(payment.getUserId()).isEqualTo(10L);
        assertThat(payment.getAmount()).isEqualByComparingTo("99.98");
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void failedPaymentCannotBeMarkedSuccess() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);
        payment.markFailed("Card declined");

        assertThatThrownBy(payment::markSuccess)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Terminal payment cannot be changed");
    }

    @Test
    void pendingPaymentCanBeMarkedSuccess() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);

        payment.markSuccess();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void pendingPaymentCanBeMarkedFailed() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);

        payment.markFailed("Simulated card decline");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Simulated card decline");
    }

    @Test
    void createRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Payment.create(1000L, 10L, BigDecimal.ZERO, PaymentMethod.CARD))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Amount must be greater than zero");
    }

    @Test
    void terminalPaymentCannotBeChanged() {
        Payment payment = Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD);
        payment.markSuccess();

        assertThatThrownBy(() -> payment.markFailed("Late failure"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Terminal payment cannot be changed");
    }
}
```

- [ ] **Step 2: Run domain tests to verify RED**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentTests" test
```

Expected: FAIL because `Payment`, `PaymentMethod`, and `PaymentStatus` do not exist.

- [ ] **Step 3: Implement domain enums and entity**

Create `PaymentStatus`:

```java
package com.example.ecommerce.paymentservice.entity;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
```

Create `PaymentMethod`:

```java
package com.example.ecommerce.paymentservice.entity;

public enum PaymentMethod {
    COD,
    CARD,
    BANK_TRANSFER
}
```

Create `Payment` with these members:

```java
package com.example.ecommerce.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id")
    }
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Payment() {
    }

    private Payment(Long orderId, Long userId, BigDecimal amount, PaymentMethod method) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order id is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (method == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long orderId, Long userId, BigDecimal amount, PaymentMethod method) {
        return new Payment(orderId, userId, amount, method);
    }

    public void markSuccess() {
        ensureMutable();
        this.status = PaymentStatus.SUCCESS;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        ensureMutable();
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason == null || reason.isBlank() ? "Payment failed" : reason;
    }

    public boolean isTerminal() {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED;
    }

    private void ensureMutable() {
        if (isTerminal()) {
            throw new IllegalStateException("Terminal payment cannot be changed");
        }
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
```

- [ ] **Step 4: Run domain tests to verify GREEN**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentTests" test
```

Expected: PASS.

- [ ] **Step 5: Write failing repository tests**

Create `PaymentRepositoryTests`:

```java
package com.example.ecommerce.paymentservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ecommerce.paymentservice.entity.Payment;
import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:payment_repository;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentRepositoryTests {

    @Autowired
    private PaymentRepository repository;

    @Test
    void persistsPayment() {
        Payment payment = repository.saveAndFlush(samplePayment(1000L, 10L));

        assertThat(payment.getId()).isNotNull();
        assertThat(repository.findById(payment.getId())).isPresent();
    }

    @Test
    void findsByIdAndUserId() {
        Payment payment = repository.saveAndFlush(samplePayment(1000L, 10L));

        assertThat(repository.findByIdAndUserId(payment.getId(), 10L)).isPresent();
        assertThat(repository.findByIdAndUserId(payment.getId(), 11L)).isEmpty();
    }

    @Test
    void findsByOrderIdAndUserId() {
        repository.saveAndFlush(samplePayment(1000L, 10L));

        assertThat(repository.findByOrderId(1000L)).isPresent();
        assertThat(repository.findByOrderIdAndUserId(1000L, 10L)).isPresent();
        assertThat(repository.findByOrderIdAndUserId(1000L, 11L)).isEmpty();
    }

    @Test
    void filtersByStatus() {
        Payment pending = samplePayment(1000L, 10L);
        Payment success = samplePayment(1001L, 11L);
        success.markSuccess();
        repository.saveAndFlush(pending);
        repository.saveAndFlush(success);

        Page<Payment> result = repository.findByStatus(
            PaymentStatus.SUCCESS,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(result.getContent()).extracting(Payment::getStatus).containsOnly(PaymentStatus.SUCCESS);
    }

    @Test
    void enforcesUniqueOrderPayment() {
        repository.saveAndFlush(samplePayment(1000L, 10L));

        assertThatThrownBy(() -> repository.saveAndFlush(samplePayment(1000L, 10L)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Payment samplePayment(Long orderId, Long userId) {
        return Payment.create(orderId, userId, new BigDecimal("99.98"), PaymentMethod.CARD);
    }
}
```

- [ ] **Step 6: Run repository tests to verify RED**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentRepositoryTests" test
```

Expected: FAIL because `PaymentRepository` does not exist.

- [ ] **Step 7: Implement repository**

Create `PaymentRepository`:

```java
package com.example.ecommerce.paymentservice.repository;

import com.example.ecommerce.paymentservice.entity.Payment;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdAndUserId(Long id, Long userId);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndUserId(Long orderId, Long userId);

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
```

- [ ] **Step 8: Run domain and repository tests**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentTests,PaymentRepositoryTests" test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add payment-service/src/main/java/com/example/ecommerce/paymentservice/entity payment-service/src/main/java/com/example/ecommerce/paymentservice/repository payment-service/src/test/java/com/example/ecommerce/paymentservice/entity payment-service/src/test/java/com/example/ecommerce/paymentservice/repository
git commit -m "feat: add payment domain persistence"
```

---

### Task 3: DTOs, Exceptions, Security, And OpenAPI

**Files:**

- Create DTO, config, and exception files listed in File Structure.
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/dto/PaymentResponseTests.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/dto/CreatePaymentRequestValidationTests.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/dto/UpdatePaymentStatusRequestValidationTests.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/config/GatewayUserTests.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/config/SecurityConfigTests.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/config/OpenApiEndpointTests.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/exception/ApiErrorResponseTests.java`

- [ ] **Step 1: Write failing DTO and error response tests**

Create tests with these assertions:

```java
@Test
void paymentResponseCarriesFields() {
    PaymentResponse response = new PaymentResponse(
        5000L,
        1000L,
        10L,
        new BigDecimal("99.98"),
        PaymentMethod.CARD,
        PaymentStatus.SUCCESS,
        null,
        Instant.parse("2026-05-13T00:00:00Z"),
        Instant.parse("2026-05-13T00:00:01Z")
    );

    assertThat(response.paymentId()).isEqualTo(5000L);
    assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
}

@Test
void createPaymentRequestRejectsInvalidValues() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    CreatePaymentRequest request = new CreatePaymentRequest(null, BigDecimal.ZERO, null, SimulatePaymentResult.SUCCESS);

    assertThat(validator.validate(request)).hasSize(3);
}

@Test
void updatePaymentStatusRequestRejectsPendingAndLongFailureReason() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    UpdatePaymentStatusRequest request = new UpdatePaymentStatusRequest(
        PaymentStatus.PENDING,
        "x".repeat(501)
    );

    assertThat(validator.validate(request)).hasSize(2);
}

@Test
void gatewayUserCopiesAndNormalizesRoles() {
    GatewayUser user = new GatewayUser(10L, "user@example.com", List.of("ROLE_ADMIN", "USER"));

    assertThat(user.roles()).containsExactly("ADMIN", "USER");
    assertThatThrownBy(() -> user.roles().add("SERVICE")).isInstanceOf(UnsupportedOperationException.class);
}

@Test
void apiErrorResponseCopiesDetailsDefensively() {
    List<ApiErrorResponse.FieldErrorDetail> details = new ArrayList<>();
    details.add(new ApiErrorResponse.FieldErrorDetail("amount", "must be greater than 0"));

    ApiErrorResponse response = new ApiErrorResponse(
        Instant.parse("2026-05-13T00:00:00Z"),
        400,
        "Bad Request",
        "Validation failed",
        "/api/payments",
        details
    );
    details.clear();

    assertThat(response.details()).hasSize(1);
    assertThatThrownBy(() -> response.details().clear()).isInstanceOf(UnsupportedOperationException.class);
}
```

- [ ] **Step 2: Run DTO/config tests to verify RED**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentResponseTests,CreatePaymentRequestValidationTests,UpdatePaymentStatusRequestValidationTests,GatewayUserTests,ApiErrorResponseTests" test
```

Expected: FAIL because DTO/config/error classes do not exist.

- [ ] **Step 3: Implement DTOs and exceptions**

Create `SimulatePaymentResult`:

```java
package com.example.ecommerce.paymentservice.dto;

public enum SimulatePaymentResult {
    PENDING,
    SUCCESS,
    FAILED
}
```

Create `CreatePaymentRequest`:

```java
package com.example.ecommerce.paymentservice.dto;

import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreatePaymentRequest(
    @NotNull Long orderId,
    @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
    @NotNull PaymentMethod method,
    SimulatePaymentResult simulateResult
) {
}
```

Create `UpdatePaymentStatusRequest`:

```java
package com.example.ecommerce.paymentservice.dto;

import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePaymentStatusRequest(
    @NotNull PaymentStatus status,
    @Size(max = 500) String failureReason
) {

    @AssertTrue(message = "Status must be SUCCESS or FAILED")
    public boolean isTerminalStatus() {
        return status == null || status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED;
    }
}
```

Create `PaymentResponse`:

```java
package com.example.ecommerce.paymentservice.dto;

import com.example.ecommerce.paymentservice.entity.PaymentMethod;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    Long paymentId,
    Long orderId,
    Long userId,
    BigDecimal amount,
    PaymentMethod method,
    PaymentStatus status,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {
}
```

Create `ApiErrorResponse`:

```java
package com.example.ecommerce.paymentservice.exception;

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

    public ApiErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
```

Create exception classes:

```java
package com.example.ecommerce.paymentservice.exception;

public class DuplicateOrderPaymentException extends RuntimeException {
    public DuplicateOrderPaymentException() {
        super("Payment already exists for this order");
    }
}
```

```java
package com.example.ecommerce.paymentservice.exception;

public class InvalidPaymentOperationException extends RuntimeException {
    public InvalidPaymentOperationException(String message) {
        super(message);
    }
}
```

```java
package com.example.ecommerce.paymentservice.exception;

public class MissingUserIdentityException extends RuntimeException {
    public MissingUserIdentityException() {
        super("Missing user identity");
    }
}
```

```java
package com.example.ecommerce.paymentservice.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(Long id) {
        super("Payment not found: " + id);
    }

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Implement gateway identity and security**

Create `GatewayUser`:

```java
package com.example.ecommerce.paymentservice.config;

import java.util.List;

public record GatewayUser(Long id, String email, List<String> roles) {

    public GatewayUser {
        if (id == null) {
            throw new IllegalArgumentException("User id is required");
        }
        roles = roles == null
            ? List.of()
            : roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .toList();
    }
}
```

Create `GatewayIdentityAuthenticationFilter` using the same pattern as `order-service`, with:

```java
String userIdHeader = request.getHeader("X-User-Id");
String email = request.getHeader("X-User-Email");
String rolesHeader = request.getHeader("X-User-Roles");
List<String> roles = rolesHeader == null || rolesHeader.isBlank()
    ? List.of("USER")
    : Arrays.stream(rolesHeader.split(",")).map(String::trim).filter(role -> !role.isBlank()).toList();
GatewayUser principal = new GatewayUser(Long.valueOf(userIdHeader), email, roles);
List<GrantedAuthority> authorities = principal.roles().stream()
    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
    .toList();
SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
SecurityContextHolder.setContext(context);
try {
    filterChain.doFilter(request, response);
} finally {
    SecurityContextHolder.clearContext();
}
```

Create `SecurityConfig` with:

```java
.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
.requestMatchers("/api/admin/payments/**").hasRole("ADMIN")
.requestMatchers("/api/payments/**").authenticated()
.anyRequest().authenticated()
```

Use a JSON authentication entry point that writes status `401` and message `Missing user identity`.

Create `OpenApiConfig` with title `Payment Service API`, version `v1`, and description `Payment APIs for the e-commerce microservices system`.

- [ ] **Step 5: Implement global exception handler**

Create `GlobalExceptionHandler` mapping:

- `MethodArgumentNotValidException` -> `400`.
- `HttpMessageNotReadableException` -> `400`.
- `MissingUserIdentityException` -> `401`.
- `AccessDeniedException` -> `403`.
- `PaymentNotFoundException` and `NoResourceFoundException` -> `404`.
- `DuplicateOrderPaymentException`, `InvalidPaymentOperationException`, and `IllegalStateException` -> `409`.
- `HttpMediaTypeNotSupportedException` -> `415`.
- `HttpRequestMethodNotSupportedException` -> `405`.
- unexpected `Exception` -> `500`.

Build responses with:

```java
new ApiErrorResponse(
    Instant.now(),
    status.value(),
    status.getReasonPhrase(),
    message,
    request.getRequestURI(),
    details
)
```

- [ ] **Step 6: Run DTO/error tests to verify GREEN**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentResponseTests,CreatePaymentRequestValidationTests,UpdatePaymentStatusRequestValidationTests,GatewayUserTests,ApiErrorResponseTests" test
```

Expected: PASS.

- [ ] **Step 7: Write failing security and OpenAPI tests**

Create tests covering:

```java
@Test
void healthEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
}

@Test
void paymentEndpointRequiresIdentity() throws Exception {
    mockMvc.perform(get("/api/payments")).andExpect(status().isUnauthorized());
}

@Test
void adminPaymentEndpointRequiresAdminRole() throws Exception {
    mockMvc.perform(get("/api/admin/payments").header("X-User-Id", "10").header("X-User-Roles", "USER"))
        .andExpect(status().isForbidden());
}

@Test
void adminPaymentEndpointAllowsAdminRole() throws Exception {
    mockMvc.perform(get("/api/admin/payments").header("X-User-Id", "10").header("X-User-Roles", "ADMIN"))
        .andExpect(status().isNotFound());
}

@Test
void openApiEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
}
```

- [ ] **Step 8: Run security/OpenAPI tests to verify RED**

Run:

```powershell
mvn -pl payment-service "-Dtest=SecurityConfigTests,OpenApiEndpointTests" test
```

Expected: FAIL until security and OpenAPI config are complete.

- [ ] **Step 9: Complete config and rerun tests**

Run:

```powershell
mvn -pl payment-service "-Dtest=SecurityConfigTests,OpenApiEndpointTests" test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```powershell
git add payment-service/src/main/java/com/example/ecommerce/paymentservice/dto payment-service/src/main/java/com/example/ecommerce/paymentservice/config payment-service/src/main/java/com/example/ecommerce/paymentservice/exception payment-service/src/test/java/com/example/ecommerce/paymentservice/dto payment-service/src/test/java/com/example/ecommerce/paymentservice/config payment-service/src/test/java/com/example/ecommerce/paymentservice/exception
git commit -m "feat: add payment API support infrastructure"
```

---

### Task 4: Payment Service Behavior

**Files:**

- Create: `payment-service/src/main/java/com/example/ecommerce/paymentservice/service/PaymentService.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/service/PaymentServiceTests.java`

- [ ] **Step 1: Write failing service tests**

Create `PaymentServiceTests` covering:

```java
@Test
void createPaymentAppliesSuccessSimulation() {
    CreatePaymentRequest request = new CreatePaymentRequest(1000L, new BigDecimal("99.98"), PaymentMethod.CARD, SimulatePaymentResult.SUCCESS);
    when(paymentRepository.findByOrderId(1000L)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0), 5000L));

    PaymentResponse response = paymentService.create(user(), request);

    assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
    assertThat(response.userId()).isEqualTo(10L);
}

@Test
void createPaymentAppliesFailedSimulation() {
    CreatePaymentRequest request = new CreatePaymentRequest(1000L, new BigDecimal("99.98"), PaymentMethod.CARD, SimulatePaymentResult.FAILED);
    when(paymentRepository.findByOrderId(1000L)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0), 5000L));

    PaymentResponse response = paymentService.create(user(), request);

    assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(response.failureReason()).isEqualTo("Payment failed");
}

@Test
void createPaymentReturnsExistingPaymentForSameUserAndOrder() {
    Payment existing = assignId(Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD), 5000L);
    when(paymentRepository.findByOrderId(1000L)).thenReturn(Optional.of(existing));

    PaymentResponse response = paymentService.create(user(), new CreatePaymentRequest(1000L, new BigDecimal("99.98"), PaymentMethod.CARD, SimulatePaymentResult.SUCCESS));

    assertThat(response.paymentId()).isEqualTo(5000L);
    verify(paymentRepository, never()).save(any());
}

@Test
void createPaymentRejectsExistingPaymentForDifferentUser() {
    Payment existing = assignId(Payment.create(1000L, 11L, new BigDecimal("99.98"), PaymentMethod.CARD), 5000L);
    when(paymentRepository.findByOrderId(1000L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> paymentService.create(user(), new CreatePaymentRequest(1000L, new BigDecimal("99.98"), PaymentMethod.CARD, SimulatePaymentResult.SUCCESS)))
        .isInstanceOf(DuplicateOrderPaymentException.class);
}

@Test
void currentUserPaymentDetailHidesAnotherUsersPayment() {
    when(paymentRepository.findByIdAndUserId(5000L, 10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentService.findCurrentUserPayment(10L, 5000L))
        .isInstanceOf(PaymentNotFoundException.class);
}

@Test
void adminCanMarkPendingPaymentSuccess() {
    Payment payment = assignId(Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD), 5000L);
    when(paymentRepository.findById(5000L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(payment)).thenReturn(payment);

    PaymentResponse response = paymentService.updateStatusAsAdmin(5000L, new UpdatePaymentStatusRequest(PaymentStatus.SUCCESS, null));

    assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
}

@Test
void adminCanMarkPendingPaymentFailed() {
    Payment payment = assignId(Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD), 5000L);
    when(paymentRepository.findById(5000L)).thenReturn(Optional.of(payment));
    when(paymentRepository.save(payment)).thenReturn(payment);

    PaymentResponse response = paymentService.updateStatusAsAdmin(5000L, new UpdatePaymentStatusRequest(PaymentStatus.FAILED, "Simulated card decline"));

    assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(response.failureReason()).isEqualTo("Simulated card decline");
}

@Test
void adminStatusUpdateRejectsTerminalPayment() {
    Payment payment = assignId(Payment.create(1000L, 10L, new BigDecimal("99.98"), PaymentMethod.CARD), 5000L);
    payment.markSuccess();
    when(paymentRepository.findById(5000L)).thenReturn(Optional.of(payment));

    assertThatThrownBy(() -> paymentService.updateStatusAsAdmin(5000L, new UpdatePaymentStatusRequest(PaymentStatus.FAILED, "Late failure")))
        .isInstanceOf(InvalidPaymentOperationException.class)
        .hasMessage("Terminal payment cannot be changed");
}
```

Also include tests:

- `findCurrentUserPayments` calls `findByUserId`.
- `findCurrentUserPaymentByOrder` calls `findByOrderIdAndUserId`.
- `findAdminPayments` calls `findByStatus` when status is present, otherwise `findAll`.
- `responseTimestampsTreatEntityTimestampsAsUtc` sets JVM default timezone to `Asia/Ho_Chi_Minh` and expects unchanged UTC instants.

- [ ] **Step 2: Run service tests to verify RED**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentServiceTests" test
```

Expected: FAIL because `PaymentService` does not exist.

- [ ] **Step 3: Implement service**

Create `PaymentService` with dependencies:

```java
private final PaymentRepository paymentRepository;
```

Methods:

```java
@Transactional
public PaymentResponse create(GatewayUser user, CreatePaymentRequest request)

@Transactional(readOnly = true)
public Page<PaymentResponse> findCurrentUserPayments(Long userId, Pageable pageable)

@Transactional(readOnly = true)
public PaymentResponse findCurrentUserPayment(Long userId, Long paymentId)

@Transactional(readOnly = true)
public PaymentResponse findCurrentUserPaymentByOrder(Long userId, Long orderId)

@Transactional(readOnly = true)
public Page<PaymentResponse> findAdminPayments(PaymentStatus status, Pageable pageable)

@Transactional(readOnly = true)
public PaymentResponse findAdminPayment(Long paymentId)

@Transactional
public PaymentResponse updateStatusAsAdmin(Long paymentId, UpdatePaymentStatusRequest request)
```

Use this creation algorithm:

```java
Optional<Payment> existing = paymentRepository.findByOrderId(request.orderId());
if (existing.isPresent()) {
    Payment payment = existing.get();
    if (!payment.getUserId().equals(user.id())) {
        throw new DuplicateOrderPaymentException();
    }
    return toResponse(payment);
}

Payment payment = Payment.create(request.orderId(), user.id(), request.amount(), request.method());
SimulatePaymentResult result = request.simulateResult() == null ? SimulatePaymentResult.PENDING : request.simulateResult();
if (result == SimulatePaymentResult.SUCCESS) {
    payment.markSuccess();
} else if (result == SimulatePaymentResult.FAILED) {
    payment.markFailed("Payment failed");
}
return toResponse(paymentRepository.save(payment));
```

Use this admin status update algorithm:

```java
Payment payment = paymentRepository.findById(paymentId)
    .orElseThrow(() -> new PaymentNotFoundException(paymentId));
if (payment.isTerminal()) {
    throw new InvalidPaymentOperationException("Terminal payment cannot be changed");
}
if (request.status() == PaymentStatus.SUCCESS) {
    payment.markSuccess();
} else if (request.status() == PaymentStatus.FAILED) {
    payment.markFailed(request.failureReason());
} else {
    throw new InvalidPaymentOperationException("Only SUCCESS or FAILED is supported");
}
return toResponse(paymentRepository.save(payment));
```

Map timestamps with UTC, not system default timezone:

```java
private static Instant toInstant(LocalDateTime timestamp) {
    return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
}
```

- [ ] **Step 4: Run service tests to verify GREEN**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentServiceTests" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add payment-service/src/main/java/com/example/ecommerce/paymentservice/service payment-service/src/test/java/com/example/ecommerce/paymentservice/service
git commit -m "feat: add payment service behavior"
```

---

### Task 5: User And Admin Controllers

**Files:**

- Create: `payment-service/src/main/java/com/example/ecommerce/paymentservice/controller/PaymentController.java`
- Create: `payment-service/src/main/java/com/example/ecommerce/paymentservice/controller/AdminPaymentController.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/controller/PaymentControllerTests.java`
- Test: `payment-service/src/test/java/com/example/ecommerce/paymentservice/controller/AdminPaymentControllerTests.java`

- [ ] **Step 1: Write failing user controller tests**

Create `PaymentControllerTests` covering:

```java
@Test
void createPaymentReturnsCreatedPayment() throws Exception {
    CreatePaymentRequest request = new CreatePaymentRequest(1000L, new BigDecimal("99.98"), PaymentMethod.CARD, SimulatePaymentResult.SUCCESS);
    when(paymentService.create(gatewayUser(), request)).thenReturn(paymentResponse(PaymentStatus.SUCCESS));

    mockMvc.perform(post("/api/payments")
            .principal(authentication())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"));
}

@Test
void listPaymentsReturnsCurrentUserPayments() throws Exception {
    when(paymentService.findCurrentUserPayments(eq(10L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(paymentResponse(PaymentStatus.PENDING))));

    mockMvc.perform(get("/api/payments").principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].paymentId").value(5000L));
}

@Test
void getPaymentByOrderReturnsCurrentUserPayment() throws Exception {
    when(paymentService.findCurrentUserPaymentByOrder(10L, 1000L)).thenReturn(paymentResponse(PaymentStatus.PENDING));

    mockMvc.perform(get("/api/payments/by-order/{orderId}", 1000L).principal(authentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value(1000L));
}

@Test
void missingGatewayUserReturnsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/payments").principal(new TestingAuthenticationToken("raw", null)))
        .andExpect(status().isUnauthorized());
}

@Test
void validationErrorReturnsBadRequest() throws Exception {
    CreatePaymentRequest request = new CreatePaymentRequest(null, BigDecimal.ZERO, null, SimulatePaymentResult.SUCCESS);

    mockMvc.perform(post("/api/payments")
            .principal(authentication())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details").isArray());
}
```

Also cover:

- `getPaymentReturnsCurrentUserPayment`.
- `duplicateOrderPaymentMapsToConflict`.
- `unsupportedContentTypeReturnsUnsupportedMediaType`.
- `unsupportedMethodReturnsMethodNotAllowed`.

- [ ] **Step 2: Run user controller tests to verify RED**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentControllerTests" test
```

Expected: FAIL because `PaymentController` does not exist.

- [ ] **Step 3: Implement user controller**

Create `PaymentController`:

```java
package com.example.ecommerce.paymentservice.controller;

import com.example.ecommerce.paymentservice.config.GatewayUser;
import com.example.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.example.ecommerce.paymentservice.dto.PaymentResponse;
import com.example.ecommerce.paymentservice.exception.MissingUserIdentityException;
import com.example.ecommerce.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
class PaymentController {

    private final PaymentService paymentService;

    PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PaymentResponse create(Authentication authentication, @Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.create(currentUser(authentication), request);
    }

    @GetMapping
    Page<PaymentResponse> list(Authentication authentication, Pageable pageable) {
        return paymentService.findCurrentUserPayments(currentUser(authentication).id(), pageable);
    }

    @GetMapping("/{id}")
    PaymentResponse get(Authentication authentication, @PathVariable Long id) {
        return paymentService.findCurrentUserPayment(currentUser(authentication).id(), id);
    }

    @GetMapping("/by-order/{orderId}")
    PaymentResponse getByOrder(Authentication authentication, @PathVariable Long orderId) {
        return paymentService.findCurrentUserPaymentByOrder(currentUser(authentication).id(), orderId);
    }

    private static GatewayUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof GatewayUser user)) {
            throw new MissingUserIdentityException();
        }
        return user;
    }
}
```

- [ ] **Step 4: Run user controller tests to verify GREEN**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentControllerTests" test
```

Expected: PASS.

- [ ] **Step 5: Write failing admin controller tests**

Create `AdminPaymentControllerTests` covering:

```java
@Test
void adminListCanFilterByStatus() throws Exception {
    when(paymentService.findAdminPayments(eq(PaymentStatus.SUCCESS), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(paymentResponse(PaymentStatus.SUCCESS))));

    mockMvc.perform(get("/api/admin/payments?status=SUCCESS").principal(adminAuthentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
}

@Test
void adminDetailReturnsPayment() throws Exception {
    when(paymentService.findAdminPayment(5000L)).thenReturn(paymentResponse(PaymentStatus.PENDING));

    mockMvc.perform(get("/api/admin/payments/{id}", 5000L).principal(adminAuthentication()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value(5000L));
}

@Test
void adminStatusUpdateMarksPaymentFailed() throws Exception {
    UpdatePaymentStatusRequest request = new UpdatePaymentStatusRequest(PaymentStatus.FAILED, "Simulated card decline");
    when(paymentService.updateStatusAsAdmin(5000L, request)).thenReturn(paymentResponse(PaymentStatus.FAILED));

    mockMvc.perform(patch("/api/admin/payments/{id}/status", 5000L)
            .principal(adminAuthentication())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"));
}

@Test
void userRoleReceivesForbidden() throws Exception {
    mockMvc.perform(get("/api/admin/payments").principal(userAuthentication()))
        .andExpect(status().isForbidden());
}
```

Also cover:

- admin status update accepts `SUCCESS`.
- validation rejects `PENDING`.
- terminal update maps to `409`.

- [ ] **Step 6: Run admin controller tests to verify RED**

Run:

```powershell
mvn -pl payment-service "-Dtest=AdminPaymentControllerTests" test
```

Expected: FAIL because `AdminPaymentController` does not exist.

- [ ] **Step 7: Implement admin controller**

Create `AdminPaymentController`:

```java
package com.example.ecommerce.paymentservice.controller;

import com.example.ecommerce.paymentservice.dto.PaymentResponse;
import com.example.ecommerce.paymentservice.dto.UpdatePaymentStatusRequest;
import com.example.ecommerce.paymentservice.entity.PaymentStatus;
import com.example.ecommerce.paymentservice.exception.InvalidPaymentOperationException;
import com.example.ecommerce.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
class AdminPaymentController {

    private final PaymentService paymentService;

    AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    Page<PaymentResponse> list(@RequestParam(required = false) PaymentStatus status, Pageable pageable) {
        return paymentService.findAdminPayments(status, pageable);
    }

    @GetMapping("/{id}")
    PaymentResponse get(@PathVariable Long id) {
        return paymentService.findAdminPayment(id);
    }

    @PatchMapping("/{id}/status")
    PaymentResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdatePaymentStatusRequest request) {
        if (request.status() != PaymentStatus.SUCCESS && request.status() != PaymentStatus.FAILED) {
            throw new InvalidPaymentOperationException("Only SUCCESS or FAILED is supported");
        }
        return paymentService.updateStatusAsAdmin(id, request);
    }
}
```

- [ ] **Step 8: Run controller tests to verify GREEN**

Run:

```powershell
mvn -pl payment-service "-Dtest=PaymentControllerTests,AdminPaymentControllerTests" test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add payment-service/src/main/java/com/example/ecommerce/paymentservice/controller payment-service/src/test/java/com/example/ecommerce/paymentservice/controller
git commit -m "feat: add payment APIs"
```

---

### Task 6: Gateway Routes

**Files:**

- Modify: `api-gateway/src/main/resources/application.yml`
- Modify: `api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`

- [ ] **Step 1: Write failing gateway route test**

Modify `GatewayRoutesTests` so the expected route ids include:

```java
"payment-service",
"payment-service-admin"
```

Expected paths:

```text
/api/payments/**
/api/admin/payments/**
```

- [ ] **Step 2: Run route test to verify RED**

Run:

```powershell
mvn -pl api-gateway "-Dtest=GatewayRoutesTests" test
```

Expected: FAIL because payment routes are not configured.

- [ ] **Step 3: Add gateway routes**

Modify `api-gateway/src/main/resources/application.yml`:

```yaml
        - id: payment-service-admin
          uri: lb://payment-service
          predicates:
            - Path=/api/admin/payments/**
        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**
```

Place the admin route before the user route so `/api/admin/payments/**` is not accidentally matched by a broader route in future changes.

- [ ] **Step 4: Run route test to verify GREEN**

Run:

```powershell
mvn -pl api-gateway "-Dtest=GatewayRoutesTests" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add api-gateway/src/main/resources/application.yml api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java
git commit -m "feat: route payment service through gateway"
```

---

### Task 7: Runtime Wiring, Docker, CI, And README

**Files:**

- Create: `payment-service/Dockerfile`
- Modify: existing service Dockerfiles to copy `payment-service/pom.xml`
- Modify: `docker-compose.yml`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`

- [ ] **Step 1: Write failing runtime checks**

Run:

```powershell
docker compose config --quiet
docker compose build payment-service
```

Expected before implementation: `docker compose build payment-service` fails because compose has no `payment-service` service and no Dockerfile.

- [ ] **Step 2: Add payment-service Dockerfile**

Create `payment-service/Dockerfile`:

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
COPY payment-service/pom.xml payment-service/pom.xml
COPY payment-service/src payment-service/src

RUN mvn -pl payment-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/payment-service/target/*.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Add this line to every existing service Dockerfile:

```dockerfile
COPY payment-service/pom.xml payment-service/pom.xml
```

Update these files:

- `eureka-server/Dockerfile`
- `api-gateway/Dockerfile`
- `auth-service/Dockerfile`
- `product-service/Dockerfile`
- `inventory-service/Dockerfile`
- `cart-service/Dockerfile`
- `order-service/Dockerfile`

- [ ] **Step 3: Update Docker Compose**

Add `payment-postgres`:

```yaml
  payment-postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: payment_db
      POSTGRES_USER: ecommerce
      POSTGRES_PASSWORD: ecommerce
    ports:
      - "127.0.0.1:5437:5432"
    volumes:
      - payment-postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ecommerce -d payment_db"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 10s
```

Add `payment-service`:

```yaml
  payment-service:
    build:
      context: .
      dockerfile: payment-service/Dockerfile
    container_name: ecommerce-payment-service
    depends_on:
      payment-postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    restart: on-failure
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: jdbc:postgresql://payment-postgres:5432/payment_db
      SPRING_DATASOURCE_USERNAME: ecommerce
      SPRING_DATASOURCE_PASSWORD: ecommerce
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      EUREKA_INSTANCE_LEASE_RENEWAL_INTERVAL_IN_SECONDS: 5
      EUREKA_INSTANCE_LEASE_EXPIRATION_DURATION_IN_SECONDS: 15
    ports:
      - "8086:8086"
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8086/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s
```

Add `payment-service` to `api-gateway.depends_on`:

```yaml
      payment-service:
        condition: service_healthy
```

Add volume:

```yaml
  payment-postgres-data:
```

- [ ] **Step 4: Update CI and README**

In `.github/workflows/ci.yml`, add:

```yaml
          docker compose build payment-service
```

to the sequential Docker build section after `order-service`.

In `README.md`, document:

- payment-service port `8086`.
- payment database port `5437`.
- `POST /api/payments`.
- `GET /api/payments`.
- `GET /api/payments/{id}`.
- `GET /api/payments/by-order/{orderId}`.
- `GET /api/admin/payments`.
- `PATCH /api/admin/payments/{id}/status`.
- Update sequential Docker build commands to include `payment-service`.
- Update Compose startup commands to include `payment-postgres` and `payment-service`.

- [ ] **Step 5: Run runtime verification**

Run:

```powershell
mvn -pl payment-service test
docker compose config --quiet
docker compose build payment-service
```

Expected: all commands exit `0`.

- [ ] **Step 6: Commit**

```powershell
git add payment-service/Dockerfile docker-compose.yml .github/workflows/ci.yml README.md eureka-server/Dockerfile api-gateway/Dockerfile auth-service/Dockerfile product-service/Dockerfile inventory-service/Dockerfile cart-service/Dockerfile order-service/Dockerfile
git commit -m "chore: wire payment service runtime"
```

---

### Task 8: Full Verification And Review

**Files:**

- No planned production changes unless review finds issues.

- [ ] **Step 1: Run full Maven verification**

Run:

```powershell
mvn -B -ntp clean test
```

Expected:

- Reactor includes `payment-service`.
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
"eureka-server","api-gateway","auth-service","product-service","inventory-service","cart-service","order-service","payment-service" | ForEach-Object { docker compose build -q $_ }
```

Expected: both commands exit `0`.

- [ ] **Step 4: Request code review**

Use `superpowers:requesting-code-review` with explicit review scope:

- spec compliance for `payment-service`
- TDD coverage quality
- payment idempotency and cross-user order id conflict behavior
- status transition rules
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
git push -u origin payment-service
```

Expected: clean working tree and successful push.
