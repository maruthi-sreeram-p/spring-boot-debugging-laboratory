# Realistic Java Backend Debugging Laboratory — Build Plan

> **Purpose of this file.** This is the construction plan for the lab, not a hint sheet.
> It describes *what each project is*, *how it is built*, and *which debugging muscles it
> trains*. It deliberately does **not** describe the defects, their locations, or their
> categories. Each project carries its own `DEBUGGING_GUIDE.md` (symptoms + graded hints)
> and a sealed `SOLUTION.md` (answer key — do not open it until you have genuinely tried).

---

## 0. Ground rules for this lab

| Rule | Detail |
|---|---|
| Applications look finished | Every project compiles, boots, and serves its documented API. Nothing is a stub. |
| Defects are unmarked | No `// BUG`, no `// FIXME`, no suspicious identifiers. Defects live inside plausible code that a real teammate would have written and a real reviewer would have approved. |
| Defects are behavioural | Wrong state, wrong configuration, wrong framework usage, wrong assumptions — never `1/0` or `throw new RuntimeException("bug")`. |
| Symptoms are not root causes | In most projects the visible failure is one or more layers away from the cause. |
| Some defects are layered | Fixing the first thing can reveal a second thing that was previously masked. |
| Credentials are placeholders | `YOUR_DATABASE_PASSWORD`, `YOUR_KAFKA_BROKER`, `YOUR_REDIS_HOST`. Every value is env-overridable; `infra/docker-compose.yml` supplies matching local defaults. |
| Nothing is pushed anywhere | No git commits, no deploys, no external services, no real payment providers. |

---

## 1. Shared infrastructure

A single `infra/docker-compose.yml` at the repository root provides every backing service
the lab needs. Projects only use the services they actually require.

| Service | Image | Host port | Used by |
|---|---|---|---|
| MySQL 8 | `mysql:8.0` | 3306 | 01, 03, 04, 06, 07, 08, 13 |
| PostgreSQL 16 | `postgres:16` | 5432 | 02, 05, 09, 10, 11, 12, 14, 15 |
| Redis 7 | `redis:7` | 6379 | 01, 05, 11, 12, 15 |
| Kafka (KRaft) | `apache/kafka:3.7.0` | 9092 | 10, 14, 15 |
| RabbitMQ | `rabbitmq:3-management` | 5672 / 15672 | 06, 09, 14, 15 |
| Adminer | `adminer` | 8081 | database inspection for all projects |

Start only what you need:

```bash
docker compose -f infra/docker-compose.yml up -d mysql redis
```

---

## 2. Common conventions across all projects

Every project follows the same package layout, so that navigating an unfamiliar project
feels like navigating an unfamiliar company repository rather than learning a new dialect.

```
com.<domain>.<app>
├── config/          Spring configuration classes
├── controller/      REST entry points, request and response only
├── dto/             request and response types
├── entity/          JPA entities
├── repository/      Spring Data repositories
├── service/         business logic and transaction boundaries
├── mapper/          entity to dto translation
├── exception/       domain exceptions and the @RestControllerAdvice
├── security/        filters, providers, user details (where applicable)
├── messaging/       producers, consumers, listeners (where applicable)
└── cache/           cache abstractions and key builders (where applicable)
```

Supporting files per project:

```
README.md                 what it is, how to run it, how to exercise it
API.md                    endpoint contracts: request, response, status, auth
DEBUGGING_GUIDE.md        business context, expected behaviour, symptoms, graded hints
SOLUTION.md               sealed answer key
src/main/resources/
  application.yml         profile-aware config with placeholder credentials
  schema.sql / data.sql   realistic schema and a small realistic seed
src/test/java/...         JUnit tests that pass — scaffolding, not an oracle
```

**On tests.** Tests in this lab are written the way real teams write them: around the
happy path, and around the assumptions the original author *believed* were true. A green
build is therefore not evidence of correctness. Some suites keep passing while the
application misbehaves. That gap is part of the exercise.

---

## 3. Difficulty model

| Band | Projects | Character of the work |
|---|---|---|
| 1 | 01 – 03 | Mostly self-contained defects, one or two components in play. Trains reading Spring logs, JPA query behaviour, DTO and mapping inspection, HTTP status semantics. |
| 2 | 04 – 06 | Several components interact. Trains request tracing across layers, transaction boundaries, async handoff, time and scheduling reasoning. |
| 3 | 07 – 09 | Security, persistence and business rules entangled. Trains authorization reasoning, dynamic query construction, message delivery semantics. |
| 4 | 10 – 12 | Messaging, caching, asynchrony. Trains broker inspection, consumer group reasoning, cache key and serialization debugging, token and claims analysis. |
| 5 | 13 – 14 | Concurrency, transactions, distributed effects. Trains lock reasoning, isolation levels, retry semantics, at-least-once delivery consequences. |
| 6 | 15 | Production style. Unknown count, unknown categories, layered failures. Trains systematic bisection of an unfamiliar system. |

