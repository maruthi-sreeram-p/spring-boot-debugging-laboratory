# Realistic Java Backend Debugging Laboratory

Fifteen Spring Boot backend applications that look like real company code and behave like
real company code — including the part where they are subtly broken.

The goal is not to write more Java. The goal is that when you meet a genuine Spring Boot
defect at work, your first move is **"let me reproduce it and trace the flow"** rather than
**"let me ask an AI what is wrong"**.

---

## Start here

1. Read [`REALISTIC_DEBUGGING_LAB_PLAN.md`](REALISTIC_DEBUGGING_LAB_PLAN.md) — what each
   project is, what it trains, how hard it is.
2. Start the backing services you need (below).
3. Pick project `01` and open its `README.md` and `DEBUGGING_GUIDE.md`.
4. Do **not** open `SOLUTION.md`.

---

## Backing services

Everything the lab needs is in one compose file. Host ports are shifted where they would
otherwise collide with a locally installed server.

| Service | Host port | Credentials |
|---|---|---|
| MySQL 8 | `3307` | `labuser` / `labpass`, root `labroot` |
| PostgreSQL 16 | `5432` | `labuser` / `labpass` |
| Redis 7 | `6380` | none |
| Kafka (KRaft) | `9092` | none |
| RabbitMQ | `5672`, UI `15672` | `labuser` / `labpass` |
| Adminer (DB browser) | `8081` | — |

Start only what a project needs:

```bash
docker compose -f infra/docker-compose.yml up -d mysql redis
```

```bash
docker compose -f infra/docker-compose.yml down
```

Override any host port with the `*_HOST_PORT` variables, e.g. `MYSQL_HOST_PORT=3306`.

Every application reads its credentials from the environment and ships placeholder defaults
(`YOUR_DATABASE_PASSWORD`, `YOUR_REDIS_HOST`, `YOUR_KAFKA_BROKER`). A `local` Spring profile
— active by default — fills them in with the compose values so projects run out of the box.

---

## Projects

| # | Project | Stack highlights | Difficulty |
|---|---|---|---|
| 01 | [E-Commerce Order Management](01-ecommerce-order-management) | MySQL, Redis, Spring Security | 2/10 |
| 02 | [Banking / Account Management](02-banking-account-management) | PostgreSQL, transactions, locking | 3/10 |
| 03 | [Employee Management](03-employee-management) | MySQL, JPA relationships, pagination | 3/10 |
| 04 | [Hospital Appointments](04-hospital-appointments) | MySQL, scheduling, time handling | 5/10 |
| 05 | [Inventory Management](05-inventory-management) | PostgreSQL, Redis, consistency | 5/10 |
| 06 | [Food Ordering](06-food-ordering) | MySQL, RabbitMQ, async | 6/10 |
| 07 | [Job Portal](07-job-portal) | MySQL, Criteria API, ownership rules | 6/10 |
| 08 | [Payment Processing Simulation](08-payment-processing) | MySQL, retries, idempotency | 7/10 |
| 09 | [Notification Service](09-notification-service) | PostgreSQL, RabbitMQ topology, DLQ | 7/10 |
| 10 | [Order Event Processing](10-order-event-processing) | PostgreSQL, Kafka, offsets | 8/10 |
| 11 | [Product Catalog + Redis](11-product-catalog-redis) | PostgreSQL, Spring Cache, key design, serialization | 8/10 |
| 12 | [Auth & Authorization Platform](12-auth-platform) | PostgreSQL, Redis, JWT, Spring Security 6 | 8/10 |
| 13 | [Library / Digital Lending](13-library-lending) | MySQL, concurrency, locking, transactions | 9/10 |
| 14 | Logistics / Delivery | PostgreSQL, Kafka + RabbitMQ | 9/10 |
| 15 | Enterprise Order Platform | everything, at once | 10/10 |

Each project directory contains:

```
README.md            what it is, how to run it, how to inspect its state
API.md               endpoint contracts: request, response, status, auth
DEBUGGING_GUIDE.md   business context, expected behaviour, symptoms, graded hints
SOLUTION.md          sealed answer key
```

---

## Working method

**Reproduce before you read code.** A defect you cannot reproduce is a defect you cannot
verify you fixed.

**Observe before you hypothesise.** The database, the Redis keyspace, the queue depth and
the SQL log are facts. The HTTP response body is a claim.

**Change one thing at a time.** Some of these defects hide others; a fix that makes things
worse is information, not failure.

**Keep a log.** For each defect: the symptom, your first three hypotheses, the observation
that killed each wrong one, and the evidence that confirmed the right one. That log is the
actual output of this lab.

---

## Working with me

| You say | I do |
|---|---|
| "I found a bug." | Ask you for symptom, suspected root cause, evidence and proposed fix — then evaluate your reasoning. I will not confirm it up front. |
| "Give me a hint." | One hint. The next one only if you ask again. |
| "Tell me the exact bug." | Name it. |
| "Give me the solution." | Open the answer key and explain the underlying concept. |
| "Verify my fix." | Inspect your changes, run the app, exercise the API, try to reproduce the original symptom, judge whether you found the real root cause, list what is still broken, and flag regressions. I will not rewrite your implementation unless you ask. |

---

## Constraints this lab respects

Nothing is committed or pushed. Nothing is deployed. No real payment provider is contacted
— project 08 simulates one in-process. No API keys are obtained and no credentials are
embedded; every secret is a placeholder you fill in yourself.
