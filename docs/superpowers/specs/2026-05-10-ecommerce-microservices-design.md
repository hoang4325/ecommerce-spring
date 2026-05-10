# E-Commerce Microservices Design

Date: 2026-05-10
Status: Approved direction, pending implementation plan

## Goal

Build a local-first e-commerce MVP using Java 21, Spring Boot 3, Spring Cloud Gateway, Eureka, PostgreSQL, Kafka, Redis, Docker Compose, Maven, REST APIs, and JWT authentication.

The first milestone is not Kubernetes and not Spring Cloud Config. The first milestone is a clean, modular microservices system that runs reliably on a local machine with Docker Compose.

## Repository Strategy

Use a Maven multi-module monorepo.

The root project owns shared build configuration only:

- Java version: 21
- Spring Boot 3 dependency management
- Spring Cloud dependency management
- Maven plugin management
- Common test defaults

Each service is an independent Spring Boot application module with its own runtime configuration, Dockerfile, tests, API boundaries, and database/schema ownership.

The repository will start with this shape:

```text
.
+-- pom.xml
+-- docker-compose.yml
+-- README.md
+-- docs/
|   +-- superpowers/
|       +-- specs/
+-- eureka-server/
+-- api-gateway/
+-- auth-service/
+-- user-service/
+-- product-service/
+-- inventory-service/
+-- cart-service/
+-- order-service/
+-- payment-service/
+-- notification-service/
```

## Services

### eureka-server

Provides service discovery for local microservice communication.

Responsibilities:

- Register service instances.
- Allow `api-gateway` and services to discover each other.
- Expose health and discovery UI endpoints for local development.

Non-goals:

- No authentication in the first MVP.
- No clustering in the first MVP.

### api-gateway

Single entry point for clients.

Responsibilities:

- Route requests to internal services.
- Validate JWT on protected routes.
- Allow unauthenticated access to auth endpoints, actuator health endpoints, and OpenAPI documentation in local development.
- Forward identity safely using headers such as `X-User-Id`, `X-User-Email`, and `X-User-Roles` after JWT validation.
- Configure CORS for local frontend use.

Initial route ownership:

- `/api/auth/**` -> `auth-service`
- `/api/users/**` -> `user-service`
- `/api/products/**` and `/api/categories/**` -> `product-service`
- `/api/inventory/**` -> `inventory-service`
- `/api/cart/**` -> `cart-service`
- `/api/orders/**` -> `order-service`
- `/api/payments/**` -> `payment-service`
- `/api/notifications/**` -> `notification-service`

### auth-service

Owns authentication and credentials.

Responsibilities:

- Register users.
- Login users.
- Hash passwords using BCrypt.
- Issue JWT access tokens.
- Represent roles: `USER`, `ADMIN`.
- Store only credential and identity fields needed for authentication.

Initial data model:

- `auth_users`
  - `id`
  - `email`
  - `password_hash`
  - `roles`
  - `enabled`
  - `created_at`
  - `updated_at`

Refresh tokens are deferred unless needed after the access-token flow is stable.

### user-service

Owns user profile information.

Responsibilities:

- Manage current user's profile.
- Manage user addresses.
- Expose internal lookup endpoints if another service needs user display data.

Initial data model:

- `user_profiles`
  - `id`
  - `auth_user_id`
  - `full_name`
  - `phone`
  - `created_at`
  - `updated_at`
- `user_addresses`
  - `id`
  - `user_profile_id`
  - `recipient_name`
  - `phone`
  - `line1`
  - `line2`
  - `city`
  - `state`
  - `postal_code`
  - `country`
  - `default_address`

The `auth_user_id` is treated as an external identity reference, not a shared entity relationship.

### product-service

Owns catalog data.

Responsibilities:

- Product CRUD.
- Category CRUD.
- Product listing.
- Product detail.
- Search products by keyword.
- Filter products by category.
- Restrict product and category management to `ADMIN`.

Initial data model:

- `categories`
  - `id`
  - `name`
  - `slug`
  - `description`
  - `active`
  - `created_at`
  - `updated_at`
- `products`
  - `id`
  - `category_id`
  - `name`
  - `slug`
  - `description`
  - `price`
  - `image_url`
  - `active`
  - `created_at`
  - `updated_at`

Product service does not own stock quantity. Stock belongs to inventory service.

