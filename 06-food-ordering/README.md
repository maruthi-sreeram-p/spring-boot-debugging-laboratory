# Spicebox — Food Ordering Service

`food-ordering-service` is the backend behind Spicebox, a food delivery marketplace. It
owns restaurants and their menus, customer accounts, orders, and the status track an order
follows from checkout to the customer's door.

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
| Persistence | Spring Data JPA, Hibernate 6, MySQL 8 |
| Messaging | RabbitMQ, topic exchange, JSON message converter |
| Async | `@EnableAsync`, Spring application events |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.spicebox.ordering
├── config/        SecurityConfig, RabbitConfig, SpiceboxProperties
├── controller/    Auth, Catalog, Order, Ops
├── dto/           request and response payloads, PagedResponse, ApiError
├── entity/        Customer, Restaurant, MenuItem, FoodOrder, OrderItem, OrderNotification
├── exception/     domain exceptions + GlobalExceptionHandler
├── mapper/        entity to DTO translation
├── messaging/     OrderEventPublisher, AnalyticsListener, DispatchListener
├── repository/    Spring Data JPA repositories
├── security/      SpiceboxUser, SpiceboxUserDetailsService
└── service/       CustomerService, CatalogService, OrderService, OrderStatusService,
                   NotificationService, KitchenIntakeListener
```

---

## How an order flows

Checkout is synchronous; everything after it is not.

```
POST /api/orders
      │
      ├── writes food_orders + order_items                        (same transaction)
      ├── publishes OrderPlacedEvent          ──► KitchenIntakeListener  (@Async, in-process)
      │                                              └── PLACED ──► ACCEPTED
      ├── publishes order.placed to RabbitMQ  ──► analytics.orders ──► AnalyticsListener
      └── calls NotificationService           (@Async) ──► writes order_notifications
                                                     
Kitchen marks READY_FOR_PICKUP
      └── publishes to RabbitMQ               ──► delivery.dispatch ──► DispatchListener
                                                     └── READY_FOR_PICKUP ──► OUT_FOR_DELIVERY
```

Two different asynchronous mechanisms are in play, and it matters which one you are
looking at:

- **Spring application events** (`OrderPlacedEvent`) are in-process. They never leave the
  JVM and there is nothing to inspect in RabbitMQ.
- **RabbitMQ messages** cross a broker. They have exchanges, routing keys, bindings and
  queues, all of which you can list from the command line.

The status track is `PLACED → ACCEPTED → PREPARING → READY_FOR_PICKUP → OUT_FOR_DELIVERY →
DELIVERED`, with `CANCELLED` and `REJECTED` as early exits.

---

## Running it

### 1. Backing services

```bash
docker compose -f infra/docker-compose.yml up -d mysql rabbitmq
```

MySQL on `localhost:3307` (`fooddb`), RabbitMQ on `localhost:5672` with the management UI
at <http://localhost:15672> (`labuser` / `labpass`).

### 2. Configuration

`application.yml` holds placeholders (`YOUR_DATABASE_*`, `YOUR_RABBITMQ_*`) and reads every
value from the environment. The `local` profile — active by default — fills them in from
`infra/docker-compose.yml`.

Exchange, routing keys and queue names all live under `spicebox.messaging`.

### 3. Start

```bash
mvn spring-boot:run
```

The exchange, queues and bindings are declared by the application at startup, so RabbitMQ
needs no manual setup. Schema and seed are applied on every start; both are idempotent.

### 4. Sign in

| Account | Password | Role |
|---|---|---|
| `ayesha.khan@example.com` | `Password123!` | customer, has order history |
| `daniel.otieno@example.com` | `Password123!` | customer, has order history |
| `mira.bhatt@example.com` | `Password123!` | customer, no orders yet |
| `ops@spicebox.test` | `Admin123!` | operations (`ROLE_OPS`) |

Restaurants: Curry Leaf Kitchen (1), Napoli Corner (2), Wok This Way (3), The Grill House
(4, inactive). Ordering rules: minimum order 99.00, delivery fee 39.00, free above 499.00.

---

## Exercising it

```bash
BASE=http://localhost:8080
CUST='ayesha.khan@example.com:Password123!'
OPS='ops@spicebox.test:Admin123!'

curl -s "$BASE/api/restaurants"
curl -s "$BASE/api/restaurants/1/menu"

curl -s -u "$CUST" -X POST "$BASE/api/orders" \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"menuItemId":1,"quantity":2},{"menuItemId":2,"quantity":1}]}'

curl -s -u "$CUST" "$BASE/api/orders"
curl -s -u "$CUST" "$BASE/api/orders/4/notifications"

curl -s -u "$OPS" "$BASE/api/ops/intake-queue"
curl -s -u "$OPS" -X POST "$BASE/api/ops/orders/4/status" \
  -H 'Content-Type: application/json' -d '{"status":"PREPARING"}'
```

Full contracts are in [API.md](API.md).

---

## Inspecting state while you debug

**MySQL:**

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass fooddb \
  -e "select id, order_code, restaurant_id, status, placed_at from food_orders order by id;"
docker exec -it lab-mysql mysql -ulabuser -plabpass fooddb \
  -e "select * from order_notifications order by id;"
```

Any order whose items do not all come from its restaurant:

```sql
SELECT o.id, o.order_code, o.restaurant_id, m.restaurant_id AS item_restaurant, i.item_name
  FROM food_orders o
  JOIN order_items i ON i.order_id = o.id
  JOIN menu_items m ON m.id = i.menu_item_id
 WHERE m.restaurant_id <> o.restaurant_id;
```

**RabbitMQ** — this is where most of this project is debugged:

```bash
docker exec -it lab-rabbitmq rabbitmqctl list_exchanges
docker exec -it lab-rabbitmq rabbitmqctl list_queues name messages consumers
docker exec -it lab-rabbitmq rabbitmqctl list_bindings source_name routing_key destination_name
```

The management UI at <http://localhost:15672> has an **Exchanges → food.orders → Publish
message** form, which lets you publish a routing key by hand and watch where it lands.
Under **Bindings** it will also tell you, live, whether a given routing key matches
anything.

**Async work** happens on pool threads, so its failures never reach the HTTP response.
Watch the log:

```bash
grep -E "task-[0-9]+|Unexpected exception occurred invoking async method" app.log
```

A `@Async` method that returns `void` has nowhere to report an exception to — Spring logs
it and the caller never finds out. Any time an asynchronous step "does nothing", read the
log before anything else.

---

## Tests

```bash
mvn test
```

The suite covers order mapping and a repository slice against H2. It passes, and it starts
neither RabbitMQ nor an async executor.
