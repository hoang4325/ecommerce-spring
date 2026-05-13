# Payment Service Design

Date: 2026-05-13
Status: Approved direction, pending written spec review

## Purpose

Build the MVP `payment-service` as the next independently runnable Spring Boot microservice. The service owns payment transaction data, simulates payment processing for an order amount, exposes user/admin payment APIs, and prepares clean boundaries for the later Kafka-based order/payment flow.

This branch should not wire the full saga yet. Kafka publication/consumption and automatic order status updates are deferred until the standalone payment API is stable, tested, and runnable through Docker Compose.

## Current Project Context

The branch is based on `order-service`, which currently includes:

- `eureka-server` for local service discovery.
- `api-gateway` forwarding JWT identity headers to downstream services.
- `auth-service` for registration/login/JWT.
- `product-service` for product/category data.
- `inventory-service` for stock and reservation data.
- `cart-service` for active carts.
- `order-service` for checkout, order persistence, inventory reservation, user order APIs, and admin order APIs.
- Docker Compose and CI covering services through `order-service`.

`payment-service` must follow the existing service style:

- Java 21, Spring Boot 3, Maven.
- Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation.
- PostgreSQL database owned by the service.
- Eureka client registration.
- OpenAPI/Swagger endpoint.
- Spring Boot Actuator health endpoints.
- Global exception handling with consistent JSON error responses.
- Dockerfile, Docker Compose entry, README updates, and CI build/test coverage.
- Focused tests for domain, repository, service, controller, config, and app startup behavior.

## Scope

The `payment-service` MVP supports:

- Create a simulated payment for an order.
- Persist payment transactions in `payment_db`.
- Return `PENDING`, `SUCCESS`, or `FAILED` payment status.
- Make repeated create requests for the same `orderId` idempotent when a payment already exists.
- Let users list and fetch their own payments.
- Let admins list and fetch all payments.
- Let admins mark a `PENDING` payment as `SUCCESS` or `FAILED` for local testing.
- Register with Eureka and route through the gateway.
- Run locally through Docker Compose.

The MVP does not support:

- Real payment providers such as Stripe, PayPal, VNPay, or bank transfer integrations.
- Card numbers, CVV, bank account data, or other sensitive payment instrument storage.
- Refunds, captures, partial payments, multi-currency settlement, invoices, or tax.
- Kafka events.
- Automatic order status updates.
- Calling `order-service` to validate order ownership or amount.
- Distributed transactions or a saga framework.

## Ownership And Boundaries

`payment-service` owns only payment transaction state. It does not own orders, inventory, carts, products, users, or notifications.

The service must not share JPA entities with other services. External communication uses REST DTOs for this branch and event payloads in later Kafka tasks.

Cross-service references are stored as ids:

- `orderId`: the order this payment belongs to.
- `userId`: authenticated user id from `X-User-Id`.

The service stores the amount submitted at payment creation time. For this MVP, the trusted caller is the gateway-authenticated user/admin or a future internal order workflow. Later integration work can make `order-service` the only component allowed to initiate payment for a checked-out order.

## Data Model

### Payment

Fields:

- `id`: generated primary key.
- `orderId`: order id the payment belongs to.
- `userId`: authenticated user id.
- `amount`: amount to charge.
- `status`: current payment status.
- `method`: simulated payment method.
- `failureReason`: optional reason when status is `FAILED`.
- `createdAt`: creation timestamp.
- `updatedAt`: last update timestamp.

Constraints and rules:

- `orderId` is required.
- `userId` is required.
- `amount` must be greater than zero.
- `method` is required.
- One payment transaction may exist per `orderId` in the MVP.
- `SUCCESS` and `FAILED` are terminal statuses.
- A terminal payment cannot be changed.
- Repeated create requests for an order with an existing payment return the existing payment instead of creating a duplicate.

### PaymentStatus

Values:

- `PENDING`: payment has been created but not finalized.
- `SUCCESS`: simulated payment succeeded.
- `FAILED`: simulated payment failed.

### PaymentMethod

Values:

- `COD`: cash on delivery simulation.
- `CARD`: card simulation without storing card details.
- `BANK_TRANSFER`: bank transfer simulation.

## API Design

All user endpoints are under `/api/payments` and require authentication. Admin endpoints are under `/api/admin/payments` and require role `ADMIN`.

