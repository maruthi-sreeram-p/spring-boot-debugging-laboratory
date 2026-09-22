# Meridian Bank — Core Banking Service

`core-banking-service` is the account and ledger backend for Meridian Bank. It owns
customers, accounts, balances and the transaction ledger, and it serves both the customer
mobile app and the branch back-office tool.

This is a **debugging lab project**. The application is feature-complete and starts
cleanly, but it does not behave correctly in every case. Work from `DEBUGGING_GUIDE.md`.
Do not open `SOLUTION.md` until you have finished.

---

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| Web | Spring MVC, REST/JSON |
| Security | Spring Security, HTTP Basic, BCrypt, stateless |
| Persistence | Spring Data JPA, Hibernate 6, PostgreSQL 16 |
| Money | `BigDecimal`, `NUMERIC(18,2)`, everything in UTC |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.meridian.banking
├── config/        SecurityConfig, BankingProperties
├── controller/    Auth, Account, MoneyMovement, BackOffice
├── dto/           request and response payloads, PagedResponse, ApiError
├── entity/        Customer, Role, Account, AccountTransaction + enums
├── exception/     domain exceptions + GlobalExceptionHandler
├── mapper/        entity to DTO translation
├── repository/    Spring Data JPA repositories
├── security/      AuthenticatedCustomer, CustomerDetailsService
└── service/       CustomerService, AccountService, LedgerService,
                   AccountTransactionService, TransferService, StatementService
```

**Ledger model.** `accounts.balance` is the authoritative balance. Every movement also
writes a `transactions` row carrying the amount and the resulting `balance_after`, so a
statement can be reconciled against the balance without recomputation. A transfer writes
two rows — a `TRANSFER_OUT` leg on the sender and a `TRANSFER_IN` leg on the recipient —
sharing one reference.

---

## Running it

### 1. Backing services

From the repository root:

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

PostgreSQL comes up on `localhost:5432` with a `bankdb` database.

### 2. Configuration

`src/main/resources/application.yml` holds placeholders (`YOUR_DATABASE_HOST`,
`YOUR_DATABASE_USER`, `YOUR_DATABASE_PASSWORD`) and reads every value from the
environment. The `local` profile — active by default — fills them in with the credentials
from `infra/docker-compose.yml`.

To point the service at your own database:

```bash
export SPRING_PROFILES_ACTIVE=default
export POSTGRES_HOST=... POSTGRES_USER=... POSTGRES_PASSWORD=...
```

Business rules live under the `banking:` key — the daily transfer limit is `50000.00` and
the minimum opening balance is `0.00`.

### 3. Start

```bash
mvn spring-boot:run
```

Schema and seed data are applied on every start from `schema.sql` and `data.sql`. Both are
idempotent (`ON CONFLICT DO NOTHING` plus a `setval` pass), so restarting never duplicates
rows and never wipes anything you changed.

### 4. Sign in

| Account | Password | Role |
|---|---|---|
| `devika.rao@example.com` | `Password123!` | customer — accounts `MB-0000-1001` (checking) and `MB-0000-1002` (savings) |
| `martin.oduor@example.com` | `Password123!` | customer — `MB-0000-1003`, plus a closed `MB-0000-1006` |
| `yuki.tanabe@example.com` | `Password123!` | customer — `MB-0000-1004` (INR) and `MB-0000-1005` (USD) |
| `teller.desk@meridian.test` | `Admin123!` | branch teller (`ROLE_TELLER`) |

Authentication is HTTP Basic on every call.

---

## Exercising it

```bash
BASE=http://localhost:8080
AUTH='devika.rao@example.com:Password123!'

curl -s -u "$AUTH" "$BASE/api/accounts"

curl -s -u "$AUTH" -X POST "$BASE/api/accounts/1/deposits" \
  -H 'Content-Type: application/json' \
  -d '{"amount": 2500.00, "description": "Cash deposit"}'

curl -s -u "$AUTH" -X POST "$BASE/api/transfers" \
  -H 'Content-Type: application/json' \
  -d '{"fromAccountNumber":"MB-0000-1001","toAccountNumber":"MB-0000-1003","amount":1200.00,"description":"Rent share"}'

curl -s -u "$AUTH" "$BASE/api/accounts/1/transactions?from=2024-09-01&to=2024-12-31"
```

Full endpoint contracts are in [API.md](API.md).

### Load script

`scripts/concurrent_deposits.py` fires a burst of simultaneous deposits at one account and
compares the resulting balance against the deposits the API accepted:

```bash
python scripts/concurrent_deposits.py --account 1 --count 50 --amount 100.00
```

This mirrors how the account behaves in production, where the teller UI retries, mobile
clients double-submit and the batch importer runs several workers.

---

## Inspecting state while you debug

**PostgreSQL** — Adminer is on <http://localhost:8081> (system PostgreSQL, server
`postgres`, user `labuser`, database `bankdb`), or from the shell:

```bash
docker exec -it lab-postgres psql -U labuser -d bankdb \
  -c "select id, account_number, balance, status from accounts order by id;"

docker exec -it lab-postgres psql -U labuser -d bankdb \
  -c "select id, reference, tx_type, amount, balance_after, created_at from transactions order by id;"
```

A useful reconciliation query — the ledger and the balance should agree:

```sql
SELECT a.account_number,
       a.balance,
       (SELECT t.balance_after
          FROM transactions t
         WHERE t.account_id = a.id
         ORDER BY t.created_at DESC, t.id DESC
         LIMIT 1) AS last_ledger_balance
  FROM accounts a
 ORDER BY a.id;
```

**Transaction boundaries** — to see where Spring opens and commits transactions:

```
logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
logging.level.org.springframework.transaction.interceptor=TRACE
```

**SQL** is already logged at `DEBUG` for `org.hibernate.SQL`. For bound parameters add
`logging.level.org.hibernate.orm.jdbc.bind=TRACE`.

---

## Tests

```bash
mvn test
```

The suite covers account and transaction mapping and a repository slice against H2. It
passes. That tells you the build is sound; it does not tell you the money is safe.
