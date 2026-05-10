# E-Commerce Microservices

Java Spring Boot microservices e-commerce system.

## Current Milestone

The repository currently contains the Maven parent project, `eureka-server`, `api-gateway`, and `auth-service`.

## Stack

- Java 21
- Spring Boot 3.5.14
- Spring Cloud 2025.0.2
- Maven
- Docker Compose
- PostgreSQL
- Eureka Server
- Spring Cloud Gateway
- JWT Authentication
- Spring Security
- Spring Data JPA
- OpenAPI/Swagger

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop or Docker Engine with Docker Compose

## Build

```powershell
mvn -pl eureka-server -am clean package
mvn -pl api-gateway -am clean package
mvn -pl auth-service -am clean package
```

## Test

```powershell
mvn -pl eureka-server -am test
mvn -pl api-gateway -am test
mvn -pl auth-service -am test
```

## Run Eureka Locally

```powershell
mvn -pl eureka-server spring-boot:run
```

Eureka dashboard:

```text
http://localhost:8761
```

Health endpoint:

```text
http://localhost:8761/actuator/health
```

## Run API Gateway Locally

```powershell
$env:JWT_SECRET="01234567890123456789012345678901"
$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE="http://localhost:8761/eureka/"
mvn -pl api-gateway spring-boot:run
```

API Gateway health endpoint:

```text
http://localhost:8080/actuator/health
```

## Run Auth Service Locally

Start PostgreSQL with an `auth_db` database, then run:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/auth_db"
$env:SPRING_DATASOURCE_USERNAME="ecommerce"
$env:SPRING_DATASOURCE_PASSWORD="ecommerce"
$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE="http://localhost:8761/eureka/"
$env:JWT_SECRET="01234567890123456789012345678901"
mvn -pl auth-service spring-boot:run
```

Auth Service health endpoint:

```text
http://localhost:8081/actuator/health
```

Swagger UI:

```text
http://localhost:8081/swagger-ui.html
```

## Run with Docker Compose

```powershell
docker compose up --build postgres eureka-server auth-service api-gateway
```

Eureka is published on `http://localhost:8761`, the API Gateway is published on `http://localhost:8080`, and the Auth Service is published on `http://localhost:8081`.

PostgreSQL is bound to `127.0.0.1:5432` for local development. The bundled `ecommerce` credentials, JWT secret, and database port exposure are for local development only.

Before sending gateway requests, wait until the Compose containers report healthy and `AUTH-SERVICE` appears in the Eureka dashboard.

Register through the gateway:

```powershell
curl.exe -X POST http://localhost:8080/api/auth/register `
  -H "Content-Type: application/json" `
  -d '{"email":"customer@example.com","password":"password123","fullName":"Example Customer"}'
```

Log in through the gateway:

```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"customer@example.com","password":"password123"}'
```
