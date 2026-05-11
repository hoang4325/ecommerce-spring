# Inventory Service Design

Date: 2026-05-11
Status: Approved by continuation request

## Goal

Add `inventory-service` as the stock ownership boundary for the local Docker Compose MVP. The service manages available and reserved stock per product, supports reservation/release/deduction rules used by checkout, exposes admin REST APIs through the gateway, and stays ready for Kafka event wiring in a later milestone.

## Context

The repository currently has `eureka-server`, `api-gateway`, `auth-service`, and `product-service`. `product-service` owns catalog data only and intentionally does not store stock. `api-gateway` already routes `/api/inventory/**` to `inventory-service`, so the next step is to add the service module, persistence, business logic, API, Docker runtime, and documentation.

## Approach Options

### Option A: REST-only inventory

This is the smallest local MVP. It would expose endpoints to reserve, release, and deduct stock, and later services could call those endpoints synchronously. The downside is that it conflicts with the project direction that important order and payment state changes should move through Kafka events.

### Option B: Kafka-first inventory

This would add Kafka dependencies, topics, consumers, producers, and event integration now. It matches the final event flow, but it requires event contracts and producers from `order-service` and `payment-service` before those services exist. That would add scaffolding that cannot be exercised end-to-end yet.

### Option C: Inventory core plus admin REST, Kafka deferred

This service implements the real domain operations now: stock management, reservation, release, deduction, and reservation records. REST endpoints expose admin stock management and controlled reservation simulation for local testing. Kafka consumers/producers are deferred until the dedicated Kafka event-flow milestone, where they can call the service layer directly.

Chosen approach: Option C. It keeps the code production-oriented without pretending the rest of the checkout flow already exists.

## Service Boundary

`inventory-service` owns stock state. It stores product IDs as external references and never imports or shares product-service JPA entities. Product existence validation is intentionally deferred because product-service already owns catalog data and cross-service validation will be designed when synchronous service clients are introduced.

The service owns its own PostgreSQL database, `inventory_db`, and runs on port `8083`.

## API Contract

All inventory APIs are admin-only in this milestone.

Endpoints:

- `GET /api/inventory/items`
- `GET /api/inventory/items/{productId}`
- `PUT /api/inventory/items/{productId}`
- `POST /api/inventory/items/{productId}/adjust`
- `POST /api/inventory/reservations`
- `POST /api/inventory/reservations/{orderId}/release`
- `POST /api/inventory/reservations/{orderId}/deduct`

Request examples:

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
- `product_id`: required, unique external product reference.
- `available_quantity`: stock that can still be reserved.
- `reserved_quantity`: stock currently reserved for pending orders.
- `created_at`: creation timestamp.
- `updated_at`: update timestamp.

Table: `stock_reservations`

- `id`: generated primary key.
- `order_id`: required external order reference.
- `product_id`: required external product reference.
- `quantity`: required positive quantity.
- `status`: `RESERVED`, `RELEASED`, `DEDUCTED`, or `FAILED`.
- `failure_reason`: optional stable reason for failed reservations.
- `created_at`: creation timestamp.
- `updated_at`: update timestamp.

The reservation model uses one row per order item. Reservation operations are all-or-nothing for successful reservations: if any requested item is missing or insufficient, no stock is moved and failed reservation rows are recorded for the requested items.

## Business Rules

Stock management:

- Setting stock creates an inventory item if the product ID does not exist.
- Setting stock updates only available quantity and leaves reserved quantity unchanged.
- Available and reserved quantities must never be negative.
- Adjusting stock by a delta cannot reduce available quantity below zero.

Reservation:

- A reservation request must include an order ID and at least one item.
- Item quantities must be positive.
- Duplicate product IDs in one reservation request are rejected.
- An order that already has `RESERVED` or `DEDUCTED` reservations cannot be reserved again.
- Successful reservation decreases `available_quantity`, increases `reserved_quantity`, and creates `RESERVED` rows.
- Failed reservation creates `FAILED` rows and does not move stock.

Release:

- Releasing an order changes `RESERVED` rows to `RELEASED`.
- Release adds the quantity back to `available_quantity` and subtracts it from `reserved_quantity`.
- Releasing an already released or failed reservation is idempotent.
- Releasing a deducted reservation is rejected.

Deduct:

- Deducting an order changes `RESERVED` rows to `DEDUCTED`.
- Deduct subtracts from `reserved_quantity`; available stock was already reduced during reservation.
- Deducting an already deducted reservation is idempotent.
- Deducting a released or failed reservation is rejected.

## Security

`api-gateway` validates JWT and forwards trusted identity headers. `inventory-service` reads `X-User-Roles` and maps roles to Spring Security authorities.

Public endpoints:

- `/actuator/health`
- `/actuator/info`
- `/v3/api-docs/**`
- `/swagger-ui.html`
- `/swagger-ui/**`

Admin endpoints:

- All `/api/inventory/**` endpoints require `ROLE_ADMIN`.

Direct service ports are for local development only. The MVP trusts identity headers only inside the local Docker/gateway boundary.

## Error Handling

The service uses the same JSON error shape already used by auth and product services:

```json
{
  "timestamp": "2026-05-11T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/inventory/items/10",
  "details": [
    {
      "field": "availableQuantity",
      "message": "must be greater than or equal to 0"
    }
  ]
}
```

Validation errors return 400 with sorted field details. Missing stock returns 404. Duplicate reservations and invalid stock transitions return 409. Unexpected errors return a stable non-leaking 500 response and are logged.

## Kafka Readiness

This milestone does not add Kafka dependencies or topics. It prepares the service layer methods that later Kafka consumers will call:

- `reserve(request)` for `OrderCreatedEvent`.
- `release(orderId)` for `OrderCancelledEvent` or `PaymentFailedEvent`.
- `deduct(orderId)` for `PaymentSucceededEvent`.

Kafka event contracts and actual publication of `StockReservedEvent` and `StockReservationFailedEvent` remain deferred to the dedicated Kafka event-flow milestone after order/payment services exist.

## Testing

Tests should follow the existing service pattern:

- Context tests for module startup.
- Repository tests for uniqueness, long-lived stock state, and reservation lookup.
- Unit tests for stock and reservation services.
- Controller tests for validation, status codes, and delegation.
- Full security tests proving `/api/inventory/**` is admin-only.

Important cases:

- Set and adjust stock.
- Reject negative stock.
- Reserve stock successfully.
- Fail reservation with insufficient stock without moving quantities.
- Reject duplicate product IDs in a reservation request.
- Release reserved stock.
- Deduct reserved stock.
- Reject invalid transitions.

## Docker Compose

Add `inventory-postgres` on `127.0.0.1:5434` with database `inventory_db`. Add `inventory-service` on `8083`, depending on healthy `inventory-postgres` and `eureka-server`. Update the existing service Dockerfiles to copy `inventory-service/pom.xml` for Maven reactor compatibility.

## Acceptance Criteria

- `inventory-service` builds as a Maven module.
- Tests pass with H2 and no external services.
- Swagger is available at `http://localhost:8083/swagger-ui.html`.
- Docker Compose config is valid.
- Docker build passes for eureka, gateway, auth, product, and inventory services.
- Gateway can route `/api/inventory/**` once the service is running.
- README explains build, test, local run, Docker Compose, and admin curl examples.
