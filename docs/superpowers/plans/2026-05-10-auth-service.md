# Auth Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `auth-service` with user registration, login, BCrypt password hashing, role support, JWT issuing, validation/error handling, OpenAPI docs, PostgreSQL runtime wiring, and focused tests.

**Architecture:** `auth-service` is a standalone Spring Boot MVC service registered with Eureka and routed through `api-gateway` at `/api/auth/**`. It owns credential data in `auth_users`, stores BCrypt password hashes only, issues HS256 JWTs using the same `JWT_SECRET` expected by the gateway, and keeps business logic in the service layer.

**Tech Stack:** Java 21, Maven, Spring Boot 3.5.14, Spring Cloud 2025.0.2, Spring Web MVC, Spring Security, Spring Data JPA, PostgreSQL, H2 for tests, Spring Validation, Springdoc OpenAPI 3.0.3, Eureka Client, Actuator, JUnit 5, Mockito, Docker Compose.

---

## Scope Check

This plan covers only `auth-service`:

- Register user.
- Login user.
- BCrypt password hashing.
- JWT access token issuing.
- Roles: `USER`, `ADMIN`.
- Auth database model.
- Validation and global error responses.
- Swagger/OpenAPI for auth-service.
- Dockerfile and Docker Compose wiring with PostgreSQL.
- Gateway route already exists from `/api/auth/**` to `auth-service`.

This plan does not implement refresh tokens, `user-service`, profile creation events, OAuth login, email verification, password reset, Kafka, Redis, product, inventory, cart, order, payment, or notification features.

## Reference Notes

- Springdoc `springdoc-openapi-starter-webmvc-ui` version `3.0.3` is available on Maven Central as of 2026-04-11.
- JWT signing uses Spring Security's `NimbusJwtEncoder` with HS256, matching the gateway's `NimbusReactiveJwtDecoder`.
- The runtime `JWT_SECRET` must be provided by environment. Tests may use a fixed dummy secret.

## File Structure

Repository root: `D:/spring/.worktrees/auth-service`

Files created by this plan:

- `auth-service/pom.xml`
- `auth-service/src/main/java/com/example/ecommerce/authservice/AuthServiceApplication.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/config/OpenApiConfig.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/config/PasswordConfig.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/config/SecurityConfig.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/config/JwtConfig.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/controller/AuthController.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/dto/AuthResponse.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/dto/LoginRequest.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/dto/RegisterRequest.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/entity/AuthUser.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/entity/Role.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/exception/ApiErrorResponse.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/exception/DuplicateEmailException.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/exception/GlobalExceptionHandler.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/exception/InvalidCredentialsException.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/repository/AuthUserRepository.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/service/AuthService.java`
- `auth-service/src/main/java/com/example/ecommerce/authservice/service/JwtTokenService.java`
- `auth-service/src/main/resources/application.yml`
- `auth-service/src/test/java/com/example/ecommerce/authservice/AuthServiceApplicationTests.java`
- `auth-service/src/test/java/com/example/ecommerce/authservice/controller/AuthControllerTests.java`
- `auth-service/src/test/java/com/example/ecommerce/authservice/repository/AuthUserRepositoryTests.java`
- `auth-service/src/test/java/com/example/ecommerce/authservice/service/AuthServiceTests.java`
- `auth-service/src/test/java/com/example/ecommerce/authservice/service/JwtTokenServiceTests.java`
- `auth-service/Dockerfile`

Files modified by this plan:

- `pom.xml`
- `docker-compose.yml`
- `README.md`
- `eureka-server/Dockerfile`
- `api-gateway/Dockerfile`

---

## API Contract

### Register

`POST /api/auth/register`

Request:

```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "fullName": "Test User"
}
```

Success response `201 Created`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": 1,
  "email": "user@example.com",
  "roles": ["USER"]
}
```

### Login

`POST /api/auth/login`

Request:

```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

Success response `200 OK`: same body as register.

### Error Response

Validation and business errors use:

```json
{
  "timestamp": "2026-05-10T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/auth/register",
  "details": [
    {
      "field": "email",
      "message": "must be a well-formed email address"
    }
  ]
}
```

## Data Model

Table: `auth_users`

- `id`: generated primary key.
- `email`: unique, required.
- `password_hash`: required BCrypt hash.
- `roles`: element collection of enum values.
- `enabled`: boolean.
- `created_at`: creation timestamp.
- `updated_at`: update timestamp.

`fullName` is accepted in `RegisterRequest` for future profile creation but is not persisted in auth-service because profile data belongs to `user-service`.

---

## Task 1: Module Skeleton and Red Context Test

**Files:**

