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
environment (see [application.yml](src/main/resources/application.yml) for defaults). Set
`ADMIN_EMAIL`/`ADMIN_PASSWORD` on first boot to auto-create an admin account (see
[Admin provisioning](#admin-provisioning) below) — without them there's no ADMIN user at all yet.
Or run the whole stack, app included:

```bash
docker compose up -d
```

Once it's up, **`http://localhost:8080/swagger-ui/index.html`** lists every endpoint and lets you
fire real requests at it — hit `/api/auth/login`, paste the `accessToken` into the panel's
Authorize button, and everything else below is one click away.

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
| GET | `/api/users/me` | authenticated | caller's own profile |
| PATCH | `/api/users/{id}/role` | ADMIN | `{role:"ADMIN"\|"CUSTOMER"}` — refuses to demote the last remaining admin (409) |

### Admin provisioning

Promoting a user to `ADMIN` normally requires an `ADMIN` token — but a fresh database has none,
so that's a chicken-and-egg problem. `AdminBootstrapRunner` solves it on startup: if no `ADMIN`
exists and both `ADMIN_EMAIL`/`ADMIN_PASSWORD` are set in the environment, it creates (or
promotes, if that email already registered as a customer) that account. It deliberately never
falls back to a guessable default password — set both env vars or provision manually
(`UPDATE users SET role = 'ADMIN' WHERE email = '...'`). Every admin after the first is promoted
through `PATCH /api/users/{id}/role` by an existing admin.

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
routes, admin bootstrap/provisioning), REST controllers for products/inventory/orders/users, the
pessimistic-locking order workflow described above, global exception handling, an interactive
Swagger UI panel, and a Mockito unit-test suite for every `@Service` class (20 tests:
`OrderServiceTest`, `InventoryServiceTest`, `CustomUserDetailsServiceTest`, `JwtServiceTest`).

**Not yet built:**
- XML/JAXB supplier inventory import
- Testcontainers integration tests (`OrderControllerIntegrationTest` etc. — exercises the real
  concurrency scenario end-to-end against a real Postgres, not mocks)
- JMeter/Gatling load test and results
- Jenkins CI/CD pipeline
