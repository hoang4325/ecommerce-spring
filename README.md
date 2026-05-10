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
- Spring Cloud Gateway

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop or Docker Engine with Docker Compose

## Build

```powershell
mvn -pl eureka-server -am clean package
mvn -pl api-gateway -am clean package
```

## Test

```powershell
mvn -pl eureka-server -am test
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

## Run with Docker Compose

```powershell
docker compose up --build eureka-server api-gateway
```

Eureka is published on `http://localhost:8761` and the API Gateway is published on `http://localhost:8080`.