- Create: `D:/spring/.worktrees/auth-service/auth-service/pom.xml`
- Create: `D:/spring/.worktrees/auth-service/auth-service/src/test/java/com/example/ecommerce/authservice/AuthServiceApplicationTests.java`
- Modify: `D:/spring/.worktrees/auth-service/pom.xml`

- [ ] **Step 1: Create `auth-service/pom.xml`**

Include these dependencies:

- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-boot-starter-oauth2-resource-server`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-actuator`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3`
- `org.postgresql:postgresql` runtime
- `com.h2database:h2` test
- `spring-boot-starter-test` test
- `spring-security-test` test

Use parent:

```xml
<parent>
    <groupId>com.example.ecommerce</groupId>
    <artifactId>ecommerce-microservices</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

- [ ] **Step 2: Register `auth-service` in root `pom.xml`**

Set modules to:

```xml
<modules>
    <module>eureka-server</module>
    <module>api-gateway</module>
    <module>auth-service</module>
</modules>
```

- [ ] **Step 3: Write the red context test**

Create `AuthServiceApplicationTests.java`:

```java
package com.example.ecommerce.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = AuthServiceApplication.class,
    properties = {
        "eureka.client.enabled=false",
        "security.jwt.secret=01234567890123456789012345678901",
        "spring.datasource.url=jdbc:h2:mem:auth_service_context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Run the red test**

Run:

```powershell
mvn -pl auth-service -am test
```

Expected: compilation fails because `AuthServiceApplication` does not exist.

Do not commit yet.

---

## Task 2: Application Configuration and Domain Model

**Files:**

- Create: `AuthServiceApplication.java`
- Create: `application.yml`
- Create: `Role.java`
- Create: `AuthUser.java`
- Create: `AuthUserRepository.java`
- Create: `AuthUserRepositoryTests.java`
- Create: `PasswordConfig.java`
- Create: `SecurityConfig.java`
- Create: `JwtConfig.java`
- Create: `OpenApiConfig.java`

- [ ] **Step 1: Create the application class**

`AuthServiceApplication` must be a standard `@SpringBootApplication`.

- [ ] **Step 2: Create runtime configuration**

`application.yml` must include:

```yaml
spring:
  application:
    name: auth-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/auth_db}
    username: ${SPRING_DATASOURCE_USERNAME:ecommerce}
    password: ${SPRING_DATASOURCE_PASSWORD:ecommerce}
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

server:
  port: 8081

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_CLIENT_SERVICEURL_DEFAULTZONE:http://localhost:8761/eureka/}
  instance:
    prefer-ip-address: true

security:
  jwt:
    secret: ${JWT_SECRET}
    issuer: ${JWT_ISSUER:ecommerce-auth-service}
    expiration-seconds: ${JWT_EXPIRATION_SECONDS:3600}

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

- [ ] **Step 3: Create `Role` enum**

Values:

```java
USER, ADMIN
```

- [ ] **Step 4: Create `AuthUser` entity**

Requirements:

- Table name `auth_users`.
- `id` generated with identity strategy.
- `email` unique and required.
- `passwordHash` maps to `password_hash` and is required.
- `roles` is an eager `@ElementCollection` of enum strings in table `auth_user_roles`.
- `enabled` defaults to true.
- `createdAt` and `updatedAt` are maintained with `@PrePersist` and `@PreUpdate`.
- Provide package-private no-args constructor for JPA.
- Provide a static factory `create(String email, String passwordHash, Set<Role> roles)`.
- Provide getters.

- [ ] **Step 5: Create repository**

`AuthUserRepository` extends `JpaRepository<AuthUser, Long>` and declares:

```java
Optional<AuthUser> findByEmailIgnoreCase(String email);
boolean existsByEmailIgnoreCase(String email);
```

- [ ] **Step 6: Create repository tests**

Use `@DataJpaTest` and H2. Test:

- `findByEmailIgnoreCase` finds a saved user with different email casing.
- unique email constraint prevents duplicate email values.

- [ ] **Step 7: Create config classes**

`PasswordConfig` exposes a `BCryptPasswordEncoder`.

`SecurityConfig`:

