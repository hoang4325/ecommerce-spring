# Notification Service Design

Date: 2026-05-14
Status: Approved direction, pending written spec review

## Purpose

Build the MVP `notification-service` as the next independently runnable Spring Boot microservice. The service owns notification log data, simulates email delivery by writing durable log records, exposes admin read APIs, and prepares clean DTO boundaries for the later Kafka-based order/payment notification flow.

This branch should not wire Kafka consumers yet. The first branch proves notification persistence, deterministic simulated delivery, admin visibility, internal creation API, service registration, and local Docker Compose runtime. Kafka topics and event consumers come in a later dedicated event-flow branch.

## Current Project Context

The branch is based on `payment-service`, which currently includes:

- `eureka-server` for local service discovery.
- `api-gateway` forwarding JWT identity headers to downstream services.
- `auth-service` for registration, login, JWT generation, and roles.
- `product-service` for product/category data.
- `inventory-service` for stock and reservation data.
- `cart-service` for active carts.
- `order-service` for checkout, order persistence, inventory reservation, user order APIs, and admin order APIs.
- `payment-service` for simulated payment persistence, user payment APIs, admin status updates, and Docker Compose runtime wiring.
- Docker Compose and CI covering services through `payment-service`.

`notification-service` must follow the existing service style:

- Java 21, Spring Boot 3, Maven.
- Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation.
- PostgreSQL database owned by the service.
- Eureka client registration.
- OpenAPI/Swagger endpoint.
- Spring Boot Actuator health endpoints.
- Global exception handling with consistent JSON error responses.
- Dockerfile, Docker Compose entry, README updates, and CI build/test coverage.
- Focused tests for domain, repository, service, controller, config, and app startup behavior.

## Approved Direction

Use the standalone notification service approach first.

The service will expose a protected internal REST endpoint that creates notification logs from an event-like request. This gives the local MVP a real, testable notification boundary before Kafka exists. Later, Kafka consumers can call the same service-layer method that the internal REST endpoint uses.

Rejected alternatives for this branch:

- Kafka-first notification consumption: closer to the final architecture, but it forces topic contracts and producer changes before order/payment/inventory event flow is ready end-to-end.
- Pure log-only module without an internal API: simpler, but not useful to exercise notification creation locally.

## Scope

The `notification-service` MVP supports:

- Persist notification logs in `notification_db`.
- Simulate email delivery by creating a notification record with `SENT` or `FAILED` status.
- Create notification logs through an internal REST endpoint for local testing and future service-to-service integration.
- Let admins list notification logs with optional filters.
- Let admins fetch one notification log by id.
- Register with Eureka and route admin notification APIs through the gateway.
- Run locally through Docker Compose.
- Document local curl examples in `README.md`.

The MVP does not support:

- Kafka consumers or producers.
- Real SMTP, SES, SendGrid, Mailgun, SMS, push notifications, or webhooks.
- Email templates, localization, attachments, retries, delivery scheduling, or outbox processing.
- User-facing notification preference management.
- User-facing inbox APIs.
- Calling `user-service`, `order-service`, or `payment-service`.
- Distributed transactions or saga orchestration.
- Kubernetes or Spring Cloud Config.

## Ownership And Boundaries

`notification-service` owns only notification log state. It does not own orders, payments, users, inventory, carts, or products.

The service must not share JPA entities with other services. External communication uses REST DTOs for this branch and event payloads in later Kafka tasks.

Cross-service references are stored as ids:

- `userId`: the user associated with the notification.
- `orderId`: the order associated with the notification, when applicable.
- `paymentId`: the payment associated with the notification, when applicable.

For this MVP, `recipient` is submitted by the internal caller. Later integration can derive it from user profile data or order snapshots.

## Data Model

### Notification

Fields:

- `id`: generated primary key.
- `userId`: optional user id associated with the notification.
- `orderId`: optional order id associated with the notification.
- `paymentId`: optional payment id associated with the notification.
- `type`: notification type.
- `channel`: delivery channel.
- `recipient`: email address or recipient identifier.
- `status`: delivery status.
- `subject`: short notification subject.
- `message`: notification message body.
- `failureReason`: reason for simulated delivery failure.
- `createdAt`: creation timestamp.

Notification type values:

- `ORDER_COMPLETED`
- `ORDER_CANCELLED`
- `PAYMENT_SUCCEEDED`
- `PAYMENT_FAILED`

Channel values:

- `EMAIL`

Status values:

- `SENT`
- `FAILED`

Database table:

```text
notifications
  id
  user_id
  order_id
  payment_id
  type
  channel
  recipient
  status
  subject
  message
  failure_reason
  created_at
```

Indexes:

- `idx_notifications_user_id` on `user_id`.
- `idx_notifications_order_id` on `order_id`.
- `idx_notifications_payment_id` on `payment_id`.
- `idx_notifications_type` on `type`.
- `idx_notifications_status` on `status`.
- `idx_notifications_created_at` on `created_at`.

## API Design

### Internal Create Notification

`POST /api/internal/notifications`

Security:

- Requires header `X-Internal-Token`.
- The expected value is configured with `notification.internal-token`.
- Docker Compose sets the local token through `NOTIFICATION_INTERNAL_TOKEN`.

Request:

```json
{
  "userId": 10,
  "orderId": 1000,
  "paymentId": 5000,
  "type": "PAYMENT_SUCCEEDED",
  "recipient": "customer@example.com",
  "subject": "Payment received",
  "message": "Your payment for order #1000 was successful.",
  "simulateFailure": false
}
```

Validation:

- `type` is required.
- `recipient` is required and must be a valid email for the `EMAIL` channel.
- `subject` is required, not blank, and at most 200 characters.
- `message` is required, not blank, and at most 2000 characters.
- `simulateFailure` defaults to `false` when omitted.

Behavior:

- Creates an `EMAIL` notification.
- If `simulateFailure` is `false`, store `status = SENT` and `failureReason = null`.
- If `simulateFailure` is `true`, store `status = FAILED` and `failureReason = "Simulated notification failure"`.
- Returns `201 Created` with the created notification.

Response:

```json
{
  "notificationId": 9000,
  "userId": 10,
  "orderId": 1000,
  "paymentId": 5000,
  "type": "PAYMENT_SUCCEEDED",
  "channel": "EMAIL",
  "recipient": "customer@example.com",
  "status": "SENT",
  "subject": "Payment received",
  "message": "Your payment for order #1000 was successful.",
  "failureReason": null,
  "createdAt": "2026-05-14T00:00:00Z"
}
```

### Admin List Notifications

`GET /api/admin/notifications`

Security:

- Requires authenticated gateway identity with role `ADMIN`.

Query parameters:

- `type`: optional `NotificationType`.
- `status`: optional `NotificationStatus`.
- `userId`: optional `Long`.
- `orderId`: optional `Long`.
- `paymentId`: optional `Long`.
- Spring `Pageable`.

Behavior:

- Returns notification logs sorted newest first by default.
- Applies all provided filters as an AND query.
- Non-admin users receive `403 Forbidden`.
- Missing identity receives `401 Unauthorized`.

### Admin Get Notification

`GET /api/admin/notifications/{id}`

Security:

- Requires authenticated gateway identity with role `ADMIN`.

Behavior:

- Returns one notification by id.
- Missing notification returns `404 Not Found`.

## Security Design

`notification-service` trusts the API Gateway identity headers for admin APIs, matching existing downstream service style:

- `X-User-Id`
- `X-User-Email`
- `X-User-Roles`

Authorization rules:

- `/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui.html`, and `/swagger-ui/**` are public.
- `/api/admin/notifications/**` and `/api/admin/notifications` require role `ADMIN`.
- `/api/internal/notifications` requires a valid `X-Internal-Token`.
- Other paths require authentication by default.

The internal token is intentionally a local MVP mechanism. It is not a long-term replacement for Kafka, service mesh identity, or signed internal service credentials.

## Error Handling

Use a `GlobalExceptionHandler` with the same response shape used by recent services:

```json
{
  "timestamp": "2026-05-14T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/internal/notifications",
  "details": [
    {
      "field": "recipient",
      "message": "must be a well-formed email address"
    }
  ]
}
```

Mappings:

- Request validation -> `400 Bad Request`.
- Malformed or missing body -> `400 Bad Request`.
- Missing or invalid internal token -> `401 Unauthorized`.
- Missing gateway identity -> `401 Unauthorized`.
- Access denied -> `403 Forbidden`.
- Notification not found -> `404 Not Found`.
- Unsupported method -> `405 Method Not Allowed`.
- Unsupported content type -> `415 Unsupported Media Type`.
- Unexpected error -> `500 Internal Server Error`.

## Runtime Design

