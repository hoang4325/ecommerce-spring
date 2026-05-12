# Cart Service Design

## Purpose

Build the MVP `cart-service` as the next independently runnable Spring Boot microservice. The service lets an authenticated user manage their own active shopping cart through REST APIs routed by `api-gateway`.

The first implementation uses PostgreSQL as the system of record. Redis is intentionally deferred to a later task after the database-backed cart is stable and tested.

## Current Project Context

The repository currently has:

- `eureka-server` for service discovery.
- `api-gateway` with a route already declared for `/api/cart/**`.
- `auth-service` issuing JWTs with `sub`, `email`, and `roles`.
- `product-service` owning product and category data.
- `inventory-service` owning stock and reservation data.
- Docker Compose and CI covering the existing services.

`cart-service` must follow the existing service style:

- Java 21, Spring Boot 3, Maven.
- Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation.
- Eureka client registration.
- OpenAPI/Swagger endpoint.
- Health endpoints.
- Global exception handler with clear error responses.
- Dockerfile and Docker Compose entry.
- Focused unit, controller, repository, config, and application tests.

## Scope

The MVP supports:

- Get the current user's active cart.
- Add a product to the cart.
- Update an item's quantity.
- Remove one item.
- Clear the active cart.
- Calculate subtotal from stored item snapshots.

The MVP does not support:

- Checkout.
- Inventory reservation.
- Payment.
- Kafka events.
- Redis cache/session storage.
- Guest carts.
- Cart merging across anonymous and authenticated sessions.
- Multi-currency pricing.
- Promotions, coupons, tax, shipping, or discounts.

Checkout will be implemented later in `order-service`. `cart-service` only prepares a stable cart representation for that future flow.

## Ownership And Boundaries

`cart-service` owns only cart data. It does not own product catalog data, inventory data, orders, or payments.

The service must not share JPA entities with any other service. All external communication uses REST DTOs or gateway identity headers.

`product-service` remains the source of truth for product details. `cart-service` stores item snapshots so cart reads do not depend on product-service availability after an item has been added.

## Data Model

### Cart

Fields:

- `id`: generated primary key.
- `userId`: authenticated user id from `X-User-Id`.
- `status`: `ACTIVE` or `CHECKED_OUT`.
- `createdAt`: creation timestamp.
- `updatedAt`: last update timestamp.

Constraints:

- Only one active cart must exist per user.
- A checked-out cart is immutable from the cart API.

### CartItem

Fields:

- `id`: generated primary key.
- `cartId`: parent cart reference.
- `productId`: product id from product-service.
- `productNameSnapshot`: product name at add/update time.
- `unitPriceSnapshot`: product price at add/update time.
- `quantity`: positive integer.
- `createdAt`: creation timestamp.
- `updatedAt`: last update timestamp.

Constraints:

- A cart may contain each product at most once.
- Quantity must be positive.
- Subtotal is computed as `sum(unitPriceSnapshot * quantity)`.

## API Design

All endpoints are under `/api/cart` and require authentication.

### Get Current Cart

`GET /api/cart`

Returns the active cart for the current user. If none exists, the service returns an empty active cart representation without creating a database row unless a write operation is performed.

Response shape:

```json
{
  "cartId": 1,
  "userId": 10,
  "status": "ACTIVE",
  "items": [
    {
      "productId": 100,
      "productName": "Pour Over Kit",
      "unitPrice": 49.99,
      "quantity": 2,
      "lineTotal": 99.98
    }
  ],
  "subtotal": 99.98
}
```

For an empty cart with no persisted row yet, `cartId` is `null`, `items` is empty, and `subtotal` is `0.00`.

### Add Item

`POST /api/cart/items`

Request:

```json
{
  "productId": 100,
  "quantity": 2
}
```

Behavior:

- Requires `quantity > 0`.
- Loads product details from `product-service`.
- Creates the user's active cart if needed.
- If the product is not already in the cart, inserts a new item.
- If the product is already in the cart, increases quantity by the requested quantity.
- Stores product name and unit price snapshots from product-service.
- Returns the updated cart.

### Update Item Quantity

`PUT /api/cart/items/{productId}`

Request:

```json
{
  "quantity": 3
}
```

Behavior:

- Requires `quantity > 0`.
- Requires the item to exist.
- Refreshes product name and unit price snapshots from `product-service`.
- Replaces the quantity with the requested value.
- Returns the updated cart.

### Remove Item

`DELETE /api/cart/items/{productId}`

Behavior:

- Requires the item to exist.
- Removes the item from the active cart.
- Returns HTTP `204 No Content`.

### Clear Cart

`DELETE /api/cart/items`

Behavior:

- Removes all items from the user's active cart.
- Returns HTTP `204 No Content`.
- If no active cart exists, this is treated as idempotent success.

## Product-Service Integration

`cart-service` calls product-service synchronously when adding or updating an item.

Initial implementation uses Spring Framework `RestClient` behind a small, testable adapter:

- `ProductCatalogClient` interface owned by cart-service.
- `RestClientProductCatalogClient` HTTP implementation.
- Product lookup endpoint: `GET /api/products/id/{id}` in product-service.

`product-service` currently exposes product lookup by slug, so the cart-service implementation plan must first add `GET /api/products/id/{id}` to product-service before cart-service consumes it.

The product response used by cart-service needs only:

- `id`
- `name`
- `price`
- active availability implied by the endpoint returning `200`

If product-service returns `404`, cart-service returns `404 Product not found`.

If product-service is unavailable or returns an unexpected error, cart-service returns `503 Product catalog unavailable`.

## Security

The gateway validates JWTs and forwards identity headers. `cart-service` trusts only gateway-provided identity headers in the MVP, matching the pattern used by `product-service` and `inventory-service`.

Required header:

- `X-User-Id`

Optional headers:

- `X-User-Email`
- `X-User-Roles`

Rules:

- `/actuator/health`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui.html`, and `/swagger-ui/**` are public.
- `/api/cart/**` requires an authenticated identity.
- Users can only access the cart associated with their own `X-User-Id`.
- There is no admin cart management API in the MVP.

## Error Handling

Use the same response style as existing services:

- `400 Bad Request`: validation failure or malformed request body.
- `401 Unauthorized`: missing identity header.
- `403 Forbidden`: authenticated but not allowed.
- `404 Not Found`: cart item or product not found.
- `409 Conflict`: invalid cart operation, such as modifying a checked-out cart.
- `415 Unsupported Media Type`: unsupported content type.
- `500 Internal Server Error`: unexpected error.
- `503 Service Unavailable`: product-service unavailable.

## Docker Compose And Runtime

Add:

- `cart-service` module to the parent `pom.xml`.
- `cart-postgres` with database `cart_db`.
- `cart-service` container on port `8084`.
- Eureka config for service registration.
- Gateway dependency on `cart-service` health in Docker Compose.
- CI Docker build entry for `cart-service`.

`api-gateway` already contains a `/api/cart/**` route, so the implementation must update gateway tests and Docker dependency wiring there.

## Testing Strategy

Use TDD for implementation tasks.

Service-layer unit tests:

- Creates active cart on first write.
- Returns empty cart when no active cart exists.
- Adds a new item with product snapshot.
- Increments quantity when adding an existing product.
- Replaces quantity on update.
- Rejects zero or negative quantity.
- Rejects missing cart item on update/remove.
- Clears cart idempotently.
- Maps product-service `404` to product not found.
- Maps product-service outage to catalog unavailable.

Controller tests:

- Requires identity header for cart APIs.
- Validates request body.
- Maps success responses.
- Maps domain exceptions to expected status codes.

Repository tests:

- Finds active cart by user id.
- Enforces one cart item per product per cart.
- Persists cart and item timestamps.

Config tests:

- Security permits health and OpenAPI.
- Security protects `/api/cart/**`.
- OpenAPI endpoint is available.

Integration-level smoke tests can be added after order-service is introduced, because checkout and reservation are outside this MVP.

## Rollout

Implementation happens on the `cart-service` branch/worktree.

Recommended milestone order:

1. Add module skeleton and baseline app.
2. Add cart domain entities and repositories.
3. Add DTOs, exceptions, and product client abstraction.
4. Add cart service behavior with TDD.
5. Add controller and security.
6. Add OpenAPI, Dockerfile, Compose, README, and CI updates.
7. Run module tests, full Maven tests, Docker Compose config validation, and Docker build.

Each milestone produces small, reviewable commits.