### inventory-service

Owns stock and stock reservation.

Responsibilities:

- Manage stock quantity per product.
- Reserve stock when an order is created.
- Release stock if an order is cancelled.
- Deduct stock when payment succeeds.
- Publish reservation success or failure events.

Initial data model:

- `inventory_items`
  - `id`
  - `product_id`
  - `available_quantity`
  - `reserved_quantity`
  - `created_at`
  - `updated_at`
- `stock_reservations`
  - `id`
  - `order_id`
  - `product_id`
  - `quantity`
  - `status`
  - `created_at`
  - `updated_at`

Reservation status values:

- `RESERVED`
- `RELEASED`
- `DEDUCTED`
- `FAILED`

### cart-service

Owns shopping cart state.

Responsibilities:

- Add product to current user's cart.
- Update quantity.
- Remove item.
- Get current user's cart.
- Calculate subtotal from cart item snapshot data.

Storage:

- Redis for cart state.

Cart items will store a snapshot of product data needed for display and checkout:

- `productId`
- `productName`
- `unitPrice`
- `quantity`

The cart does not reserve stock. Stock reservation begins when order checkout starts.

### order-service

Owns orders and order state transitions.

Responsibilities:

- Create order from cart snapshot.
- Store order items.
- Manage order status.
- Expose order history for current user.
- Expose admin order management.
- Publish `OrderCreatedEvent`.
- React to inventory and payment events.

Initial data model:

- `orders`
  - `id`
  - `user_id`
  - `status`
  - `subtotal`
  - `created_at`
  - `updated_at`
- `order_items`
  - `id`
  - `order_id`
  - `product_id`
  - `product_name`
  - `unit_price`
  - `quantity`
  - `line_total`

Order status values:

- `PENDING`
- `STOCK_RESERVED`
- `PAYMENT_PENDING`
- `COMPLETED`
- `CANCELLED`

### payment-service

Owns payment records and simulated payment processing.

Responsibilities:

- Create payment attempt after stock is reserved.
- Simulate payment success or failure.
- Store payment status.
- Publish payment result events.

Initial data model:

- `payments`
  - `id`
  - `order_id`
  - `user_id`
  - `amount`
  - `status`
  - `failure_reason`
  - `created_at`
  - `updated_at`

Payment status values:

- `PENDING`
- `SUCCESS`
- `FAILED`

### notification-service

Owns notification logs.

Responsibilities:

- Consume order/payment events.
- Simulate email notification by logging.
- Store notification result.

Initial data model:

- `notifications`
  - `id`
  - `user_id`
  - `order_id`
  - `type`
  - `recipient`
  - `status`
  - `message`
  - `created_at`

## Communication

Use REST for simple synchronous requests:

- Client to gateway.
- Gateway to backend services.
- Cart service may query product service when adding an item to capture product name and price.
- Order service may query cart service during checkout.

Use Kafka for important business events:

- `OrderCreatedEvent`
- `StockReservedEvent`
- `StockReservationFailedEvent`
- `PaymentSucceededEvent`
- `PaymentFailedEvent`
- `OrderCompletedEvent`
- `OrderCancelledEvent`

## Event Flow

Primary successful checkout flow:

1. Client calls checkout through `api-gateway`.
2. `order-service` reads cart snapshot and creates order with status `PENDING`.
3. `order-service` publishes `OrderCreatedEvent`.
4. `inventory-service` consumes `OrderCreatedEvent`.
5. `inventory-service` reserves stock and publishes `StockReservedEvent`.
6. `order-service` consumes `StockReservedEvent` and updates order to `STOCK_RESERVED`.
7. `payment-service` consumes `StockReservedEvent` and creates a simulated payment.
8. `payment-service` publishes `PaymentSucceededEvent`.
9. `order-service` consumes `PaymentSucceededEvent`, updates order to `COMPLETED`, and publishes `OrderCompletedEvent`.
10. `inventory-service` consumes `PaymentSucceededEvent` and deducts reserved stock.
11. `notification-service` consumes completion/payment event and logs notification.

Failure flow when stock cannot be reserved:

1. `inventory-service` consumes `OrderCreatedEvent`.
2. Stock is insufficient.
3. `inventory-service` publishes `StockReservationFailedEvent`.
4. `order-service` marks order as `CANCELLED`.
5. `order-service` publishes `OrderCancelledEvent`.
6. `notification-service` logs cancellation notification.

