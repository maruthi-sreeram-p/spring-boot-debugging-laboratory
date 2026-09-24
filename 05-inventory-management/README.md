# Vantage Supply — Inventory Service

`inventory-service` is the stock system of record for Vantage Supply, a distributor running
three live warehouses. It owns products, warehouses, suppliers, stock levels, the movement
ledger and purchase-order receiving.

This is a **debugging lab project**. The application is feature-complete and starts
cleanly, but it does not behave correctly in every case. Work from `DEBUGGING_GUIDE.md`.
Do not open `SOLUTION.md` until you have finished.

---

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| Web | Spring MVC, REST/JSON |
| Security | Spring Security, HTTP Basic, BCrypt, `@EnableMethodSecurity` |
| Persistence | Spring Data JPA, Hibernate 6, PostgreSQL 16 |
| Cache | Redis 7 via `StringRedisTemplate`, write-through on the hot read path |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.vantage.inventory
├── cache/         StockCache (Redis-backed stock figures)
├── config/        SecurityConfig, InventoryProperties
├── controller/    Stock, PurchaseOrder, Catalog
├── dto/           request and response payloads, PagedResponse, ApiError
├── entity/        Warehouse, Product, Supplier, StockLevel, StockMovement,
│                  PurchaseOrder, PurchaseOrderLine, AppUser
├── exception/     domain exceptions + GlobalExceptionHandler
├── mapper/        entity to DTO translation
├── repository/    Spring Data JPA repositories
├── security/      InventoryUser, InventoryUserDetailsService
└── service/       StockService, ReceivingService
```

**Stock model.** `stock_levels` is a snapshot: one row per (product, warehouse) with
`quantity_on_hand` and `quantity_reserved`. `stock_movements` is the ledger: one row per
physical event — receipts, issues and adjustments — carrying a signed quantity. The two are
meant to agree: the sum of a product's movements in a warehouse equals its
`quantity_on_hand`. That reconciliation is run every month end.

**Cache model.** Per-warehouse stock reads are the busiest thing the service does, so the
current figures are kept in Redis with a 15-minute TTL and refreshed whenever the database
moves. The network-wide view is not cached — it is used by reporting screens, not by the
picking floor.

**Receiving model.** A purchase order has lines with `quantity_ordered` and
`quantity_received`. A delivery is posted against the order; stock rises, a `RECEIPT`
movement is written, and the order moves to `PARTIALLY_RECEIVED` or `RECEIVED`.

---

## Running it

### 1. Backing services

```bash
docker compose -f infra/docker-compose.yml up -d postgres redis
```

PostgreSQL on `localhost:5432` (`inventorydb`), Redis on `localhost:6380`.

### 2. Configuration

`application.yml` holds placeholders (`YOUR_DATABASE_HOST`, `YOUR_DATABASE_USER`,
`YOUR_DATABASE_PASSWORD`, `YOUR_REDIS_HOST`) and reads every value from the environment.
The `local` profile — active by default — fills them in from `infra/docker-compose.yml`.

### 3. Start

```bash
mvn spring-boot:run
```

Schema and seed are applied on every start; both are idempotent.

### 4. Sign in

| Account | Password | Role |
|---|---|---|
| `dispatch.blr@vantage.test` | `Password123!` | `ROLE_WAREHOUSE` — Bengaluru floor |
| `dispatch.mum@vantage.test` | `Password123!` | `ROLE_WAREHOUSE` — Mumbai floor |
| `controller@vantage.test` | `Admin123!` | `ROLE_CONTROLLER` — inventory control |

Warehouses: `WH-BLR` (1), `WH-MUM` (2), `WH-DEL` (3), `WH-CHE` (4, inactive).
Open purchase orders: `PO-2025-0021` (id 1, into WH-BLR), `PO-2025-0022` (id 2, into WH-MUM).

---

## Exercising it

```bash
BASE=http://localhost:8080
WH='dispatch.blr@vantage.test:Password123!'
CTRL='controller@vantage.test:Admin123!'

curl -s -u "$WH" "$BASE/api/stock/products/1"
curl -s -u "$WH" "$BASE/api/stock/products/1/warehouses/1"
curl -s -u "$WH" "$BASE/api/stock/warehouses/1"
curl -s -u "$WH" "$BASE/api/stock/movements?productId=1&warehouseId=1"

curl -s -u "$WH" "$BASE/api/purchase-orders/1"
curl -s -u "$WH" -X POST "$BASE/api/purchase-orders/1/receipts" \
  -H 'Content-Type: application/json' \
  -d '{"lines":[{"productId":1,"quantity":400}],"note":"Pallet 1 of 3"}'

curl -s -u "$CTRL" -X POST "$BASE/api/stock/adjustments" \
  -H 'Content-Type: application/json' \
  -d '{"productId":3,"warehouseId":1,"quantityDelta":-4,"reason":"Damaged in handling"}'
```

Full contracts are in [API.md](API.md).

---

## Inspecting state while you debug

**PostgreSQL:**

```bash
docker exec -it lab-postgres psql -U labuser -d inventorydb \
  -c "select product_id, warehouse_id, quantity_on_hand, quantity_reserved from stock_levels order by product_id, warehouse_id;"
```

The month-end reconciliation — the snapshot against the ledger. Every row should come back
with a zero difference:

```sql
SELECT s.product_id, s.warehouse_id, s.quantity_on_hand,
       COALESCE(SUM(m.quantity), 0) AS ledger_total,
       s.quantity_on_hand - COALESCE(SUM(m.quantity), 0) AS difference
  FROM stock_levels s
  LEFT JOIN stock_movements m
    ON m.product_id = s.product_id AND m.warehouse_id = s.warehouse_id
 GROUP BY s.product_id, s.warehouse_id, s.quantity_on_hand
 ORDER BY s.product_id, s.warehouse_id;
```

**Redis** — what the cache is actually holding, and for how long:

```bash
docker exec -it lab-redis redis-cli KEYS 'inv:*'
docker exec -it lab-redis redis-cli GET 'inv:stock:1'
docker exec -it lab-redis redis-cli TTL 'inv:stock:1'
docker exec -it lab-redis redis-cli FLUSHALL      # start from a cold cache
docker exec -it lab-redis redis-cli MONITOR       # watch every command as it arrives
```

`MONITOR` is the single most useful tool in this project: run it in one terminal and drive
the API from another, and you can see exactly which keys the application touches and when.

---

## Tests

```bash
mvn test
```

The suite covers stock mapping and a repository slice against H2. It passes, and it never
touches Redis.