- disables CSRF
- permits `/api/auth/**`, `/actuator/health`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`
- requires authentication for other endpoints
- configures stateless session management

`JwtConfig`:

- exposes a `JwtEncoder` bean using `ImmutableSecret<SecurityContext>`
- key comes from `security.jwt.secret`
- uses UTF-8 bytes

`OpenApiConfig`:

- exposes an `OpenAPI` bean with title `Auth Service API` and version `v1`

- [ ] **Step 8: Run tests**

Run:

```powershell
mvn -pl auth-service -am test
```

Expected: context and repository tests pass.

Do not commit yet; service behavior is not implemented.

---

## Task 3: DTOs, Exceptions, JWT Service, and Auth Service

**Files:**

- Create: `RegisterRequest.java`
- Create: `LoginRequest.java`
- Create: `AuthResponse.java`
- Create: `DuplicateEmailException.java`
- Create: `InvalidCredentialsException.java`
- Create: `ApiErrorResponse.java`
- Create: `JwtTokenService.java`
- Create: `AuthService.java`
- Create: `JwtTokenServiceTests.java`
- Create: `AuthServiceTests.java`

- [ ] **Step 1: Create DTO records**

`RegisterRequest`:

- `@Email @NotBlank String email`
- `@NotBlank @Size(min = 8, max = 100) String password`
- `@NotBlank @Size(max = 120) String fullName`

`LoginRequest`:

- `@Email @NotBlank String email`
- `@NotBlank String password`

`AuthResponse`:

- `String accessToken`
- `String tokenType`
- `long expiresIn`
- `Long userId`
- `String email`
- `Set<Role> roles`

- [ ] **Step 2: Create exceptions and error response**

`DuplicateEmailException` message: `Email is already registered`.

`InvalidCredentialsException` message: `Invalid email or password`.

`ApiErrorResponse` must be a record with:

- `Instant timestamp`
- `int status`
- `String error`
- `String message`
- `String path`
- `List<FieldErrorDetail> details`

Nested record `FieldErrorDetail(String field, String message)`.

- [ ] **Step 3: Create `JwtTokenService`**

Responsibilities:

- Accept `JwtEncoder`, issuer, expiration seconds.
- Issue HS256 JWT with claims:
  - `sub`: user id as string
  - `email`
  - `roles`: role names as list
  - `iss`
  - `iat`
  - `exp`
- Return token string.
- Expose `expiresInSeconds()`.

- [ ] **Step 4: Create `AuthService`**

Responsibilities:

- `register(RegisterRequest request)`:
  - normalize email with `trim().toLowerCase(Locale.ROOT)`
  - reject duplicate email
  - encode password with BCrypt
  - create user with role `USER`
  - save user
  - issue token
  - return `AuthResponse`
- `login(LoginRequest request)`:
  - normalize email
  - find user
  - reject missing user, disabled user, or password mismatch
  - issue token
  - return `AuthResponse`

- [ ] **Step 5: Create tests**

`JwtTokenServiceTests`:

- token contains subject, email, roles, issuer.
- `expiresInSeconds()` returns configured expiration.

`AuthServiceTests` with Mockito:

- register hashes password, assigns `USER`, saves normalized email, returns token.
- register rejects duplicate email.
- login returns token for matching password.
- login rejects wrong password.
- login rejects unknown email.

- [ ] **Step 6: Run tests**

Run:

```powershell
mvn -pl auth-service -am test
```

Expected: all auth-service tests pass.

- [ ] **Step 7: Commit domain and service behavior**

Run:

```powershell
git add pom.xml auth-service
git commit -m "feat: add auth service domain and authentication"
```

Expected: commit succeeds.

---

## Task 4: Controller and Global Exception Handling

**Files:**

- Create: `AuthController.java`
- Create: `GlobalExceptionHandler.java`
- Create: `AuthControllerTests.java`

- [ ] **Step 1: Create controller**

`AuthController`:

- base path `/api/auth`
- `POST /register` returns `201 Created`
- `POST /login` returns `200 OK`
- accepts validated request bodies
- delegates to `AuthService`

- [ ] **Step 2: Create global exception handler**

Handle:

- `MethodArgumentNotValidException` -> `400 Bad Request` with field details.
- `DuplicateEmailException` -> `409 Conflict`.
- `InvalidCredentialsException` -> `401 Unauthorized`.
- fallback `Exception` -> `500 Internal Server Error`.

Use `ApiErrorResponse`.

- [ ] **Step 3: Create controller tests**

Use `@WebMvcTest(AuthController.class)` with mocked `AuthService`.

Tests:

- register valid request returns `201` and token response.
- login valid request returns `200` and token response.
- register invalid email returns `400`.
- duplicate email returns `409`.
- invalid credentials returns `401`.

- [ ] **Step 4: Run tests**

Run:

```powershell
mvn -pl auth-service -am test
```

Expected: all tests pass.

- [ ] **Step 5: Commit controller and error handling**

Run:

```powershell
git add auth-service
git commit -m "feat: expose auth endpoints"
```

Expected: commit succeeds.

---

## Task 5: Docker Compose, Runtime Wiring, and README

**Files:**

- Create: `auth-service/Dockerfile`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `eureka-server/Dockerfile`
- Modify: `api-gateway/Dockerfile`

- [ ] **Step 1: Create `auth-service/Dockerfile`**

Dockerfile must:

- Use `maven:3.9-eclipse-temurin-21` build stage.
- Copy root `pom.xml`.
- Copy all module POM files required by root reactor: `eureka-server/pom.xml`, `api-gateway/pom.xml`, `auth-service/pom.xml`.
- Copy `auth-service/src`.
- Run `mvn -pl auth-service -am package -DskipTests`.
- Use `eclipse-temurin:21-jre` runtime.
- Expose `8081`.
- Run `/app/app.jar`.

- [ ] **Step 2: Update existing Dockerfiles for reactor compatibility**

Because root `pom.xml` now lists three modules, existing service Dockerfiles must copy all module POMs:

- `eureka-server/Dockerfile` must copy `api-gateway/pom.xml` and `auth-service/pom.xml`.
- `api-gateway/Dockerfile` must copy `auth-service/pom.xml`.

This prevents Maven from failing with missing child module paths inside Docker builds.

- [ ] **Step 3: Update Compose**

`docker-compose.yml` must include:

- `postgres` service using `postgres:17-alpine`
  - `POSTGRES_DB=auth_db`
  - `POSTGRES_USER=ecommerce`
  - `POSTGRES_PASSWORD=ecommerce`
  - port `5432:5432`
  - named volume `postgres-data`
- existing `eureka-server`
- existing `api-gateway`
- new `auth-service`
  - build from `auth-service/Dockerfile`
  - container `ecommerce-auth-service`
  - depends on `postgres` and `eureka-server`
  - env:
    - `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/auth_db`
    - `SPRING_DATASOURCE_USERNAME=ecommerce`
    - `SPRING_DATASOURCE_PASSWORD=ecommerce`
    - `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/`
    - `JWT_SECRET=01234567890123456789012345678901`
  - port `8081:8081`

`api-gateway` must keep `JWT_SECRET` with the same local dummy value.

- [ ] **Step 4: Update README**

README must include:

- current milestone includes `auth-service`
- test command: `mvn -pl auth-service -am test`
- build command: `mvn -pl auth-service -am clean package`
- Compose command: `docker compose up --build postgres eureka-server auth-service api-gateway`
- register/login curl examples through gateway:
  - `POST http://localhost:8080/api/auth/register`
  - `POST http://localhost:8080/api/auth/login`