Failure flow when payment fails:

1. `payment-service` publishes `PaymentFailedEvent`.
2. `order-service` marks order as `CANCELLED`.
3. `order-service` publishes `OrderCancelledEvent`.
4. `inventory-service` releases reserved stock.
5. `notification-service` logs failure/cancellation notification.

## Security

JWT is issued by `auth-service` and validated by `api-gateway`.

JWT claims:

- `sub`: user id
- `email`: user email
- `roles`: list of roles
- `iat`: issued at
- `exp`: expiration time

Authorization rules:

- Public:
  - register
  - login
  - health endpoints
  - product listing and product detail
- `USER`:
  - manage own profile
  - manage own cart
  - checkout
  - view own orders
- `ADMIN`:
  - manage products
  - manage categories
  - manage inventory
  - manage orders

Internal services should trust identity headers only when requests come through the gateway in local Docker networking. The MVP will document this boundary and keep direct service ports exposed only as needed for development.

## Database Ownership

Each service owns a separate PostgreSQL database or schema:

- `auth_db`
- `user_db`
- `product_db`
- `inventory_db`
- `cart_db` if relational persistence is later needed
- `order_db`
- `payment_db`
- `notification_db`

For the MVP, `cart-service` stores carts in Redis and does not need relational tables unless later requirements demand persistent cart history.

No service imports or shares JPA entities from another service. Cross-service references use IDs, DTOs, REST APIs, or event payloads.

## Package Structure

Each application service uses this package shape:

```text
controller
service
repository
entity
dto
config
exception
event
```

Controllers only handle HTTP concerns:

- request mapping
- authentication principal extraction
- validation entrypoint
- response status mapping

Business logic belongs in service classes.

## API and Error Handling

Each service will include:

- Request DTO validation using Jakarta Validation.
- Global exception handling with consistent JSON error responses.
- OpenAPI/Swagger documentation.
- Spring Boot Actuator health endpoints.

Standard error response:

```json
{
  "timestamp": "2026-05-10T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/products",
  "details": [
    {
      "field": "name",
      "message": "must not be blank"
    }
  ]
}
```

## Testing Strategy

Use TDD where practical. For each service, create tests before or alongside implementation.

Minimum test coverage by service:

- Service layer unit tests for business rules.
- Controller tests for important API behavior.
- Repository tests where query behavior matters.
- Integration tests for the most important checkout flows after services are connected.

Important flow tests:

- Register and login returns JWT.
- Admin can create product and category.
- User can add product to cart.
- Checkout creates order.
- Stock reservation succeeds.
- Stock reservation fails when insufficient.
- Payment success completes order.
- Payment failure cancels order and releases stock.

## Implementation Order

1. Initialize parent Maven project.
2. Create `eureka-server`.
3. Create `api-gateway`.
4. Create `auth-service` with JWT.
5. Create `product-service`.
6. Create `inventory-service`.
7. Create `cart-service`.
8. Create `order-service`.
9. Create `payment-service`.
10. Create `notification-service`.
11. Add Kafka event flow.
12. Add Redis-backed cart/cache behavior.
13. Add Docker Compose for full local system.
14. Write README with local run instructions.
15. Add and harden tests for critical services and flows.

Each implementation step must include:

- Files changed.
- Why the files changed.
- How to run.
- How to test.
- Test results.

## Deferred Work

Do not implement these in the first MVP:

- Kubernetes.
- Spring Cloud Config.
- Distributed tracing.
- Real payment gateway integration.
- Real email provider integration.
- Advanced saga orchestration framework.
- Multi-node Eureka clustering.

These can be added after the Docker Compose MVP is stable.

## Acceptance Criteria

The MVP is acceptable when:

- All services build with Maven.
- All services start through Docker Compose.
- Eureka shows registered services.
- Gateway routes requests to backend services.
- User can register and login.
- JWT-protected routes work through the gateway.
- Admin can manage products, categories, and inventory.
- User can add items to cart.
- User can checkout.
- Order creation publishes event.
- Inventory reserves or rejects stock.
- Payment simulation publishes success or failure.
- Order status updates correctly.
- Notification service logs payment/order result.
- README explains local setup, commands, and test commands.
- Critical unit/controller tests pass.
