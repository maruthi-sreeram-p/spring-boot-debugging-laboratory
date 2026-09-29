# Riverstone — Order Event Processing

`order-event-service` is the event-driven pipeline behind Riverstone's order flow. An order
is accepted over HTTP and everything after that happens on Kafka: inventory reserves the
stock, a notification goes out, and the order status catches up.

```
POST /api/orders
      │  write the order, publish OrderCreated
      ▼
orders.created  (3 partitions)
      │
      ▼
InventoryConsumer          group: inventory-service
      │  reserve stock, publish InventoryEvent
      ▼
inventory.events  (3 partitions)
      ├──► NotificationConsumer    tells the customer
      └──► OrderStatusConsumer     moves the order to CONFIRMED or REJECTED
```

This is a **debugging lab project**. The application is feature-complete and starts
cleanly, but it does not behave correctly in every case. Work from `DEBUGGING_GUIDE.md`.
Do not open `SOLUTION.md` until you have finished.

---

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| Web | Spring MVC, REST/JSON |
| Security | Spring Security, HTTP Basic, BCrypt |
| Persistence | Spring Data JPA, Hibernate 6, PostgreSQL 16 |
| Messaging | Apache Kafka, Spring Kafka, JSON serialization, manual acknowledgement |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.riverstone.orderevents
├── config/        SecurityConfig, KafkaTopicConfig, RiverstoneProperties
├── controller/    Order, Platform
├── dto/           request and response payloads, OrderTraceResponse, EventLogResponse
├── entity/        OrderRecord, StockItem, Reservation, OrderNotification, EventLogEntry
├── exception/     domain exceptions + GlobalExceptionHandler
├── mapper/        entity to DTO translation
├── messaging/     OrderCreatedEvent, InventoryEvent, InventoryConsumer,
│                  NotificationConsumer, OrderStatusConsumer
├── repository/    Spring Data JPA repositories
├── security/      PipelineUser, PipelineUserDetailsService
└── service/       OrderService, EventRecorder
```

---

## The event log — your main instrument

Kafka does not remember who consumed what, so this service keeps its own record. **Every
event a consumer takes off a topic is written to `event_log`** with the coordinates it came
from:

| Column | Why it matters |
|---|---|
| `topic`, `partition_no`, `offset_no` | which partition carried the message, and where |
| `message_key` | the key the producer used, or null if it did not use one |
| `consumer_group`, `handled_by` | which group, and which consumer class, actually processed it |

`EventRecorder` writes these rows in their own transaction, so the record of what arrived
survives whatever the handler then does with it. That is deliberate: it means the log still
tells you an event was received even when the work was rolled back.

Two endpoints expose it:

```bash
curl -s -u "$OPS" localhost:8080/api/platform/events
curl -s -u "$OPS" "localhost:8080/api/platform/events?topic=orders.created"
curl -s -u "$AUTH" localhost:8080/api/orders/RS-441023/trace
```

`/trace` is the one to reach for first: it shows an order's current state next to every
event any consumer handled for it, plus what inventory and notifications actually recorded.

---

## Kafka facts this project relies on

- **Ordering is per partition, not per topic.** Two events that must be processed in
  sequence have to land on the same partition, and the only thing that guarantees that is
  the **message key** — Kafka hashes it to choose a partition.
- **A null key means the producer picks.** Modern clients use a sticky partitioner: they
  keep filling one partition until a batch is sent. At low volume that can mean *every*
  message goes to the same partition for a long time.
- **A consumer group divides the partitions among its members.** Every message goes to
  exactly one member of a group. Two services that both need every message must be in
  **different** groups.
- **Acknowledgement commits an offset.** Once committed, the broker will not send that record
  again to that group. A record whose work failed after the offset was committed is gone.

---

## The simulated failure modes

Behaviour is driven by the SKU, the way a sandbox uses reserved values:

| SKU | Behaviour |
|---|---|
| `RS-WIDGET-01`, `RS-WIDGET-02`, `RS-GADGET-01` | ordinary, plenty of stock |
| `RS-SCARCE-01` | only 3 available — order more to see the rejection path |
| `RS-GHOST-01` | a pre-release line; the allocation plan check fails late in processing |
| `RS-POISON-01` | a discontinued line; the consumer refuses it outright |

---

## Running it

### 1. Backing services

```bash
docker compose -f infra/docker-compose.yml up -d postgres kafka
```

PostgreSQL on `localhost:5432` (`ordereventsdb`), Kafka on `localhost:9092`.

### 2. Configuration

`application.yml` holds placeholders (`YOUR_DATABASE_*`, `YOUR_KAFKA_BROKER`) and reads
every value from the environment. The `local` profile — active by default — fills them in.
Topic names, partition count and consumer groups live under `riverstone:`.

### 3. Start

```bash
mvn spring-boot:run
```

Topics are created by the application at startup with 3 partitions each. Schema and seed are
applied on every start; both are idempotent.

### 4. Sign in

| Account | Password | Role |
|---|---|---|
| `orders@riverstone.test` | `Password123!` | `ROLE_ORDERS` — place and read orders |
| `platform@riverstone.test` | `Admin123!` | `ROLE_PLATFORM` — the event log and stock view |

---

## Exercising it

```bash
BASE=http://localhost:8080
AUTH='orders@riverstone.test:Password123!'
OPS='platform@riverstone.test:Admin123!'

REF=$(curl -s -u "$AUTH" -X POST "$BASE/api/orders" \
  -H 'Content-Type: application/json' \
  -d '{"customerRef":"CUST-9001","sku":"RS-WIDGET-01","quantity":4}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['orderRef'])")

sleep 3
curl -s -u "$AUTH" "$BASE/api/orders/$REF/trace"
curl -s -u "$OPS" "$BASE/api/platform/events"
curl -s -u "$OPS" "$BASE/api/platform/stock"
```

### Load script

`scripts/burst_orders.py` fires many concurrent orders for one SKU and checks the stock
arithmetic afterwards:

```bash
python scripts/burst_orders.py --sku RS-WIDGET-01 --count 40 --quantity 1
```

Full contracts are in [API.md](API.md).

---

## Inspecting Kafka directly

```bash
docker exec -it lab-kafka bash -lc "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic orders.created"

docker exec -it lab-kafka bash -lc "/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list"

docker exec -it lab-kafka bash -lc "/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group order-pipeline"
```

The `--describe --group` output is the most useful single command in this project. It shows
every member of a group, which partitions each one owns, the current offset and the lag. A
member with no partitions assigned is a consumer that will never do any work.

To read the raw messages on a topic:

```bash
docker exec -it lab-kafka bash -lc "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic inventory.events --from-beginning --property print.key=true --property print.partition=true --timeout-ms 5000"
```

`print.key=true` and `print.partition=true` are worth remembering — most partitioning
problems are visible in that output alone.

**PostgreSQL:**

```bash
docker exec -it lab-postgres psql -U labuser -d ordereventsdb \
  -c "select topic, partition_no, message_key, event_type, consumer_group, handled_by from event_log order by id;"

docker exec -it lab-postgres psql -U labuser -d ordereventsdb \
  -c "select status, count(*) from orders group by status;"
```

---

## Tests

```bash
mvn test
```

The suite covers mapping and a repository slice against H2. It passes, and it starts no
broker.
