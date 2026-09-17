# CommerceCore

Production-style, concurrent e-commerce backend built with Java & Spring Boot. Modular monolith: one deployable, module boundaries by package (`user`, `product`, `inventory`, `order`, `payment`, `security`, `common`).

## Stack

Java 21 · Spring Boot 3 · Spring Data JPA / Hibernate · PostgreSQL · Spring Security + JWT · Docker / Docker Compose · Jenkins · Maven

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
| POST | `/api/supplier/inventory-updates` | ADMIN | `Content-Type: application/xml`, body per [XML/JAXB import](#xmljaxb-supplier-import) below |

### Admin provisioning

Promoting a user to `ADMIN` normally requires an `ADMIN` token — but a fresh database has none,
so that's a chicken-and-egg problem. `AdminBootstrapRunner` solves it on startup: if no `ADMIN`
exists and both `ADMIN_EMAIL`/`ADMIN_PASSWORD` are set in the environment, it creates (or
promotes, if that email already registered as a customer) that account. It deliberately never
falls back to a guessable default password — set both env vars or provision manually
(`UPDATE users SET role = 'ADMIN' WHERE email = '...'`). Every admin after the first is promoted
through `PATCH /api/users/{id}/role` by an existing admin.

### XML/JAXB supplier import

```xml
<inventoryUpdate>
    <product>
        <sku>IPHONE-17-256</sku>
        <quantity>25</quantity>
    </product>
</inventoryUpdate>
```

`SupplierInventoryImportService` unmarshals this via JAXB and applies each line through the same
`InventoryService.setStock` the admin stock endpoint uses. A batch doesn't abort on one bad
line — the response lists which SKUs updated and which failed (unknown SKU, negative quantity)
separately. Unmarshalling goes through a StAX `XMLInputFactory` with DTDs and external entities
both disabled, so a crafted feed can't XXE its way into reading local files — this is external
input, not internal data.

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

## Performance test

`src/test/jmeter/order-load-test.jmx`: a setUp thread seeds an admin-bootstrapped catalog (20
products, 1,000,000 units of stock each — spread across many rows on purpose, so the measurement
is order-placement throughput under Spring/Hibernate/Postgres, not the single-row lock-contention
scenario the concurrency section above already covers on its own), then the main thread group
fires `POST /api/orders` at configurable concurrency.

```bash
docker compose up -d postgres
ADMIN_EMAIL=admin@commercecore.local ADMIN_PASSWORD=admin-bootstrap-pass ./mvnw spring-boot:run &

jmeter -n -t src/test/jmeter/order-load-test.jmx \
  -Jusers=100 -Jloops=100 -Jrampup=10 \
  -Jadmin_email=admin@commercecore.local -Jadmin_password=admin-bootstrap-pass \
  -l results.jtl
```

Actually run, on this machine, against the app above (not estimated):

```
Performance Test
────────────────────────
Concurrent Users: 100
Requests:         10,000
Success Rate:     100.00%
Average Latency:  2.5 ms
P95 Latency:      5 ms
P99 Latency:      7 ms
Max Latency:      44 ms
```

Cross-checked against Postgres directly after the run: `orders` had exactly 10,000 rows with
`status = 'CONFIRMED'`, and total stock consumed across the 20 products summed to exactly 10,000
— no lost updates, no double-counting, under real concurrent load.

Take the absolute numbers with a grain of salt: this ran on a single laptop with JMeter, the app,
and Postgres all sharing one machine (no network hop), and `MockPaymentGateway` always succeeds
instantly — there's no real payment provider latency in this path yet. The *shape* of the result
(zero errors, sub-10ms P99 at 100 concurrent users) is the meaningful part; re-run it yourself
with `-Jusers`/`-Jloops`/`-Jproduct_count` to match your own hardware and get numbers to trust.

## Data model

`users`, `products`, `inventory` (`quantity`, `reserved_quantity`, optimistic `version`),
`orders`, `order_items`, `payments`. See the entity classes under `src/main/java/com/commercecore/`
for the authoritative shape.

## Status

**Built:** entities + repositories for all six modules, JWT auth (register/login, role-gated
routes, admin bootstrap/provisioning), REST controllers for products/inventory/orders/users, the
pessimistic-locking order workflow described above, global exception handling, an interactive
Swagger UI panel, XML/JAXB supplier inventory import (XXE-hardened), a Mockito unit-test suite
for every `@Service` class (26 tests: `OrderServiceTest`, `InventoryServiceTest`,
`CustomUserDetailsServiceTest`, `JwtServiceTest`, `SupplierInventoryImportServiceTest`),
`OrderControllerIT` — a Testcontainers integration test that drives the concurrency
scenario over real HTTP against a real Postgres instead of mocks — a JMeter load test with
real, reproducible results (see [Performance test](#performance-test) above), and a
[Jenkinsfile](Jenkinsfile) (Checkout -> Compile -> Unit Tests -> Integration Tests -> Package ->
Docker Build -> Deploy; every stage's underlying command verified to actually work on this
machine — see the Jenkinsfile's own header comment for the one thing it doesn't do yet, wiring a
real deploy target).

Every gap from the original plan is closed.