Service:

- Maven module: `notification-service`.
- Spring application name: `notification-service`.
- HTTP port: `8087`.
- Local database: `notification_db`.
- Local database port: `5438`.
- Swagger UI: `http://localhost:8087/swagger-ui.html`.
- Health endpoint: `http://localhost:8087/actuator/health`.

Docker Compose additions:

- `notification-postgres` on `127.0.0.1:5438:5432`.
- `notification-service` built from `notification-service/Dockerfile`.
- `api-gateway` depends on healthy `notification-service`.
- Volume `notification-postgres-data`.

Gateway additions:

- Add `notification-service-admin` route:
  - `Path=/api/admin/notifications/**`
  - `uri=lb://notification-service`
- Existing `/api/notifications/**` route may remain for compatibility, but this branch does not add user-facing endpoints under that path.
- Internal create endpoint is available on the service port for local testing. A gateway route for `/api/internal/notifications/**` is optional and should only be added if tests and docs prove the internal token protection works through the gateway.

Existing service Dockerfiles must copy `notification-service/pom.xml` so multi-module Docker builds remain valid after adding the module.

CI must build and test `notification-service`, and the sequential Docker image build must include `notification-service`.

## Package Structure

Use:

```text
notification-service/src/main/java/com/example/ecommerce/notificationservice
  config
  controller
  dto
  entity
  exception
  repository
  service
```

The `event` package is intentionally deferred until Kafka event-flow work starts. The internal create DTO will mirror the future event shape enough that Kafka consumers can map event payloads into the same service-layer command later.

## Testing Requirements

Tests must cover:

- Application context starts with H2 and Eureka disabled.
- Notification domain creation:
  - sent notification.
  - failed notification.
  - required type.
  - required recipient.
  - required subject/message.
- Repository:
  - save and fetch by id.
  - filter by type.
  - filter by status.
  - filter by user id.
  - filter by order id.
  - filter by payment id.
  - newest-first sorting.
- Service:
  - internal create maps `simulateFailure = false` to `SENT`.
  - internal create maps `simulateFailure = true` to `FAILED`.
  - admin filter delegates criteria correctly.
  - missing id throws not-found exception.
- Controller:
  - internal create returns `201 Created`.
  - missing internal token returns `401 Unauthorized`.
  - invalid internal token returns `401 Unauthorized`.
  - validation errors return `400 Bad Request`.
  - admin list supports filters and default newest-first pageable sort.
  - admin detail returns a notification.
  - user role receives `403 Forbidden` for admin endpoints.
- OpenAPI and health endpoints are reachable.
- Docker Compose config validates.
- Docker image for `notification-service` builds.

## Future Kafka Event Flow

Kafka is intentionally deferred for this branch. Later work will add:

- Topic contracts for:
  - `PaymentSucceededEvent`
  - `PaymentFailedEvent`
  - `OrderCompletedEvent`
  - `OrderCancelledEvent`
- Kafka consumers in `notification-service`.
- Mapping from event payloads to the same service-layer create method used by `POST /api/internal/notifications`.
- Idempotency strategy for duplicate events, likely using event id or `(eventType, aggregateId)` uniqueness.
- Notification retry/outbox behavior if real delivery is introduced.

## Implementation Constraints

- Keep business logic in service classes, not controllers.
- Do not share JPA entities between services.
- Use DTOs for request/response boundaries.
- Keep the internal token check small and explicit.
- Do not add Kafka dependencies in this branch.
- Do not add Redis, Kubernetes, Spring Cloud Config, or email provider SDKs in this branch.
- Keep Docker Compose local-first.
- Use TDD for each implementation task.
- Implement one focused task at a time and run tests after each task.

## Acceptance Criteria

- `notification-service` builds as part of the Maven reactor.
- `notification-service` starts locally and registers with Eureka.
- `notification-service` exposes health and Swagger endpoints.
- Internal create API creates `SENT` and `FAILED` notification logs.
- Admin APIs can list and fetch notification logs.
- Admin list supports filters and newest-first default sorting.
- API Gateway routes admin notification APIs to `notification-service`.
- Docker Compose starts `notification-postgres` and `notification-service`.
- CI runs tests and builds the notification Docker image.
- README documents local run, Docker Compose, and notification API examples.
- Full reactor tests pass with zero failures and zero errors.
- Docker Compose config validates.
- Sequential Docker builds for all implemented services including `notification-service` pass.
