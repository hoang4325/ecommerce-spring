# Notification Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the MVP `notification-service` that persists simulated email notification logs, exposes protected internal creation and admin read APIs, and runs locally through Docker Compose.

**Architecture:** `notification-service` is a Spring Boot MVC/JPA service with its own PostgreSQL database and no shared entities. Admin APIs use the existing trusted gateway identity header pattern, while the internal creation endpoint uses a small local `X-Internal-Token` filter until Kafka consumers are added in a later event-flow branch. Notification filtering uses a criteria DTO plus Spring Data JPA specifications so filters stay composable without controller business logic.

**Tech Stack:** Java 21, Spring Boot 3.5.14, Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation, PostgreSQL, H2 tests, Eureka client, Springdoc OpenAPI, Maven, Docker Compose.

---

## File Structure

Create:

- `notification-service/pom.xml`: service module dependencies and build plugin.
- `notification-service/Dockerfile`: multi-module Docker build for notification-service.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/NotificationServiceApplication.java`: Spring Boot entrypoint.
- `notification-service/src/main/resources/application.yml`: datasource, Eureka, actuator, Springdoc, and internal-token config.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/entity/Notification.java`: notification aggregate root.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/entity/NotificationType.java`: notification type enum.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/entity/NotificationChannel.java`: delivery channel enum.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/entity/NotificationStatus.java`: delivery status enum.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/dto/CreateNotificationRequest.java`: internal creation request.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/dto/NotificationResponse.java`: notification response DTO.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/dto/NotificationSearchCriteria.java`: admin filter criteria.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/repository/NotificationRepository.java`: notification repository.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/repository/NotificationSpecifications.java`: composable admin filtering.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/service/NotificationService.java`: creation, filtering, detail lookup, mapping.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/controller/InternalNotificationController.java`: protected internal create API.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/controller/AdminNotificationController.java`: admin list/detail APIs.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/config/GatewayUser.java`: authenticated gateway identity.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/config/GatewayIdentityAuthenticationFilter.java`: trusted identity header filter.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/config/InternalTokenAuthenticationFilter.java`: local internal token filter.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/config/NotificationProperties.java`: `notification.internal-token` configuration.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/config/SecurityConfig.java`: endpoint authorization and JSON auth/forbidden responses.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/config/OpenApiConfig.java`: Swagger metadata.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/exception/ApiErrorResponse.java`: standard error body.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/exception/GlobalExceptionHandler.java`: error mapping.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/exception/MissingUserIdentityException.java`: missing trusted identity.
- `notification-service/src/main/java/com/example/ecommerce/notificationservice/exception/NotificationNotFoundException.java`: notification lookup miss.
- Tests under `notification-service/src/test/java/com/example/ecommerce/notificationservice/**`.

Modify:

- `pom.xml`: add `<module>notification-service</module>`.
- `api-gateway/src/main/resources/application.yml`: add admin notification route.
- `api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`: assert notification admin route and path.
- `.github/workflows/ci.yml`: include notification-service in sequential Docker image builds.
- `docker-compose.yml`: add `notification-postgres`, `notification-service`, volume, and gateway dependency.
- `README.md`: document notification-service endpoints and local run commands.
- Existing service Dockerfiles: add `COPY notification-service/pom.xml notification-service/pom.xml` so multi-module Docker builds stay valid.

Use this PowerShell prefix for Maven tests if Surefire temp files hit the C: drive:

```powershell
New-Item -ItemType Directory -Force -Path D:\spring\.tmp\notification-service | Out-Null
$env:JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=D:\spring\.tmp\notification-service'
mvn -pl notification-service test
Remove-Item Env:JAVA_TOOL_OPTIONS
```

---

### Task 1: Module Skeleton

**Files:**

- Modify: `pom.xml`
- Create: `notification-service/pom.xml`
- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/NotificationServiceApplication.java`
- Create: `notification-service/src/main/resources/application.yml`
- Test: `notification-service/src/test/java/com/example/ecommerce/notificationservice/NotificationServiceApplicationTests.java`

- [ ] **Step 1: Write the failing application context test**

Create `notification-service/src/test/java/com/example/ecommerce/notificationservice/NotificationServiceApplicationTests.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify RED**

Run:

```powershell
mvn -pl notification-service test
```

Expected: FAIL because Maven does not know the `notification-service` module yet.

- [ ] **Step 3: Add the module POM and application skeleton**

Modify the root `pom.xml` modules:

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
    <module>notification-service</module>