### Create Simulated Payment

`POST /api/payments`

Request:

```json
{
  "orderId": 1000,
  "amount": 99.98,
  "method": "CARD",
  "simulateResult": "SUCCESS"
}
```

Behavior:

1. Read `X-User-Id` from gateway identity headers.
2. Validate `orderId`, positive `amount`, and `method`.
3. If a payment already exists for `orderId`, return it unchanged.
4. If a payment exists for `orderId` but belongs to a different user, return `409 Conflict` without exposing the existing payment body.
5. Create a payment as `PENDING`.
6. Apply the simulation result:
   - `SUCCESS` marks the payment `SUCCESS`.
   - `FAILED` marks the payment `FAILED` with a default failure reason.
   - omitted or `PENDING` leaves the payment `PENDING`.
7. Persist and return the payment.

Successful response shape:

```json
{
  "paymentId": 5000,
  "orderId": 1000,
  "userId": 10,
  "amount": 99.98,
  "method": "CARD",
  "status": "SUCCESS",
  "failureReason": null,
  "createdAt": "2026-05-13T00:00:00Z",
  "updatedAt": "2026-05-13T00:00:01Z"
}
```

### Current User Payment History

`GET /api/payments`

Behavior:

- Returns the current user's payments.
- Supports Spring `Pageable`.
- Orders are sorted newest first by default.
- Users can only see payments with their own `userId`.

### Current User Payment Detail

`GET /api/payments/{id}`

Behavior:

- Returns the payment if it belongs to the current user.
- Returns `404 Not Found` if the payment does not exist or belongs to another user.

### Current User Payment By Order

`GET /api/payments/by-order/{orderId}`

Behavior:

- Returns the payment for the current user's order id.
- Returns `404 Not Found` if missing or owned by another user.

### Admin Payment List

`GET /api/admin/payments`

Behavior:

- Requires `ADMIN`.
- Supports Spring `Pageable`.
- Optional `status` filter.
- Orders are sorted newest first by default.

### Admin Payment Detail

`GET /api/admin/payments/{id}`

Behavior:

- Requires `ADMIN`.
- Returns any payment by id.
- Returns `404 Not Found` if missing.

### Admin Complete Or Fail Payment

`PATCH /api/admin/payments/{id}/status`

Request:

```json
{
  "status": "FAILED",
  "failureReason": "Simulated card decline"
}
```

Behavior:

- Requires `ADMIN`.
- Accepts only `SUCCESS` or `FAILED`.
- Rejects updates to terminal payments with `409 Conflict`.
- `FAILED` stores the provided failure reason or a default reason.
- `SUCCESS` clears any failure reason.

## Simulation Rules

The MVP should keep simulation explicit and deterministic:

- `simulateResult=SUCCESS` returns `SUCCESS`.
- `simulateResult=FAILED` returns `FAILED`.
- `simulateResult=PENDING` or omitted returns `PENDING`.

No random payment result should be used. Randomness makes tests and local demos harder to reason about.

## Security

The gateway validates JWTs and forwards identity headers. `payment-service` follows the same trusted-header pattern as `cart-service`, `product-service`, `inventory-service`, and `order-service`.

Required header:

- `X-User-Id`

Optional headers:

- `X-User-Email`
- `X-User-Roles`

Rules:

- `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui.html`, and `/swagger-ui/**` are public.
- `/api/payments/**` requires an authenticated identity.
- `/api/admin/payments/**` requires role `ADMIN`.
- User endpoints must scope reads and creation to the current `X-User-Id`.
- Admin endpoints must not use client-supplied user ids for authorization.

## Error Handling

Use the same response style as existing services:

- `400 Bad Request`: validation failure, malformed request body, unsupported payment method/status, or unsupported simulation result.
- `401 Unauthorized`: missing or invalid identity header.
- `403 Forbidden`: authenticated but missing required role.
- `404 Not Found`: payment not found.
- `409 Conflict`: invalid payment status transition or terminal payment update.
- `415 Unsupported Media Type`: unsupported content type.
- `500 Internal Server Error`: unexpected error.

Error response shape:

```json
{
  "timestamp": "2026-05-13T00:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Terminal payment cannot be changed",
  "path": "/api/admin/payments/5000/status",
  "details": []
}
```

## Docker Compose And Runtime

Add:

