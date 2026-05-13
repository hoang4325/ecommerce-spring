# Order Service Design

Date: 2026-05-13
Status: Approved design, pending implementation plan

## Purpose

Build the MVP `order-service` as the next independently runnable Spring Boot microservice. The service owns order data, creates orders from a user's cart snapshot, reserves stock through the existing inventory reservation API, and exposes order history/admin order APIs through the gateway.

Kafka, payment processing, notification delivery, and full saga orchestration remain deferred until the order persistence and REST checkout path are stable and tested.

## Current Project Context

The repository currently has:

- `eureka-server` for local service discovery.
- `api-gateway` with an existing `/api/orders/**` route declaration.
- `auth-service` issuing JWTs and the gateway forwarding `X-User-Id`, `X-User-Email`, and `X-User-Roles`.
- `product-service` owning product/category data.
- `inventory-service` owning stock and reservation data, with REST endpoints for reserve/release/deduct.
- `cart-service` owning database-backed active carts and returning cart item snapshots.
- Docker Compose and CI covering all implemented services through `cart-service`.

`order-service` must follow the established service style:

- Java 21, Spring Boot 3, Maven.
- Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation.
- PostgreSQL database owned by the service.
- Eureka client registration.
- OpenAPI/Swagger endpoint.
- Spring Boot Actuator health endpoints.
- Global exception handling with consistent JSON error responses.
- Dockerfile, Docker Compose entry, README updates, and CI build/test coverage.
- Focused tests for domain, repository, service, controller, client, config, and app startup behavior.

## Scope

The `order-service` MVP supports:

- Checkout from the current user's active cart.
- Persist an order and order item snapshots.
- Recompute order totals from cart items instead of trusting a cross-service subtotal blindly.
- Reserve stock through `inventory-service` after the order is persisted.
- Mark orders as `STOCK_RESERVED` when inventory reservation succeeds.
- Mark orders as `CANCELLED` when stock reservation fails or inventory is unavailable during checkout.
- List the current user's orders.
- Fetch one current-user order by id.
- Let admins list all orders.
- Let admins fetch one order by id.
- Let admins cancel a non-terminal order, releasing stock when the order had reserved stock.

The MVP does not support:

- Payment processing.
- Kafka publishing/consumption.
- Notification publishing.
- Clearing the cart after checkout.
- Shipping addresses, tax, shipping fees, discounts, coupons, or multi-currency pricing.
- Distributed locks or a saga framework.
- Real service-to-service authentication beyond the local trusted-header pattern already used in the repo.

## Ownership And Boundaries

`order-service` owns only order state and order item snapshots. It does not own product catalog data, cart state, stock quantity, payment records, or notification records.

The service must not share JPA entities with other services. External communication uses REST DTOs for the MVP and event payloads in later Kafka tasks.

Cross-service references are stored as ids:

- `userId` from the gateway identity header.
- `sourceCartId` from `cart-service`.
- `productId` from cart item snapshots.

`order-service` stores product name and unit price snapshots from the cart response so historical orders remain readable even if catalog data changes later.

## Data Model

### Order

Fields:

- `id`: generated primary key.
- `userId`: authenticated user id from `X-User-Id`.
- `sourceCartId`: cart id used for checkout.
- `status`: current order status.
- `subtotal`: sum of item line totals.
- `cancellationReason`: optional reason when status is `CANCELLED`.
- `createdAt`: creation timestamp.
- `updatedAt`: last update timestamp.

Constraints and rules:

- `userId` is required.
- `sourceCartId` is required for checkout-created orders.
- `subtotal` must be zero or positive.
- Terminal statuses are `COMPLETED` and `CANCELLED`.
- A terminal order cannot be changed by the normal checkout/admin cancellation flow.
- If an existing non-terminal order exists for the same `userId` and `sourceCartId`, checkout returns that order instead of creating a duplicate.

### OrderItem

Fields:

- `id`: generated primary key.
- `orderId`: parent order reference.
- `productId`: product id from cart-service snapshot.
- `productName`: product name snapshot.
- `unitPrice`: unit price snapshot.
- `quantity`: positive integer.
- `lineTotal`: `unitPrice * quantity`.
- `createdAt`: creation timestamp.
- `updatedAt`: last update timestamp.

