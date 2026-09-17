# CommerceCore

Production-style, concurrent e-commerce backend built with Java & Spring Boot. Modular monolith: one deployable, module boundaries by package (`user`, `product`, `inventory`, `order`, `payment`, `security`, `common`).

## Stack

Java 21 · Spring Boot 3 · Spring Data JPA / Hibernate · PostgreSQL · Spring Security + JWT · Docker / Docker Compose · Maven

## Architecture

```
                        Client / UI
                             |
                             v
                       Spring Boot API
                             |
        +--------------------+--------------------+
        |                    |                     |
     User Module        Product Module         Order Module
                                                     |
                              +----------------------+----------+
                              |                                 |
                        Inventory Module                 Payment Module
                              |                                 |
                              +----------------+-----------------+
                                               v
                                          PostgreSQL
```

## Concurrency model (the centerpiece)

Scenario: one unit of stock, three customers order it at the same instant. Exactly one must succeed.

`OrderService.placeOrder()` runs the whole reserve -> pay -> confirm/release workflow in a single `@Transactional` method:

1. Every `Inventory` row touched by the order is fetched with `SELECT ... FOR UPDATE`
   (`InventoryRepository.findByProductIdForUpdate`, `@Lock(PESSIMISTIC_WRITE)`), held for the
   life of the transaction. Concurrent `placeOrder()` calls on the same product serialize here
   instead of racing past the availability check.
2. `Inventory.reserve()` enforces `reserved <= quantity` and throws `InsufficientStockException`
   (-> HTTP 409) if the request can't be satisfied.
3. `Inventory.@Version` still guards any code path that reads a row without taking the lock
   (defense in depth beyond the order-placement path).
4. On success: inventory is deducted, `Order` transitions `PENDING -> INVENTORY_RESERVED ->
   PAYMENT_PENDING -> CONFIRMED`. On payment failure: the reservation is released and the order
   moves to `FAILED`. Invalid transitions (e.g. `CONFIRMED -> PENDING`) are rejected by
   `OrderStatus.canTransitionTo()`.

Verified live (docker-compose Postgres, 3 parallel `curl POST /api/orders` against a
`quantity=1` product): one request returned `201 CONFIRMED`, the other two returned `409
Insufficient stock`, and the row settled at `quantity=0, reserved_quantity=0` — no overselling,
no lost updates.

## Running locally

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

The app reads `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET` from the
environment (see [application.yml](src/main/resources/application.yml) for defaults). Or run the
whole stack, app included:

```bash
docker compose up -d
```

## API

All request/response bodies are JSON. Protected endpoints take `Authorization: Bearer <token>`.

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | public | `{email,password,firstName,lastName}` -> `{accessToken,tokenType}`, role defaults to `CUSTOMER` |
| POST | `/api/auth/login` | public | `{email,password}` -> `{accessToken,tokenType}` |
| GET | `/api/products?page=&size=&sort=` | public | paginated, active products only |
| POST | `/api/products` | ADMIN | create |
| PUT | `/api/products/{id}` | ADMIN | full update |
| DELETE | `/api/products/{id}` | ADMIN | |
| POST | `/api/inventory/{productId}/stock` | ADMIN | `{quantity}` — sets absolute stock, upserts the row |
| GET | `/api/orders?page=&size=` | authenticated | caller's own orders |
| POST | `/api/orders` | authenticated | `{items:[{productId,quantity}]}` -> runs the workflow above |

There's currently no endpoint to promote a user to `ADMIN` — do it directly in the database
(`UPDATE users SET role = 'ADMIN' WHERE email = '...'`) until an admin-provisioning flow exists.

### Error responses

A `GlobalExceptionHandler` maps domain exceptions to a consistent body instead of a raw 500:

```json
{"timestamp":"...", "status":409, "error":"Conflict", "message":"...", "path":"/api/orders"}
```

| Status | When |
|---|---|
| 400 | bean validation failures, malformed input |
| 401 | bad credentials (message is always "Invalid credentials" — doesn't reveal whether the email exists) |
| 404 | referenced user/product/order not found |
| 409 | duplicate email/sku, insufficient stock, invalid order state transition |

## Data model

`users`, `products`, `inventory` (`quantity`, `reserved_quantity`, optimistic `version`),
`orders`, `order_items`, `payments`. See the entity classes under `src/main/java/com/commercecore/`
for the authoritative shape.

## Status

**Built:** entities + repositories for all six modules, JWT auth (register/login, role-gated
routes), REST controllers for products/inventory/orders, the pessimistic-locking order
workflow described above, global exception handling.

**Not yet built:**
- `GET /api/users/me`, admin user-provisioning
- XML/JAXB supplier inventory import
- Automated test suite (unit + Testcontainers integration tests)
- JMeter/Gatling load test and results
- Jenkins CI/CD pipeline