- Swagger URL:
  - direct service: `http://localhost:8081/swagger-ui.html`

- [ ] **Step 5: Verify**

Run:

```powershell
mvn -pl auth-service -am test
docker compose config
docker compose build eureka-server api-gateway auth-service
```

Expected:

- Maven tests pass.
- Compose config includes `postgres`, `eureka-server`, `api-gateway`, and `auth-service`.
- Docker builds pass if Docker daemon is running.

- [ ] **Step 6: Commit runtime wiring**

Run:

```powershell
git add auth-service/Dockerfile eureka-server/Dockerfile api-gateway/Dockerfile docker-compose.yml README.md
git commit -m "chore: wire auth service into local runtime"
```

Expected: commit succeeds.

---

## Task 6: Final Review and Handoff

**Files:**

- Inspect all files created or modified by this plan.

- [ ] **Step 1: Run final verification**

Run:

```powershell
git diff --check
mvn -pl auth-service -am test
mvn -pl auth-service -am package
docker compose config
docker compose build eureka-server api-gateway auth-service
git status --short
```

Expected:

- no whitespace errors
- Maven tests pass
- Maven package passes
- Compose config valid
- Docker builds pass when Docker daemon is running
- working tree clean

- [ ] **Step 2: Push branch**

Run:

```powershell
git push -u origin auth-service
```

Expected: branch pushes to GitHub.

- [ ] **Step 3: Report completion**

Include:

- files changed
- why changed
- tests run
- Docker status
- how to run
- next recommended plan: `product-service`

## Plan Self-Review

Spec coverage in this plan:

- Covers `auth-service` register/login with JWT.
- Covers BCrypt password hashing.
- Covers roles `USER` and `ADMIN` in the entity and JWT claim model.
- Covers JPA persistence with auth-owned database table.
- Covers validation and global error responses.
- Covers OpenAPI/Swagger for auth-service.
- Covers health endpoints.
- Covers Dockerfile and Docker Compose local runtime.
- Covers unit, controller, and repository tests.

Requirements assigned to later plans:

- Refresh tokens.
- User profile creation in `user-service`.
- End-to-end auth through gateway after compose runtime is started.
- Admin bootstrap strategy beyond database seeding/manual insert.
