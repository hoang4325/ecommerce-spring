# E-Commerce Microservices

Java Spring Boot microservices e-commerce system.

## Current Milestone

The repository currently contains the Maven parent project, `eureka-server`, `api-gateway`, `auth-service`, `product-service`, `inventory-service`, and `cart-service`.

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
mvn -pl product-service -am clean package
mvn -pl inventory-service -am clean package
mvn -pl cart-service -am clean package
```

## Test

```powershell
mvn -pl eureka-server -am test
mvn -pl api-gateway -am test
mvn -pl auth-service -am test
mvn -pl product-service -am test
mvn -pl inventory-service -am test
mvn -pl cart-service -am test
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

## Run Product Service Locally

Start PostgreSQL with a `product_db` database, then run:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/product_db"
$env:SPRING_DATASOURCE_USERNAME="ecommerce"
$env:SPRING_DATASOURCE_PASSWORD="ecommerce"
$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE="http://localhost:8761/eureka/"
mvn -pl product-service spring-boot:run
```

Product Service health endpoint:

```text
http://localhost:8082/actuator/health
```

Swagger UI:

```text
http://localhost:8082/swagger-ui.html
```

## Run Inventory Service Locally

Start PostgreSQL with an `inventory_db` database, then run:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5434/inventory_db"
$env:SPRING_DATASOURCE_USERNAME="ecommerce"
$env:SPRING_DATASOURCE_PASSWORD="ecommerce"
$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE="http://localhost:8761/eureka/"
mvn -pl inventory-service spring-boot:run
```

Inventory Service health endpoint:

```text
http://localhost:8083/actuator/health
```

Swagger UI:

```text
http://localhost:8083/swagger-ui.html
```

## Run Cart Service Locally

Start PostgreSQL with a `cart_db` database, then run:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5435/cart_db"
$env:SPRING_DATASOURCE_USERNAME="ecommerce"
$env:SPRING_DATASOURCE_PASSWORD="ecommerce"
$env:EUREKA_CLIENT_SERVICEURL_DEFAULTZONE="http://localhost:8761/eureka/"
$env:PRODUCT_SERVICE_BASE_URL="http://localhost:8082"
mvn -pl cart-service spring-boot:run
```

Cart Service health endpoint:

```text
http://localhost:8084/actuator/health
```

Swagger UI:

```text
http://localhost:8084/swagger-ui.html
```

## Run with Docker Compose

```powershell
docker compose up --build postgres product-postgres inventory-postgres cart-postgres eureka-server auth-service product-service inventory-service cart-service api-gateway
```

Eureka is published on `http://localhost:8761`, the API Gateway is published on `http://localhost:8080`, the Auth Service is published on `http://localhost:8081`, the Product Service is published on `http://localhost:8082`, the Inventory Service is published on `http://localhost:8083`, and the Cart Service is published on `http://localhost:8084`.

Auth PostgreSQL is bound to `127.0.0.1:5432`, Product PostgreSQL is bound to `127.0.0.1:5433`, Inventory PostgreSQL is bound to `127.0.0.1:5434`, and Cart PostgreSQL is bound to `127.0.0.1:5435` for local development. The bundled `ecommerce` database credentials, JWT secret, and database port exposure are for local development only.

Before sending gateway requests, wait until the Compose containers report healthy and `AUTH-SERVICE`, `PRODUCT-SERVICE`, `INVENTORY-SERVICE`, and `CART-SERVICE` appear in the Eureka dashboard.

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

Public catalog reads through the gateway:

```powershell
curl.exe http://localhost:8080/api/categories

curl.exe http://localhost:8080/api/products

curl.exe "http://localhost:8080/api/products?keyword=coffee&categorySlug=coffee-gear"
```

Admin catalog writes through the gateway require `Authorization: Bearer <token>`. The token must include the `ADMIN` role in its `roles` claim.

```powershell
curl.exe -X POST http://localhost:8080/api/categories `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{"name":"Coffee Gear","slug":"coffee-gear","description":"Brewing tools and accessories"}'

curl.exe -X POST http://localhost:8080/api/products `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{"categoryId":1,"name":"Pour Over Kit","slug":"pour-over-kit","description":"Starter pour-over bundle","price":49.99,"imageUrl":"https://example.com/pour-over-kit.jpg"}'

curl.exe -X PUT http://localhost:8080/api/products/1 `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{"categoryId":1,"name":"Pour Over Kit","slug":"pour-over-kit","description":"Updated starter bundle","price":44.99,"imageUrl":"https://example.com/pour-over-kit.jpg"}'

curl.exe -X DELETE http://localhost:8080/api/products/1 `
  -H "Authorization: Bearer <token>"
```

Admin inventory writes through the gateway also require `Authorization: Bearer <token>`. The token must include the `ADMIN` role in its `roles` claim.

```powershell
curl.exe -X PUT http://localhost:8080/api/inventory/items/1 `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{"availableQuantity":25}'

curl.exe -X POST http://localhost:8080/api/inventory/items/1/adjust `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{"delta":5}'

curl.exe http://localhost:8080/api/inventory/items/1 `
  -H "Authorization: Bearer <token>"

curl.exe -X POST http://localhost:8080/api/inventory/reservations `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{"orderId":1001,"items":[{"productId":1,"quantity":2}]}'

curl.exe -X POST http://localhost:8080/api/inventory/reservations/1001/release `
  -H "Authorization: Bearer <token>"

curl.exe -X POST http://localhost:8080/api/inventory/reservations/1001/deduct `
  -H "Authorization: Bearer <token>"
```

Cart requests through the gateway require `Authorization: Bearer <token>`.

```powershell
curl.exe http://localhost:8080/api/cart `
  -H "Authorization: Bearer <token>"

curl.exe -X POST http://localhost:8080/api/cart/items `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{"productId":1,"quantity":2}'

curl.exe -X PUT http://localhost:8080/api/cart/items/1 `
  -H "Authorization: Bearer <token>" `
  -H "Content-Type: application/json" `
  -d '{"quantity":3}'

curl.exe -X DELETE http://localhost:8080/api/cart/items/1 `
  -H "Authorization: Bearer <token>"

curl.exe -X DELETE http://localhost:8080/api/cart/items `
  -H "Authorization: Bearer <token>"
```
