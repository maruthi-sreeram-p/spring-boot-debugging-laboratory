# Ledgerline — Payment Processing Service

`payment-processing-service` takes card payments for merchant orders. It owns orders,
payments, the attempt history, and the reconciliation job that closes off payments whose
outcome was never learned.

**No real payment provider is involved.** The acquirer is simulated in-process by
`SimulatedPaymentGateway` — nothing leaves the JVM, no credentials exist, no network call is
made. It behaves the way a sandbox does: the amount decides the outcome.

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
| Persistence | Spring Data JPA, Hibernate 6, MySQL 8 |
| Resilience | Spring Retry (`@EnableRetry`), scheduled reconciliation |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.ledgerline.payments
├── config/        SecurityConfig, PaymentProperties
├── controller/    Order, Payment, PayOps
├── dto/           request and response payloads, PagedResponse, ApiError
├── entity/        Merchant, PaymentOrder, Payment, PaymentAttempt, AppUser + enums
├── exception/     domain exceptions + GlobalExceptionHandler
├── gateway/       SimulatedPaymentGateway, GatewayResult, GatewayCall, GatewayTimeoutException
├── mapper/        entity to DTO translation
├── repository/    Spring Data JPA repositories
├── scheduling/    ReconciliationJob
├── security/      PaymentsUser, PaymentsUserDetailsService
└── service/       OrderService, PaymentService
```

---

## The simulated acquirer

`SimulatedPaymentGateway` decides what happens from the **minor units of the amount**, the
same way a real sandbox uses magic card numbers:

| Amount ends in | Behaviour |
|---|---|
| `.11` | declined — "Insufficient funds" |
| `.22` | **the capture is taken, and then the response is lost** (throws `GatewayTimeoutException`) |
| `.33` | slow, then approved |
| anything else | approved |

The `.22` case is the important one. It models the single most awkward failure in payments:
the acquirer has your customer's money and you do not know it. Real acquirers do this
during incidents, and every payment system has to survive it.

The simulator records **every call that reached it**, including the ones whose response was
lost. That record is the settlement view — what the acquirer believes happened — and it is
what a real reconciliation would be run against:

```bash
curl -s -u 'payops@ledgerline.test:Admin123!' localhost:8080/api/payops/acquirer/calls
```

When your database and that list disagree, one of them is wrong, and it is never the
acquirer.

---

## How a payment is meant to work

```
POST /api/payments  (Idempotency-Key: <key>)
      │
      ├── has this key been used before? ──yes──► return the original payment, charge nothing
      │
      ├── create payment, status PROCESSING
      ├── call the acquirer
      │       approved ──► payment SUCCEEDED, order PAID
      │       declined ──► payment FAILED,    order PAYMENT_FAILED
      │       no answer ─► payment stays PROCESSING, order unchanged
      └── return the payment
```

An order only becomes `PAID` when money has actually been taken. A payment left in
`PROCESSING` is picked up later by reconciliation, which asks the acquirer what really
happened before deciding anything.

**Idempotency keys** are how a client retries safely. Replaying a request with a key that
has already been used must return the original outcome and must not contact the acquirer
again — whatever that outcome was.

---

## Running it

### 1. Backing services

```bash
docker compose -f infra/docker-compose.yml up -d mysql
```

MySQL on `localhost:3307` with a `paymentsdb` database.

### 2. Configuration

`application.yml` holds placeholders (`YOUR_DATABASE_*`) and reads every value from the
environment. The `local` profile — active by default — fills them in from
`infra/docker-compose.yml`.

Retry and reconciliation settings live under `payments:` — 3 attempts, 200 ms backoff, and
a payment is considered stale after 5 minutes in `PROCESSING`.

### 3. Start

```bash
mvn spring-boot:run
```

Schema and seed are applied on every start; both are idempotent.

### 4. Sign in

| Account | Password | Role |
|---|---|---|
| `merchant@northwind.test` | `Password123!` | `ROLE_MERCHANT` |
| `merchant@spicebox.test` | `Password123!` | `ROLE_MERCHANT` |
| `payops@ledgerline.test` | `Admin123!` | `ROLE_PAYOPS` — reconciliation and the acquirer view |

Seeded orders, chosen so every sandbox behaviour is reachable:

| Order | Amount | What it exercises |
|---|---|---|
| `ORD-5003` | 2450.00 | ordinary approval |
| `ORD-5004` | 399.00 | ordinary approval |
| `ORD-5005` | 50000.00 | a large order |
| `ORD-5006` | 760.11 | decline |
| `ORD-5007` | 1120.22 | captured, response lost |
| `ORD-5008` | 3300.00 | ordinary approval |

---

## Exercising it

```bash
BASE=http://localhost:8080
MER='merchant@northwind.test:Password123!'
OPS='payops@ledgerline.test:Admin123!'

curl -s -u "$OPS" -X POST "$BASE/api/payops/acquirer/reset"

curl -s -u "$MER" -X POST "$BASE/api/payments" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: chk-001' \
  -d '{"orderRef":"ORD-5008","amount":3300.00,"instrument":"CARD_VISA"}'

curl -s -u "$MER" "$BASE/api/orders/ORD-5008"
curl -s -u "$MER" "$BASE/api/orders/ORD-5008/payments"
curl -s -u "$OPS" "$BASE/api/payops/acquirer/calls"
curl -s -u "$OPS" -X POST "$BASE/api/payops/jobs/reconcile"
```

Full contracts are in [API.md](API.md).

---

## Inspecting state while you debug

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass paymentsdb \
  -e "select payment_ref, order_id, idempotency_key, amount, status, attempts, gateway_ref from payments order by id;"

docker exec -it lab-mysql mysql -ulabuser -plabpass paymentsdb \
  -e "select order_ref, amount, status from orders order by id;"
```

**The three questions worth asking after every experiment**, in this order:

1. What does the acquirer say? `GET /api/payops/acquirer/calls`
2. What do our payments say? `select * from payments`
3. What do our orders say? `select * from orders`

Every defect in this project is a disagreement between two of those three.

Orders that claim to be paid without a successful payment behind them:

```sql
SELECT o.order_ref, o.amount, o.status
  FROM orders o
 WHERE o.status = 'PAID'
   AND NOT EXISTS (SELECT 1 FROM payments p
                    WHERE p.order_id = o.id AND p.status = 'SUCCEEDED');
```

Orders paid for less than they are worth:

```sql
SELECT o.order_ref, o.amount AS owed, SUM(p.amount) AS taken
  FROM orders o JOIN payments p ON p.order_id = o.id AND p.status = 'SUCCEEDED'
 GROUP BY o.id, o.order_ref, o.amount
HAVING SUM(p.amount) <> o.amount;
```

Idempotency keys used more than once:

```sql
SELECT idempotency_key, COUNT(*) FROM payments GROUP BY idempotency_key HAVING COUNT(*) > 1;
```

Retries and transaction boundaries are worth logging when you get to them:

```
logging.level.org.springframework.retry=DEBUG
logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```

---

## Tests

```bash
mvn test
```

The suite covers the simulator's own behaviour and a repository slice against H2. It
passes — and note that it tests the acquirer, not the code that talks to it.