---

## 4. The fifteen projects

### 01 — E-Commerce Order Management (`01-ecommerce-order-management`)
- **Domain:** storefront back office — customers, catalogue, cart, checkout, order history.
- **Stack:** Spring Boot, Spring Security, Spring Data JPA, Hibernate, MySQL, Redis, Maven, JUnit.
- **Architecture:** classic layered monolith. The cart lives in Redis, orders in MySQL. Checkout drains the cart and decrements stock inside one service method.
- **Concepts trained:** entity lifecycle, lazy loading, `@Transactional` placement, DTO projection, cache and database divergence, HTTP status semantics.
- **Difficulty:** 2/10. **Complexity:** roughly 40 source files, 6 entities, 5 controllers.
- **Approximate defect load:** a small handful, largely independent.

### 02 — Banking / Account Management (`02-banking-account-management`)
- **Domain:** retail accounts — customers, accounts, deposits, withdrawals, transfers, statements.
- **Stack:** Spring Boot, Spring Security, JPA/Hibernate, PostgreSQL, Maven, JUnit.
- **Architecture:** ledger style. Every balance change writes a `transactions` row; a transfer is one service operation spanning two accounts.
- **Concepts trained:** transaction boundaries, rollback semantics, monetary types and rounding, optimistic locking, concurrent access to one row, validation placement.
- **Difficulty:** 3/10. **Complexity:** roughly 38 source files, 5 entities.
- **Approximate defect load:** a small handful; at least one only appears under repetition or concurrency.

### 03 — Employee Management (`03-employee-management`)
- **Domain:** HR directory — employees, departments, managers, roles, search, pagination.
- **Stack:** Spring Boot, Spring Security with role-based rules, JPA/Hibernate, MySQL, Maven, JUnit.
- **Architecture:** read-heavy CRUD with derived queries, JPQL, specification-style filtering and `Pageable` endpoints.
- **Concepts trained:** relationship mapping, fetch strategy and N+1, pagination with joins, sort field translation, method-level authorization, mapping fidelity.
- **Difficulty:** 3/10. **Complexity:** roughly 36 source files, 5 entities.
- **Approximate defect load:** a small handful, mostly in the query and mapping seam.

### 04 — Hospital Appointment System (`04-hospital-appointments`)
- **Domain:** outpatient scheduling — patients, doctors, availability, appointments, cancellations.
- **Stack:** Spring Boot, Spring Security with role-based rules, JPA/Hibernate, MySQL, Maven, JUnit.
- **Architecture:** slot generation from doctor schedules, booking with overlap rules, a scheduled job for reminders and no-show marking.
- **Concepts trained:** date and time correctness, time zones, half-open versus closed intervals, scheduled tasks, role-based access, uniqueness under contention.
- **Difficulty:** 5/10. **Complexity:** roughly 40 source files, 6 entities.
- **Approximate defect load:** several, and they interact — scheduling defects are rarely alone.

### 05 — Inventory Management (`05-inventory-management`)
- **Domain:** multi-warehouse stock — products, warehouses, stock levels, movements, suppliers, purchase orders.
- **Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, Redis, Maven, JUnit.
- **Architecture:** stock reads served from Redis, writes through the database; goods receipts adjust stock and emit movement rows.
- **Concepts trained:** cache invalidation, read-your-writes, aggregate consistency, ledger versus snapshot columns, transactional side effects on external stores.
- **Difficulty:** 5/10. **Complexity:** roughly 42 source files, 7 entities.
- **Approximate defect load:** several; at least one is environment or state dependent.

### 06 — Food Ordering Backend (`06-food-ordering`)
- **Domain:** delivery marketplace — restaurants, menus, carts, orders, delivery status.
- **Stack:** Spring Boot, Spring Security, JPA/Hibernate, MySQL, RabbitMQ, `@Async`, Maven, JUnit.
- **Architecture:** synchronous order intake, asynchronous kitchen and delivery progression driven by events and a listener.
- **Concepts trained:** async execution and proxying, transaction visibility from another thread, event publication timing, status state machines.
- **Difficulty:** 6/10. **Complexity:** roughly 40 source files, 7 entities.
- **Approximate defect load:** several, spread across the synchronous and asynchronous halves.