Constraints and rules:

- A single order may contain each product at most once.
- `quantity` must be positive.
- `unitPrice` must be zero or positive.
- `lineTotal` is computed by `order-service`, not trusted from the client.

### OrderStatus

Values:

- `PENDING`: order row has been created and inventory reservation has not completed.
- `STOCK_RESERVED`: inventory has reserved stock for all order items.
- `PAYMENT_PENDING`: reserved for the future payment-service flow.
- `COMPLETED`: reserved for the future payment success flow.
- `CANCELLED`: checkout failed, admin cancelled the order, stock reservation failed, payment failed, or a later cancellation event was processed.

The first implementation reaches only `PENDING`, `STOCK_RESERVED`, and `CANCELLED`.

## API Design

All user endpoints are under `/api/orders` and require authentication. Admin endpoints are under `/api/admin/orders` and require role `ADMIN`.

### Checkout

`POST /api/orders/checkout`

Request body:

```json
{}
```

Behavior:

1. Read `X-User-Id` from the gateway identity headers.
2. Call `cart-service` `GET /api/cart` using the same user id.
3. Reject checkout with `409 Conflict` if the cart has no persisted `cartId` or has no items.
4. If a non-terminal order already exists for the same `userId` and `sourceCartId`, return that order.
5. Persist a new order as `PENDING` with item snapshots from the cart.
6. Call `inventory-service` `POST /api/inventory/reservations` with the new order id and item quantities.
7. If inventory returns `RESERVED`, update the order to `STOCK_RESERVED` and return `201 Created`.
8. If inventory returns `FAILED`, update the order to `CANCELLED`, store a cancellation reason, and return `409 Conflict`.
9. If inventory is unavailable, update the order to `CANCELLED`, store a cancellation reason, and return `503 Service Unavailable`.

Successful response shape:

```json
{
  "orderId": 1000,
  "userId": 10,
  "sourceCartId": 20,
  "status": "STOCK_RESERVED",
  "items": [
    {
      "productId": 100,
      "productName": "Pour Over Kit",
      "unitPrice": 49.99,
      "quantity": 2,
      "lineTotal": 99.98
    }
  ],
  "subtotal": 99.98,
  "cancellationReason": null,
  "createdAt": "2026-05-13T00:00:00Z",
  "updatedAt": "2026-05-13T00:00:01Z"
}
```

### Current User Order History

`GET /api/orders`

Behavior:

- Returns the current user's orders.
- Supports Spring `Pageable`.
- Orders are sorted newest first by default.
- Users can only see orders with their own `userId`.

### Current User Order Detail

`GET /api/orders/{id}`

Behavior:

- Returns the order if it belongs to the current user.
- Returns `404 Not Found` if the order does not exist or belongs to another user.

### Admin Order List

`GET /api/admin/orders`

Behavior:

- Requires `ADMIN`.
- Supports Spring `Pageable`.
- Optional `status` filter.
- Orders are sorted newest first by default.

### Admin Order Detail

`GET /api/admin/orders/{id}`

Behavior:

- Requires `ADMIN`.
- Returns any order by id.
- Returns `404 Not Found` if missing.

### Admin Cancel Order

`PATCH /api/admin/orders/{id}/status`

Request:

```json
{
  "status": "CANCELLED",
  "reason": "Customer requested cancellation"
}
```

Behavior:

- Requires `ADMIN`.
- The first implementation accepts only `CANCELLED` as a manual admin status change.
- Rejects cancellation of terminal orders with `409 Conflict`.
- If the order status is `STOCK_RESERVED` or `PAYMENT_PENDING`, calls `inventory-service` `POST /api/inventory/reservations/{orderId}/release`.
- Marks the order `CANCELLED` and stores the reason when release succeeds or when no stock was reserved.
- Returns `503 Service Unavailable` if the release call fails, leaving the order unchanged.

## Cart-Service Integration

`order-service` calls `cart-service` synchronously during checkout.

Adapter shape:

- `CartClient` interface owned by order-service.
- `RestClientCartClient` HTTP implementation.
- `CartSnapshot` and `CartItemSnapshot` DTOs owned by order-service.
- Endpoint: `GET /api/cart`.

Headers sent to cart-service:

- `X-User-Id`: current user's id.
- `X-User-Roles`: current user's roles when available, otherwise `USER`.

Mapping rules:

- Empty cart response maps to `EmptyCartException`.
- Missing identity maps to `Unauthorized`.
- `5xx` or network failure maps to `CartServiceUnavailableException`.

`order-service` uses cart item snapshots for order creation and recomputes totals locally.

## Inventory-Service Integration

`order-service` calls `inventory-service` synchronously for stock reservation and admin cancellation release.

Adapter shape:

- `InventoryReservationClient` interface owned by order-service.
- `RestClientInventoryReservationClient` HTTP implementation.
- `InventoryReservationRequest`, `InventoryReservationItem`, and `InventoryReservationResult` DTOs owned by order-service.

Endpoints:

- Reserve: `POST /api/inventory/reservations`.
- Release: `POST /api/inventory/reservations/{orderId}/release`.

`inventory-service` currently protects all `/api/inventory/**` endpoints with role `ADMIN`. The order-service implementation should make a small support change so reservation/release/deduct endpoints accept a service role while stock management endpoints remain `ADMIN` only:

- `/api/inventory/reservations/**`: `ADMIN` or `SERVICE`.
- `/api/inventory/**`: `ADMIN`.

`order-service` sends `X-User-Roles: SERVICE` for inventory reservation calls. This matches the current local trusted-header boundary. A future production hardening task should replace this with explicit service-to-service authentication and private networking.

Mapping rules:

- `RESERVED` means checkout succeeds and order becomes `STOCK_RESERVED`.
- `FAILED` means checkout fails and order becomes `CANCELLED`.
- `5xx` or network failure means checkout fails with `503` and order becomes `CANCELLED`.
- Release failure during admin cancellation returns `503` and leaves the order unchanged.

## Security

The gateway validates JWTs and forwards identity headers. `order-service` follows the same pattern as `cart-service`, `product-service`, and `inventory-service`.

Required header:

- `X-User-Id`

Optional headers:

- `X-User-Email`
- `X-User-Roles`

Rules:

- `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui.html`, and `/swagger-ui/**` are public.
- `/api/orders/**` requires an authenticated identity.
- `/api/admin/orders/**` requires role `ADMIN`.
- User endpoints must scope reads and checkout to the current `X-User-Id`.
- Admin endpoints must not use client-supplied user ids for authorization.

## Error Handling

Use the same response style as existing services:

- `400 Bad Request`: validation failure, malformed request body, or unsupported manual status value.
- `401 Unauthorized`: missing or invalid identity header.
- `403 Forbidden`: authenticated but missing required role.
- `404 Not Found`: order not found.
- `409 Conflict`: empty cart, duplicate invalid checkout state, stock reservation failure, invalid status transition, or terminal order modification.
- `415 Unsupported Media Type`: unsupported content type.
- `500 Internal Server Error`: unexpected error.
- `503 Service Unavailable`: cart-service or inventory-service unavailable.

Error response shape:

```json
{
  "timestamp": "2026-05-13T00:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Cart is empty",
  "path": "/api/orders/checkout",
  "details": []
}
```

## Docker Compose And Runtime

Add:

- `order-service` module to root `pom.xml`.
- `order-postgres` with database `order_db`.
- `order-service` container on port `8085`.
- Eureka registration config.
- `CART_SERVICE_BASE_URL=http://cart-service`.
- `INVENTORY_SERVICE_BASE_URL=http://inventory-service`.
- Gateway dependency on `order-service` health.
- Dockerfiles for existing services must copy `order-service/pom.xml` so multi-module Docker builds remain valid.
- CI must build and test `order-service`.

Expected local ports:

- PostgreSQL: `127.0.0.1:5436 -> order-postgres:5432`.
- Service: `8085`.
- Gateway route: `8080/api/orders/**`.

## Testing Strategy

Use TDD for implementation tasks.