</modules>
```

Create `notification-service/pom.xml`:

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

    <artifactId>notification-service</artifactId>
    <name>notification-service</name>
    <description>Notification service for the e-commerce microservices system</description>

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

Create `notification-service/src/main/java/com/example/ecommerce/notificationservice/NotificationServiceApplication.java`:

```java
package com.example.ecommerce.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
```

Create `notification-service/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: notification-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5438/notification_db}
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
  port: 8087

notification:
  internal-token: ${NOTIFICATION_INTERNAL_TOKEN:local-notification-token}

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

- [ ] **Step 4: Run the test to verify GREEN**

Run:

```powershell
mvn -pl notification-service test
```

Expected: PASS with 1 test.

- [ ] **Step 5: Commit**

```powershell
git add pom.xml notification-service
git commit -m "feat: add notification service module skeleton"
```

---

### Task 2: Notification Domain And Persistence

**Files:**

- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/entity/NotificationType.java`
- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/entity/NotificationChannel.java`
- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/entity/NotificationStatus.java`
- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/entity/Notification.java`
- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/dto/NotificationSearchCriteria.java`
- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/repository/NotificationRepository.java`
- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/repository/NotificationSpecifications.java`
- Test: `notification-service/src/test/java/com/example/ecommerce/notificationservice/entity/NotificationTests.java`
- Test: `notification-service/src/test/java/com/example/ecommerce/notificationservice/repository/NotificationRepositoryTests.java`

- [ ] **Step 1: Write failing domain tests**

Create `NotificationTests`:

```java
package com.example.ecommerce.notificationservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotificationTests {

    @Test
    void createSentEmailNotification() {
        Notification notification = Notification.sentEmail(
            10L,
            1000L,
            5000L,
            NotificationType.PAYMENT_SUCCEEDED,
            "customer@example.com",
            "Payment received",
            "Your payment was successful."
        );

        assertThat(notification.getUserId()).isEqualTo(10L);
        assertThat(notification.getOrderId()).isEqualTo(1000L);
        assertThat(notification.getPaymentId()).isEqualTo(5000L);
        assertThat(notification.getType()).isEqualTo(NotificationType.PAYMENT_SUCCEEDED);
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.getRecipient()).isEqualTo("customer@example.com");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getFailureReason()).isNull();
    }

    @Test
    void createFailedEmailNotification() {
        Notification notification = Notification.failedEmail(
            10L,
            1000L,
            5000L,
            NotificationType.PAYMENT_FAILED,
            "customer@example.com",
            "Payment failed",
            "Your payment failed.",
            "Simulated notification failure"
        );

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).isEqualTo("Simulated notification failure");
    }

    @Test
    void createRejectsMissingType() {
        assertThatThrownBy(() -> Notification.sentEmail(
            10L, 1000L, 5000L, null, "customer@example.com", "Subject", "Message"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Notification type is required");
    }

    @Test
    void createRejectsMissingRecipient() {
        assertThatThrownBy(() -> Notification.sentEmail(
            10L, 1000L, 5000L, NotificationType.PAYMENT_SUCCEEDED, " ", "Subject", "Message"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Recipient is required");
    }

    @Test
    void createRejectsMissingSubject() {
        assertThatThrownBy(() -> Notification.sentEmail(
            10L, 1000L, 5000L, NotificationType.PAYMENT_SUCCEEDED, "customer@example.com", "", "Message"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Subject is required");
    }

    @Test
    void createRejectsMissingMessage() {
        assertThatThrownBy(() -> Notification.sentEmail(
            10L, 1000L, 5000L, NotificationType.PAYMENT_SUCCEEDED, "customer@example.com", "Subject", " "
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Message is required");
    }
}
```

- [ ] **Step 2: Run domain tests to verify RED**

Run:

```powershell
mvn -pl notification-service "-Dtest=NotificationTests" test
```

Expected: FAIL because the notification entity and enums do not exist.

- [ ] **Step 3: Implement enums and entity**

Create enums:

```java
package com.example.ecommerce.notificationservice.entity;

public enum NotificationType {
    ORDER_COMPLETED,
    ORDER_CANCELLED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED
}
```

```java
package com.example.ecommerce.notificationservice.entity;

public enum NotificationChannel {
    EMAIL
}
```

```java
package com.example.ecommerce.notificationservice.entity;

public enum NotificationStatus {
    SENT,
    FAILED
}
```

Create `Notification` with:

```java
package com.example.ecommerce.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_order_id", columnList = "order_id"),
        @Index(name = "idx_notifications_payment_id", columnList = "payment_id"),
        @Index(name = "idx_notifications_type", columnList = "type"),
        @Index(name = "idx_notifications_status", columnList = "status"),
        @Index(name = "idx_notifications_created_at", columnList = "created_at")
    }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    private Notification(
        Long userId,
        Long orderId,
        Long paymentId,
        NotificationType type,
        String recipient,
        NotificationStatus status,
        String subject,
        String message,
        String failureReason
    ) {
        if (type == null) {
            throw new IllegalArgumentException("Notification type is required");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
        this.userId = userId;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.type = type;
        this.channel = NotificationChannel.EMAIL;
        this.recipient = recipient;
        this.status = status;
        this.subject = subject;
        this.message = message;
        this.failureReason = failureReason;
    }

    public static Notification sentEmail(
        Long userId,
        Long orderId,
        Long paymentId,
        NotificationType type,
        String recipient,
        String subject,
        String message
    ) {
        return new Notification(userId, orderId, paymentId, type, recipient, NotificationStatus.SENT, subject, message, null);
    }

    public static Notification failedEmail(
        Long userId,
        Long orderId,
        Long paymentId,
        NotificationType type,
        String recipient,
        String subject,
        String message,
        String failureReason
    ) {
        String reason = failureReason == null || failureReason.isBlank()
            ? "Notification failed"
            : failureReason;
        return new Notification(userId, orderId, paymentId, type, recipient, NotificationStatus.FAILED, subject, message, reason);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getOrderId() { return orderId; }
    public Long getPaymentId() { return paymentId; }
    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public NotificationStatus getStatus() { return status; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: Run domain tests to verify GREEN**

Run:

```powershell
mvn -pl notification-service "-Dtest=NotificationTests" test
```

Expected: PASS.

- [ ] **Step 5: Write failing repository tests**

Create `NotificationSearchCriteria`:

```java
package com.example.ecommerce.notificationservice.dto;

import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;

public record NotificationSearchCriteria(
    NotificationType type,
    NotificationStatus status,
    Long userId,
    Long orderId,
    Long paymentId
) {
}
```

Create `NotificationRepositoryTests`:

```java
package com.example.ecommerce.notificationservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.notificationservice.dto.NotificationSearchCriteria;
import com.example.ecommerce.notificationservice.entity.Notification;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:notification_repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=ecommerce",
    "spring.datasource.password=ecommerce",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false"
})
class NotificationRepositoryTests {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void savesAndFindsNotification() {
        Notification saved = notificationRepository.save(sent(10L, 1000L, 5000L, NotificationType.PAYMENT_SUCCEEDED));

        assertThat(notificationRepository.findById(saved.getId()))
            .get()
            .extracting(Notification::getRecipient)
            .isEqualTo("customer@example.com");
    }

    @Test
    void filtersByTypeStatusAndReferences() {
        notificationRepository.save(sent(10L, 1000L, 5000L, NotificationType.PAYMENT_SUCCEEDED));
        notificationRepository.save(failed(11L, 1001L, 5001L, NotificationType.PAYMENT_FAILED));

        NotificationSearchCriteria criteria = new NotificationSearchCriteria(
            NotificationType.PAYMENT_FAILED,
            NotificationStatus.FAILED,
            11L,
            1001L,
            5001L
        );

        Page<Notification> result = notificationRepository.findAll(
            NotificationSpecifications.byCriteria(criteria),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getType()).isEqualTo(NotificationType.PAYMENT_FAILED);
    }

    @Test
    void sortsNewestFirst() {
        notificationRepository.save(sent(10L, 1000L, 5000L, NotificationType.PAYMENT_SUCCEEDED));
        notificationRepository.save(sent(10L, 1002L, 5002L, NotificationType.ORDER_COMPLETED));

        Page<Notification> result = notificationRepository.findAll(
            NotificationSpecifications.byCriteria(new NotificationSearchCriteria(null, null, 10L, null, null)),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getCreatedAt())
            .isAfterOrEqualTo(result.getContent().get(1).getCreatedAt());
    }

    private static Notification sent(Long userId, Long orderId, Long paymentId, NotificationType type) {
        return Notification.sentEmail(userId, orderId, paymentId, type, "customer@example.com", "Subject", "Message");
    }

    private static Notification failed(Long userId, Long orderId, Long paymentId, NotificationType type) {
        return Notification.failedEmail(userId, orderId, paymentId, type, "customer@example.com", "Subject", "Message", "Simulated notification failure");
    }
}
```

- [ ] **Step 6: Run repository tests to verify RED**

Run:

```powershell
mvn -pl notification-service "-Dtest=NotificationRepositoryTests" test
```

Expected: FAIL because `NotificationRepository` and `NotificationSpecifications` do not exist.

- [ ] **Step 7: Implement repository and specifications**

Create `NotificationRepository`:

```java
package com.example.ecommerce.notificationservice.repository;

import com.example.ecommerce.notificationservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
}
```

Create `NotificationSpecifications`:

```java
package com.example.ecommerce.notificationservice.repository;

import com.example.ecommerce.notificationservice.dto.NotificationSearchCriteria;
import com.example.ecommerce.notificationservice.entity.Notification;
import org.springframework.data.jpa.domain.Specification;

public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<Notification> byCriteria(NotificationSearchCriteria criteria) {
        return Specification
            .where(hasType(criteria.type()))
            .and(hasStatus(criteria.status()))
            .and(hasUserId(criteria.userId()))
            .and(hasOrderId(criteria.orderId()))
            .and(hasPaymentId(criteria.paymentId()));
    }

    private static Specification<Notification> hasType(Object type) {
        return (root, query, builder) -> type == null ? null : builder.equal(root.get("type"), type);
    }

    private static Specification<Notification> hasStatus(Object status) {
        return (root, query, builder) -> status == null ? null : builder.equal(root.get("status"), status);
    }

    private static Specification<Notification> hasUserId(Long userId) {
        return (root, query, builder) -> userId == null ? null : builder.equal(root.get("userId"), userId);
    }

    private static Specification<Notification> hasOrderId(Long orderId) {
        return (root, query, builder) -> orderId == null ? null : builder.equal(root.get("orderId"), orderId);
    }

    private static Specification<Notification> hasPaymentId(Long paymentId) {
        return (root, query, builder) -> paymentId == null ? null : builder.equal(root.get("paymentId"), paymentId);
    }
}
```

- [ ] **Step 8: Run domain and repository tests to verify GREEN**

Run:

```powershell
mvn -pl notification-service "-Dtest=NotificationTests,NotificationRepositoryTests" test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add notification-service/src/main/java/com/example/ecommerce/notificationservice/entity notification-service/src/main/java/com/example/ecommerce/notificationservice/dto/NotificationSearchCriteria.java notification-service/src/main/java/com/example/ecommerce/notificationservice/repository notification-service/src/test/java/com/example/ecommerce/notificationservice/entity notification-service/src/test/java/com/example/ecommerce/notificationservice/repository
git commit -m "feat: add notification domain persistence"
```

---

### Task 3: DTOs, Exceptions, Security, And OpenAPI

**Files:**

- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/dto/CreateNotificationRequest.java`
- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/dto/NotificationResponse.java`
- Create: config classes listed in the file structure.
- Create: exception classes listed in the file structure.
- Tests under `notification-service/src/test/java/com/example/ecommerce/notificationservice/dto`, `config`, and `exception`.

- [ ] **Step 1: Write failing DTO and error tests**

Create tests for:

```java
new CreateNotificationRequest(
    NotificationType.PAYMENT_SUCCEEDED,
    "customer@example.com",
    "Payment received",
    "Your payment was successful.",
    10L,
    1000L,
    5000L,
    false
)
```

Expected validation:

- valid request has no violations.
- null `type`, blank `recipient`, invalid email, blank `subject`, blank `message`, too-long `subject`, and too-long `message` fail validation.
- `NotificationResponse` preserves ids, enum values, text, and `Instant createdAt`.
- `ApiErrorResponse` stores field details.

Run:

```powershell
mvn -pl notification-service "-Dtest=CreateNotificationRequestValidationTests,NotificationResponseTests,ApiErrorResponseTests" test
```

Expected: FAIL because DTOs and error body do not exist.

- [ ] **Step 2: Implement DTOs and basic exceptions**

Create `CreateNotificationRequest`:

```java
package com.example.ecommerce.notificationservice.dto;

import com.example.ecommerce.notificationservice.entity.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
    @NotNull NotificationType type,
    @NotBlank @Email @Size(max = 320) String recipient,
    @NotBlank @Size(max = 200) String subject,
    @NotBlank @Size(max = 2000) String message,
    Long userId,
    Long orderId,
    Long paymentId,
    Boolean simulateFailure
) {
    public boolean shouldSimulateFailure() {
        return Boolean.TRUE.equals(simulateFailure);
    }
}
```

Create `NotificationResponse`:

```java
package com.example.ecommerce.notificationservice.dto;

import com.example.ecommerce.notificationservice.entity.NotificationChannel;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import java.time.Instant;

public record NotificationResponse(
    Long notificationId,
    Long userId,
    Long orderId,
    Long paymentId,
    NotificationType type,
    NotificationChannel channel,
    String recipient,
    NotificationStatus status,
    String subject,
    String message,
    String failureReason,
    Instant createdAt
) {
}
```

Create `ApiErrorResponse`:

```java
package com.example.ecommerce.notificationservice.exception;

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

Create `MissingUserIdentityException`:

```java
package com.example.ecommerce.notificationservice.exception;

public class MissingUserIdentityException extends RuntimeException {

    public MissingUserIdentityException() {
        super("Missing user identity");
    }
}
```

Create `NotificationNotFoundException`:

```java
package com.example.ecommerce.notificationservice.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long id) {
        super("Notification not found: " + id);
    }
}
```

- [ ] **Step 3: Run DTO and error tests to verify GREEN**

Run:

```powershell
mvn -pl notification-service "-Dtest=CreateNotificationRequestValidationTests,NotificationResponseTests,ApiErrorResponseTests" test
```

Expected: PASS.

- [ ] **Step 4: Write failing security and OpenAPI tests**

Create `SecurityConfigTests` covering:

- `/actuator/health` returns `200 OK` without identity.
- `/api/admin/notifications` without identity returns `401` and message `Missing user identity`.
- `/api/admin/notifications` with `X-User-Id: 10`, `X-User-Roles: USER` returns `403`.
- `/api/admin/notifications` with `X-User-Id: 1`, `X-User-Roles: ADMIN` is not `401` or `403`.
- `POST /api/internal/notifications` without `X-Internal-Token` returns `401`.
- `POST /api/internal/notifications` with wrong `X-Internal-Token` returns `401`.

Create `OpenApiEndpointTests` covering:

- `/v3/api-docs` is public and contains title `Notification Service API`.
- `/swagger-ui.html` is public or redirects.

Run:

```powershell
mvn -pl notification-service "-Dtest=SecurityConfigTests,OpenApiEndpointTests" test
```

Expected: FAIL because security/openapi config does not exist.

- [ ] **Step 5: Implement config and global exception handler**

Implement:

- `GatewayUser`: normalize roles by trimming and stripping `ROLE_`.
- `GatewayIdentityAuthenticationFilter`: parse positive `X-User-Id`, default missing roles to `USER`, set `ROLE_` authorities, clear context in `finally`.
- `NotificationProperties`: `@ConfigurationProperties(prefix = "notification")` record with `String internalToken`.
- `InternalTokenAuthenticationFilter`: for `POST /api/internal/notifications`, compare `X-Internal-Token` to configured token; if valid set `ROLE_INTERNAL`.
- `SecurityConfig`: stateless, csrf disabled, public health/swagger, internal endpoint `hasRole("INTERNAL")`, admin endpoint `hasRole("ADMIN")`, JSON 401/403 bodies.
- `OpenApiConfig`: title `Notification Service API`, version `v1`.
- `GlobalExceptionHandler`: map validation, unreadable body, missing identity, access denied, not found, no resource, media type, method, bad request binding, and unexpected exceptions.

Use explicit ordering:

```java
.addFilterBefore(internalTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(gatewayIdentityAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

`InternalTokenAuthenticationFilter` must only authenticate the internal endpoint so it does not override admin gateway identity on other paths.

- [ ] **Step 6: Run security and OpenAPI tests to verify GREEN**

Run:

```powershell
mvn -pl notification-service "-Dtest=SecurityConfigTests,OpenApiEndpointTests" test
```

Expected: PASS. Admin allowed tests may return `404` until controllers exist, but they must not return `401` or `403`.

- [ ] **Step 7: Commit**

```powershell
git add notification-service/src/main/java/com/example/ecommerce/notificationservice/dto notification-service/src/main/java/com/example/ecommerce/notificationservice/config notification-service/src/main/java/com/example/ecommerce/notificationservice/exception notification-service/src/test/java/com/example/ecommerce/notificationservice/dto notification-service/src/test/java/com/example/ecommerce/notificationservice/config notification-service/src/test/java/com/example/ecommerce/notificationservice/exception
git commit -m "feat: add notification API support infrastructure"
```

---

### Task 4: Notification Service Behavior

**Files:**

- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/service/NotificationService.java`
- Test: `notification-service/src/test/java/com/example/ecommerce/notificationservice/service/NotificationServiceTests.java`

- [ ] **Step 1: Write failing service tests**

Create `NotificationServiceTests` covering:

- `createNotificationStoresSentNotification`.
- `createNotificationStoresFailedNotification`.
- `findAdminNotificationsDelegatesCriteriaAndPageable`.
- `findAdminNotificationReturnsNotification`.
- `findAdminNotificationThrowsWhenMissing`.
- `mapsCreatedAtAsUtcInstant`.

Example test body:

```java
@Test
void createNotificationStoresSentNotification() {
    CreateNotificationRequest request = request(false);
    when(notificationRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 9000L));

    NotificationResponse response = notificationService.create(request);

    assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
    assertThat(response.failureReason()).isNull();
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(captor.capture());
    assertThat(captor.getValue().getType()).isEqualTo(NotificationType.PAYMENT_SUCCEEDED);
}
```

Run:

```powershell
mvn -pl notification-service "-Dtest=NotificationServiceTests" test
```

Expected: FAIL because `NotificationService` does not exist.

- [ ] **Step 2: Implement service behavior**

Implement `NotificationService` with:

```java
@Service
public class NotificationService {

    private static final String SIMULATED_FAILURE_REASON = "Simulated notification failure";

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        Notification notification = request.shouldSimulateFailure()
            ? Notification.failedEmail(request.userId(), request.orderId(), request.paymentId(), request.type(), request.recipient(), request.subject(), request.message(), SIMULATED_FAILURE_REASON)
            : Notification.sentEmail(request.userId(), request.orderId(), request.paymentId(), request.type(), request.recipient(), request.subject(), request.message());
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> findAdminNotifications(NotificationSearchCriteria criteria, Pageable pageable) {
        return notificationRepository.findAll(NotificationSpecifications.byCriteria(criteria), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NotificationResponse findAdminNotification(Long id) {
        return notificationRepository.findById(id).map(this::toResponse).orElseThrow(() -> new NotificationNotFoundException(id));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getUserId(),
            notification.getOrderId(),
            notification.getPaymentId(),
            notification.getType(),
            notification.getChannel(),
            notification.getRecipient(),
            notification.getStatus(),
            notification.getSubject(),
            notification.getMessage(),
            notification.getFailureReason(),
            toInstant(notification.getCreatedAt())
        );
    }

    private static Instant toInstant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }
}
```

- [ ] **Step 3: Run service tests to verify GREEN**

Run:

```powershell
mvn -pl notification-service "-Dtest=NotificationServiceTests" test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add notification-service/src/main/java/com/example/ecommerce/notificationservice/service notification-service/src/test/java/com/example/ecommerce/notificationservice/service
git commit -m "feat: add notification service behavior"
```

---

### Task 5: Internal And Admin Controllers

**Files:**

- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/controller/InternalNotificationController.java`
- Create: `notification-service/src/main/java/com/example/ecommerce/notificationservice/controller/AdminNotificationController.java`
- Test: `notification-service/src/test/java/com/example/ecommerce/notificationservice/controller/InternalNotificationControllerTests.java`
- Test: `notification-service/src/test/java/com/example/ecommerce/notificationservice/controller/AdminNotificationControllerTests.java`

- [ ] **Step 1: Write failing internal controller tests**

Create tests covering:

- valid internal token and request returns `201`.
- missing token returns `401`.
- invalid token returns `401`.
- validation error returns `400`.
- unsupported content type returns `415`.
- unsupported method returns `405`.

Use `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@MockitoBean NotificationService`, and properties including:

```text
notification.internal-token=test-internal-token
```

Run:

```powershell
mvn -pl notification-service "-Dtest=InternalNotificationControllerTests" test
```

Expected: FAIL because the controller does not exist.

- [ ] **Step 2: Implement internal controller**

Create:

```java
package com.example.ecommerce.notificationservice.controller;

import com.example.ecommerce.notificationservice.dto.CreateNotificationRequest;
import com.example.ecommerce.notificationservice.dto.NotificationResponse;
import com.example.ecommerce.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/notifications")
public class InternalNotificationController {

    private final NotificationService notificationService;

    public InternalNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest request) {
        return notificationService.create(request);
    }
}
```

- [ ] **Step 3: Run internal controller tests to verify GREEN**

Run:

```powershell
mvn -pl notification-service "-Dtest=InternalNotificationControllerTests" test
```

Expected: PASS.

- [ ] **Step 4: Write failing admin controller tests**

Create tests covering:

- `GET /api/admin/notifications?type=PAYMENT_SUCCEEDED&status=SENT&userId=10&orderId=1000&paymentId=5000` delegates all filters.
- `GET /api/admin/notifications` defaults pageable sort to `createdAt DESC`.
- `GET /api/admin/notifications/{id}` returns detail.
- missing gateway user returns `401`.
- user role returns `403`.
- missing notification maps to `404`.

Run:

```powershell
mvn -pl notification-service "-Dtest=AdminNotificationControllerTests" test
```

Expected: FAIL because admin controller does not exist.

- [ ] **Step 5: Implement admin controller**

Create:

```java
package com.example.ecommerce.notificationservice.controller;

import com.example.ecommerce.notificationservice.dto.NotificationResponse;
import com.example.ecommerce.notificationservice.dto.NotificationSearchCriteria;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import com.example.ecommerce.notificationservice.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Page<NotificationResponse> list(
        @RequestParam(required = false) NotificationType type,
        @RequestParam(required = false) NotificationStatus status,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) Long paymentId,
        @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return notificationService.findAdminNotifications(
            new NotificationSearchCriteria(type, status, userId, orderId, paymentId),
            pageable
        );
    }

    @GetMapping("/{id}")
    public NotificationResponse get(@PathVariable Long id) {
        return notificationService.findAdminNotification(id);
    }
}
```

- [ ] **Step 6: Run controller tests to verify GREEN**

Run:

```powershell
mvn -pl notification-service "-Dtest=InternalNotificationControllerTests,AdminNotificationControllerTests" test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add notification-service/src/main/java/com/example/ecommerce/notificationservice/controller notification-service/src/test/java/com/example/ecommerce/notificationservice/controller
git commit -m "feat: add notification APIs"
```

---

### Task 6: Gateway Routes

**Files:**

- Modify: `api-gateway/src/main/resources/application.yml`
- Modify: `api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java`

- [ ] **Step 1: Write failing gateway route test**

Modify `GatewayRoutesTests` expected route ids to include:

```java
"notification-service-admin",
"notification-service"
```

Add assertions:

```java
assertThat(pathPredicates(routesById.get("notification-service-admin")))
    .contains("/api/admin/notifications/**");
assertThat(pathPredicates(routesById.get("notification-service")))
    .contains("/api/notifications/**");
```

Run:

```powershell
mvn -pl api-gateway "-Dtest=GatewayRoutesTests" test
```

Expected: FAIL because `notification-service-admin` is not configured.

- [ ] **Step 2: Add gateway admin route**

Modify `api-gateway/src/main/resources/application.yml` so notification routes become:

```yaml
            - id: notification-service-admin
              uri: lb://notification-service
              predicates:
                - Path=/api/admin/notifications/**
            - id: notification-service
              uri: lb://notification-service
              predicates:
                - Path=/api/notifications/**
```

Keep the existing `notification-service` route for compatibility.

- [ ] **Step 3: Run gateway route test to verify GREEN**

Run:

```powershell
mvn -pl api-gateway "-Dtest=GatewayRoutesTests" test
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add api-gateway/src/main/resources/application.yml api-gateway/src/test/java/com/example/ecommerce/apigateway/route/GatewayRoutesTests.java
git commit -m "feat: route notification admin APIs through gateway"
```

---

### Task 7: Runtime Wiring, Docker, CI, And README

**Files:**

- Create: `notification-service/Dockerfile`
- Modify: existing service Dockerfiles to copy `notification-service/pom.xml`
- Modify: `docker-compose.yml`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`

- [ ] **Step 1: Run failing runtime checks**

Run:

```powershell
docker compose config --quiet
docker compose build notification-service
```

Expected before implementation: `docker compose config --quiet` may pass, and `docker compose build notification-service` fails with `no such service: notification-service`.

- [ ] **Step 2: Add notification-service Dockerfile**

Create `notification-service/Dockerfile`:

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
COPY notification-service/pom.xml notification-service/pom.xml
COPY notification-service/src notification-service/src

RUN mvn -pl notification-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/notification-service/target/*.jar app.jar

EXPOSE 8087

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Add this line to every existing service Dockerfile:

```dockerfile
COPY notification-service/pom.xml notification-service/pom.xml
```

Update:

- `eureka-server/Dockerfile`
- `api-gateway/Dockerfile`
- `auth-service/Dockerfile`
- `product-service/Dockerfile`
- `inventory-service/Dockerfile`
- `cart-service/Dockerfile`
- `order-service/Dockerfile`
- `payment-service/Dockerfile`

- [ ] **Step 3: Update Docker Compose**

Add:

```yaml
  notification-postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: notification_db
      POSTGRES_USER: ecommerce
      POSTGRES_PASSWORD: ecommerce
    ports:
      - "127.0.0.1:5438:5432"
    volumes:
      - notification-postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ecommerce -d notification_db"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 10s
```

Add:

```yaml
  notification-service:
    build:
      context: .
      dockerfile: notification-service/Dockerfile
    container_name: ecommerce-notification-service
    depends_on:
      notification-postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    restart: on-failure
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: jdbc:postgresql://notification-postgres:5432/notification_db
      SPRING_DATASOURCE_USERNAME: ecommerce
      SPRING_DATASOURCE_PASSWORD: ecommerce
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:8761/eureka/
      EUREKA_INSTANCE_LEASE_RENEWAL_INTERVAL_IN_SECONDS: 5
      EUREKA_INSTANCE_LEASE_EXPIRATION_DURATION_IN_SECONDS: 15
      NOTIFICATION_INTERNAL_TOKEN: local-notification-token
    ports:
      - "8087:8087"
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8087/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s
```

Add to `api-gateway.depends_on`:

```yaml
      notification-service:
        condition: service_healthy
```

Add volume:

```yaml
  notification-postgres-data:
```

- [ ] **Step 4: Update CI and README**

In `.github/workflows/ci.yml`, add:

```yaml
          docker compose build notification-service
```

In `README.md`, document:

- notification-service port `8087`.
- notification database port `5438`.
- `POST /api/internal/notifications` with `X-Internal-Token`.
- `GET /api/admin/notifications`.
- `GET /api/admin/notifications/{id}`.
- Update Maven build/test command blocks to include `notification-service`.
- Update sequential Docker build commands to include `notification-service`.
- Update Compose startup command to include `notification-postgres` and `notification-service`.
- Update Eureka wait list to include `NOTIFICATION-SERVICE`.

- [ ] **Step 5: Run runtime verification**

Run:

```powershell
mvn -pl notification-service test
docker compose config --quiet
docker compose build notification-service
```

Expected: all commands exit `0`.

- [ ] **Step 6: Commit**

```powershell
git add notification-service/Dockerfile docker-compose.yml .github/workflows/ci.yml README.md eureka-server/Dockerfile api-gateway/Dockerfile auth-service/Dockerfile product-service/Dockerfile inventory-service/Dockerfile cart-service/Dockerfile order-service/Dockerfile payment-service/Dockerfile
git commit -m "chore: wire notification service runtime"
```

---

### Task 8: Full Verification And Review

**Files:**

- No planned production changes unless review finds issues.

- [ ] **Step 1: Run full Maven verification**

Run:

```powershell
New-Item -ItemType Directory -Force -Path D:\spring\.tmp\notification-service | Out-Null
$env:JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=D:\spring\.tmp\notification-service'
mvn -B -ntp clean test
Remove-Item Env:JAVA_TOOL_OPTIONS
```

Expected:

- Reactor includes `notification-service`.
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
"eureka-server","api-gateway","auth-service","product-service","inventory-service","cart-service","order-service","payment-service","notification-service" | ForEach-Object { docker compose build -q $_ }
```

Expected: both commands exit `0`.

- [ ] **Step 4: Request final code review**

Request review over the full `notification-service` branch with focus on:

- spec compliance.
- no Kafka dependencies in this branch.
- internal token security.
- admin authorization.
- filtering correctness.
- notification status simulation.
- error mapping.
- Docker Compose and CI wiring.

- [ ] **Step 5: Fix Critical and Important findings**

For each Critical or Important finding:

1. Write or update a failing test that reproduces the issue.
2. Run the targeted test and verify it fails for the expected reason.
3. Implement the smallest fix.
4. Run the targeted test and relevant module tests.
5. Commit the fix with message `fix: ...`.

- [ ] **Step 6: Push branch**

Run:

```powershell
git status --short --branch
git push -u origin notification-service
```

Expected: clean working tree and successful push.