### 07 — Job Portal Backend (`07-job-portal`)
- **Domain:** hiring — candidates, recruiters, companies, job postings, applications.
- **Stack:** Spring Boot, Spring Security, JPA/Hibernate with Criteria and Specification, MySQL, Maven, JUnit.
- **Architecture:** an ownership model where a recruiter should see only their own company's data, plus a dynamic search endpoint with many optional filters.
- **Concepts trained:** ownership checks versus role checks, predicate composition, filter combination logic, count versus content queries, case and locale sensitivity.
- **Difficulty:** 6/10. **Complexity:** roughly 40 source files, 6 entities.
- **Approximate defect load:** several; security and query defects are interleaved.

### 08 — Payment Processing Simulation (`08-payment-processing`)
- **Domain:** a simulated payment service provider — orders, payment requests, status transitions, retries, transaction history. **No real provider is contacted.**
- **Stack:** Spring Boot, JPA/Hibernate, MySQL, Spring Retry, a scheduled reconciliation job, Maven, JUnit.
- **Architecture:** an in-process fake gateway with deliberate latency and intermittent failure, an idempotency layer, a retry policy, and reconciliation.
- **Concepts trained:** idempotency keys, state machines, retry versus replay, at-least-once side effects, reconciliation, retry and transaction interaction.
- **Difficulty:** 7/10. **Complexity:** roughly 40 source files, 6 entities.
- **Approximate defect load:** several; duplicates and illegal transitions are the theme.

### 09 — Notification Service (`09-notification-service`)
- **Domain:** fan-out notifications — email-like, SMS-like and in-app channels, templates, preferences, delivery log.
- **Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, RabbitMQ with a topic exchange and a dead-letter queue, Maven, JUnit.
- **Architecture:** a producer publishing per-channel routing keys, three consumers, failure routing to a dead-letter queue, and a delivery log recording attempts.
- **Concepts trained:** exchange, binding and routing-key semantics, manual versus automatic acknowledgement, requeue loops, dead-letter configuration, consumer idempotency.
- **Difficulty:** 7/10. **Complexity:** roughly 38 source files, 5 entities.
- **Approximate defect load:** several, concentrated in broker topology and acknowledgement handling.

### 10 — Order Event Processing (`10-order-event-processing`)
- **Domain:** an event-driven order pipeline: order created, then Kafka, then inventory, then notification, then order status.
- **Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, Kafka, Maven, JUnit.
- **Architecture:** one producer, three consumers in distinct consumer groups, JSON payloads, keyed partitioning, manual offset control in places.
- **Concepts trained:** partitioning and ordering, consumer groups, offset commit timing, deserialization contracts, poison messages, exactly-once illusions.
- **Difficulty:** 8/10. **Complexity:** roughly 42 source files, 6 entities.
- **Approximate defect load:** several; ordering and offsets dominate.

### 11 — Product Catalog with Redis (`11-product-catalog-redis`)
- **Domain:** a high-read catalogue — categories, products, variants, price history, browse by category.
- **Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, Redis via both Spring Cache and a manual `RedisTemplate`, Maven, JUnit.
- **Architecture:** declarative caching on read paths, manual cache maintenance on write paths, list caches alongside entity caches, a TTL policy.
- **Concepts trained:** cache key design, `@Cacheable` and `@CacheEvict` semantics, self-invocation and proxying, serializer choice, stale collection caches, TTL and negative caching.
- **Difficulty:** 8/10. **Complexity:** roughly 38 source files, 5 entities.
- **Approximate defect load:** several; every one of them is invisible on a cold cache.

### 12 — Authentication and Authorization Platform (`12-auth-platform`)
- **Domain:** an identity service — registration, login, JWT issuance, refresh, roles, fine-grained permissions, protected APIs.
- **Stack:** Spring Boot, Spring Security 6, JJWT, JPA/Hibernate, PostgreSQL, Redis for token state, Maven, JUnit.
- **Architecture:** stateless resource protection through a JWT filter, permission-to-authority expansion, refresh token rotation, logout and denylist.
- **Concepts trained:** filter chain ordering, claim validation, authority naming conventions, expiry and clock handling, denylist correctness, matcher precedence.
- **Difficulty:** 8/10. **Complexity:** roughly 40 source files, 6 entities.
- **Approximate defect load:** several; the interesting ones only matter to an attacker, or only after a token rotates.