- `payment-service` module to root `pom.xml`.
- `payment-postgres` with database `payment_db`.
- `payment-service` container on port `8086`.
- Eureka registration config.
- Gateway route for `/api/payments/**`.
- Gateway route for `/api/admin/payments/**`.
- Gateway dependency on `payment-service` health.
- Dockerfiles for existing service modules must copy `payment-service/pom.xml` so multi-module Docker builds remain valid.
- CI must build and test `payment-service`.

Expected local ports:

- PostgreSQL: `127.0.0.1:5437 -> payment-postgres:5432`.
- Service: `8086`.
- Gateway routes:
  - `8080/api/payments/**`
  - `8080/api/admin/payments/**`

## Testing Strategy

Use TDD for implementation tasks.

Domain tests:

- Creates payment in `PENDING`.
- Applies `SUCCESS` simulation.
- Applies `FAILED` simulation and stores failure reason.
- Rejects non-positive amount.
- Rejects terminal payment status changes.

Repository tests:

- Persists payment.
- Finds payment by id and user id.
- Finds payment by order id.
- Finds payment by order id and user id.
- Filters admin payment list by status.
- Enforces unique payment per order id.

Service-layer tests:

- Create payment returns existing payment for the same order id.
- Create payment rejects an existing payment for the same order id when it belongs to another user.
- Create payment stores current user id from gateway identity.
- Create payment applies explicit `SUCCESS`.
- Create payment applies explicit `FAILED`.
- Current user history returns only current user's payments.
- Current user detail hides another user's payment as `404`.
- Admin list can filter by status.
- Admin status update rejects terminal payments.
- Admin can mark pending payment as `SUCCESS`.
- Admin can mark pending payment as `FAILED`.

Controller tests:

- User endpoints require identity.
- Admin endpoints require `ADMIN`.
- Create payment success returns `201`.
- Validation errors return structured `400`.
- User detail enforces ownership.
- Unsupported content type returns `415`.
- Unsupported method returns `405`.

Config tests:

- Security permits health and OpenAPI.
- Security protects `/api/payments/**`.
- Security restricts `/api/admin/payments/**`.
- OpenAPI endpoint is available.

Runtime verification:

- `mvn -pl payment-service test`.
- `mvn -B -ntp clean test`.
- `docker compose config --quiet`.
- Sequential Docker image builds for all implemented services including `payment-service`.

## Kafka And Future Event Flow

Kafka is intentionally deferred for this branch. The first branch proves payment persistence, deterministic payment simulation, user/admin payment APIs, and runtime wiring.

Later event-flow work will add:

- `PaymentSucceededEvent` published by `payment-service`.
- `PaymentFailedEvent` published by `payment-service`.
- `order-service` consuming payment result events.
- `order-service` moving `STOCK_RESERVED` or `PAYMENT_PENDING` orders to `COMPLETED` on success.
- `order-service` moving orders to `CANCELLED` on payment failure and releasing stock through inventory.
- `notification-service` consuming order/payment events.

## Rollout

Implementation happens on the `payment-service` branch/worktree, based on the pushed `order-service` branch.

Recommended milestone order:

1. Add module skeleton and baseline app.
2. Add payment domain entities and repositories.
3. Add DTOs, exceptions, security, and OpenAPI config.
4. Add payment service behavior with TDD.
5. Add user and admin controllers with TDD.
6. Add gateway routes.
7. Add Dockerfile, Compose, README, and CI updates.
8. Run module tests, full Maven tests, Docker Compose config validation, and Docker build.
9. Review spec compliance and fix Critical/Important issues before pushing.

Each milestone should produce a small, reviewable commit.

## Acceptance Criteria

The branch is acceptable when:

- `payment-service` builds as part of the Maven reactor.
- `payment-service` starts locally and registers with Eureka.
- `api-gateway` routes `/api/payments/**` and `/api/admin/payments/**` to `payment-service`.
- Authenticated users can create simulated payments.
- Payment creation persists transactions in `payment_db`.
- Repeated creation for the same `orderId` is idempotent.
- Users can list and view only their own payments.
- Admins can list and view all payments.
- Admins can complete or fail pending payments.
- Terminal payments cannot be modified.
- OpenAPI and health endpoints work.
- Docker Compose config is valid.
- Docker build succeeds for all implemented services.
- Critical unit, controller, repository, config, and app tests pass.