Domain tests:

- Creates order from cart item snapshots.
- Computes line totals and subtotal.
- Rejects empty item lists.
- Rejects non-positive quantity.
- Rejects negative price.
- Rejects terminal order cancellation.
- Allows cancellation of non-terminal orders with a reason.

Service-layer tests:

- Checkout rejects an empty cart.
- Checkout returns an existing non-terminal order for the same cart.
- Checkout creates `PENDING`, reserves stock, and updates to `STOCK_RESERVED`.
- Checkout marks order `CANCELLED` when reservation returns `FAILED`.
- Checkout marks order `CANCELLED` and returns unavailable when inventory call fails.
- User history returns only current-user orders.
- User detail hides another user's order as `404`.
- Admin list can filter by status.
- Admin cancel releases stock for `STOCK_RESERVED`.
- Admin cancel leaves order unchanged if release fails.

Client tests:

- Cart client sends `X-User-Id` and maps the cart response.
- Cart client maps `5xx` and network errors to cart unavailable.
- Inventory client sends service role and maps `RESERVED`.
- Inventory client maps `FAILED`.
- Inventory client maps `5xx` and network errors to inventory unavailable.

Controller tests:

- User endpoints require identity.
- Admin endpoints require `ADMIN`.
- Checkout success returns `201`.
- Empty cart maps to `409`.
- Inventory unavailable maps to `503`.
- Validation errors return structured `400`.
- Order detail enforces user ownership.

Repository tests:

- Persists order with items.
- Finds order by id and user id.
- Finds existing non-terminal order by user id and source cart id.
- Filters admin order list by status.
- Orders list newest first by default.

Config tests:

- Security permits health and OpenAPI.
- Security protects `/api/orders/**`.
- Security restricts `/api/admin/orders/**`.
- OpenAPI endpoint is available.

Runtime verification:

- `mvn -pl order-service test`.
- `mvn -B -ntp clean test`.
- `docker compose config --quiet`.
- `docker compose build eureka-server api-gateway auth-service product-service inventory-service cart-service order-service`.

## Kafka And Future Event Flow

Kafka is intentionally deferred for this branch. The first branch proves order persistence, checkout, REST reservation, and admin reads/cancel behavior.

Later event-flow work will add:

- `OrderCreatedEvent` published by order-service.
- `StockReservedEvent` and `StockReservationFailedEvent` consumed by order-service.
- `PaymentSucceededEvent` and `PaymentFailedEvent` consumed by order-service.
- `OrderCompletedEvent` and `OrderCancelledEvent` published by order-service.
- Inventory reservation moving from synchronous REST to Kafka consumer behavior.
- Cart clearing at the correct lifecycle point.
- Notification-service consumers.

## Rollout

Implementation happens on the `order-service` branch/worktree.

Recommended milestone order:

1. Add module skeleton and baseline app.
2. Add order domain entities and repositories.
3. Add DTOs, exceptions, and security/OpenAPI config.
4. Add cart client abstraction and tests.
5. Add inventory reservation client abstraction and tests.
6. Add checkout service behavior with TDD.
7. Add user and admin controllers with TDD.
8. Add the small inventory-service security support for service-role reservation endpoints.
9. Add Dockerfile, Compose, README, and CI updates.
10. Run module tests, full Maven tests, Docker Compose config validation, and Docker build.

Each milestone produces small, reviewable commits.

## Acceptance Criteria

The branch is acceptable when:

- `order-service` builds as part of the Maven reactor.
- `order-service` starts locally and registers with Eureka.
- `api-gateway` routes `/api/orders/**` to `order-service`.
- Authenticated users can checkout a non-empty cart.
- Checkout stores order and order item snapshots in `order_db`.
- Checkout reserves stock through `inventory-service`.
- Failed stock reservation cancels the order and returns a clear error.
- Users can list and view only their own orders.
- Admins can list and view all orders.
- Admins can cancel supported non-terminal orders.
- OpenAPI and health endpoints work.
- Docker Compose config is valid.
- Docker build succeeds for all implemented services.
- Critical unit, controller, repository, client, and config tests pass.