### 13 — Library / Digital Lending (`13-library-lending`)
- **Domain:** lending — books, copies, members, loans, returns, due dates, fines, reservations.
- **Stack:** Spring Boot, JPA/Hibernate, MySQL, Maven, JUnit, with a bundled load script to exercise concurrency.
- **Architecture:** copy-level availability, borrowing limits, overdue fine accrual through a scheduled job, a reservation queue.
- **Concepts trained:** lost updates, pessimistic versus optimistic locking, transaction propagation, read-modify-write races, date arithmetic in fine calculation.
- **Difficulty:** 9/10. **Complexity:** roughly 40 source files, 7 entities.
- **Approximate defect load:** several; the headline defect needs concurrent requests to show itself.

### 14 — Logistics / Delivery Management (`14-logistics-delivery`)
- **Domain:** last-mile delivery — orders, delivery agents, shipments, tracking events, assignment.
- **Stack:** Spring Boot, JPA/Hibernate, PostgreSQL, Kafka for the tracking stream, RabbitMQ for assignment commands, Maven, JUnit.
- **Architecture:** an assignment engine consuming commands, an agent capacity model, a tracking event stream, and a projection computing current shipment status.
- **Concepts trained:** distributed state, duplicate command delivery, out-of-order events, projection rebuilds, capacity accounting under concurrency.
- **Difficulty:** 9/10. **Complexity:** roughly 44 source files, 7 entities.
- **Approximate defect load:** several, across both brokers and the database.

### 15 — Enterprise Order Processing Platform (`15-enterprise-order-platform`)
- **Domain:** the full system — authenticated multi-role ordering, pricing, inventory reservation, payment, fulfilment, notification, audit.
- **Stack:** Spring Boot, Spring Security with JWT, JPA/Hibernate, PostgreSQL, Redis, Kafka, RabbitMQ, REST, Maven, JUnit.
- **Architecture:** a modular monolith with internal boundaries: `identity`, `catalog`, `ordering`, `inventory`, `payment`, `fulfilment`, `notification`, `audit`. Synchronous REST at the edge, Kafka for domain events, RabbitMQ for work commands, Redis for cache and idempotency, PostgreSQL as the system of record.
- **Concepts trained:** everything above, simultaneously, with no map.
- **Difficulty:** 10/10. **Complexity:** 80 or more source files, 12 or more entities, 4 backing services.
- **Defect load:** **deliberately unstated.** Independent defects, interacting defects, and defects that only become reachable once an earlier one is fixed. The visible error is not reliably the root cause.

---

## 5. Build order and per-project procedure

Projects are built strictly in sequence. For each one:

1. Scaffold the Maven module and package structure.
2. Implement the complete, working feature set.
3. Author the schema and a realistic seed.
4. Introduce the defects, woven into plausible code.
5. `mvn -q clean compile` — must succeed.
6. Boot the application against the compose stack — must start.
7. Exercise the API and confirm each intended symptom actually reproduces.
8. Write `README.md` and `API.md`.
9. Write `DEBUGGING_GUIDE.md` — symptoms and graded hints, no causes.
10. Write `SOLUTION.md` — the sealed answer key.
11. Final pass: search for accidental giveaways such as `BUG`, `FIXME`, `TODO`, `XXX`, `HACK`, or telling identifiers.

---

## 6. How to use the lab

**Working a project**

1. Read only `README.md` and `DEBUGGING_GUIDE.md`. Do not open `SOLUTION.md`.
2. Start the required infrastructure, boot the app, seed the database.
3. Reproduce the symptom yourself before reading any hint. A defect you cannot reproduce
   is a defect you cannot verify you have fixed.
4. Form a hypothesis, then look for evidence: a log line, a SQL statement, a Redis key, a
   queue depth, a breakpoint proving the value is already wrong at that point.
5. Only then change code.

**Working with me while you debug**

- If you say *"I found a bug"*, I will not confirm it. I will ask for the symptom, your
  suspected root cause, your evidence, and your proposed fix, and then evaluate your
  reasoning.
- If you ask for a hint, you get exactly one more hint.
- If you ask for the exact bug, I name it.
- If you ask for the solution, I open the answer key and explain the underlying concept.
- When you believe a project is fixed, ask for **verification mode**: I will inspect your
  changes, run the app, exercise the API, attempt to reproduce the original symptom, judge
  whether you identified the real root cause, list what is still broken, and flag
  regressions. I will not rewrite your implementation unless you ask me to.

**A discipline worth keeping.** For each defect you solve, write down the symptom you saw,
the first three hypotheses you had, which observation eliminated each wrong one, and the
single piece of evidence that confirmed the right one. That log is the real product of
this lab. The fixes are disposable; the search strategy is not.
