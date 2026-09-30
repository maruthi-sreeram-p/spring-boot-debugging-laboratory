# 13 — Athenaeum Circulation Service

The lending system for a four-branch city library. It holds the catalogue, tracks every physical
copy, issues and takes back loans, runs a hold queue, and charges for books that come back late.

**Difficulty:** 9 / 10 · **Theme:** lost updates, read-modify-write races, pessimistic versus
optimistic locking, transaction propagation, date arithmetic

---

## Stack

| Concern      | Choice |
| ------------ | ------ |
| Runtime      | Java 21, Spring Boot 3.3.5 |
| Persistence  | Spring Data JPA / Hibernate 6 on MySQL 8 (InnoDB, REPEATABLE READ) |
| Security     | Spring Security 6, HTTP Basic, BCrypt, stateless |
| Scheduling   | Spring `@Scheduled` for the overnight fine accrual |
| Build / test | Maven, JUnit 5, AssertJ |
| Load         | a bundled Python script for concurrent requests |

Seven entities: `books`, `book_copies`, `members`, `loans`, `reservations`, `fines`, `app_users`.

---

## The circulation rules

These are the library's published rules. The service is supposed to implement them.

| Tier | Loan length | Books at once |
| ---- | ----------- | ------------- |
| Standard | 14 days | 4 |
| Premium | 21 days | 8 |
| Staff | 28 days | 12 |

- A loan may be renewed at most twice, and not at all while somebody is waiting for the title.
- **A charge of 5.00 accrues for each day a book is late**, capped at 500.00. A book that comes
  back the day after it was due is one day late.
- Borrowing is blocked above 100.00 of outstanding charges.
- A hold placed on a title that is entirely out joins a queue. When a copy comes back, the member
  at the head of the queue has it **held for them for 48 hours** on the hold shelf.
- A copy that is lost or away for repair is not borrowable.

---

## Running it

```bash
docker compose -f ../infra/docker-compose.yml up -d mysql
```

Then, from this directory:

```bash
mvn -B -DskipTests package && java -jar target/lending-service-13.2.9.jar --spring.profiles.active=local
```

The `local` profile points at `localhost:3307` (the lab MySQL, database `librarydb`).
`application.yml` carries placeholders — `YOUR_DATABASE_HOST`, `YOUR_DATABASE_USER`,
`YOUR_DATABASE_PASSWORD` — so set those, or the `MYSQL_*` environment variables, to run it
elsewhere.

`schema.sql` and `data.sql` are applied on every boot and are idempotent. Loan dates in the seed
are relative to boot time, so the overdue examples stay overdue.

### Accounts

| Username | Password | Role |
| -------- | -------- | ---- |
| `librarian@athenaeum.test` | `Admin123!` | `ROLE_LIBRARIAN` |
| `anita.desai@athenaeum.test` | `Password123!` | `ROLE_MEMBER` |
| `rahul.verma@athenaeum.test` | `Password123!` | `ROLE_MEMBER` |
| `daniel.fernandes@athenaeum.test` | `Password123!` | `ROLE_MEMBER` |

The catalogue (`GET /api/catalogue/**`) is public. `/api/circulation/**` needs either role.
`/api/admin/**` needs `ROLE_LIBRARIAN`.

---

## Inspecting state

The database is the source of truth here, and most of this project is settled by SQL.

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass librarydb
```

Queries worth keeping to hand:

```sql
SELECT copy_id, COUNT(*) FROM loans WHERE status='ACTIVE' GROUP BY copy_id HAVING COUNT(*) > 1;
```

```sql
SELECT m.id, m.membership_number, m.active_loan_count AS cached, COUNT(l.id) AS actual
FROM members m LEFT JOIN loans l ON l.member_id = m.id AND l.status = 'ACTIVE'
GROUP BY m.id, m.membership_number, m.active_loan_count;
```

```sql
SELECT id, copy_id, due_at, returned_at, TIMESTAMPDIFF(HOUR, due_at, NOW()) AS hours_late FROM loans;
```

InnoDB will also tell you about contention directly:

```sql
SHOW ENGINE INNODB STATUS\G
```

```sql
SELECT * FROM performance_schema.data_locks;
```

`org.hibernate.SQL` is at `DEBUG`, so `target/app.log` contains every statement in the order it
was sent, which matters more here than in most projects.

### Concurrency

The desk serves one person at a time. The self-service kiosks and the mobile app do not. The
bundled script reproduces that:

```bash
pip install requests
```

```bash
python scripts/concurrent_borrows.py --book 1 --members 1,2,3,4,6,7
```

```bash
python scripts/concurrent_borrows.py --book 1 --members 4 --repeat 6
```

Run each against a freshly reset database, and check the tables afterwards rather than trusting
the HTTP responses.

To reset:

```bash
docker exec lab-mysql mysql -ulabuser -plabpass -e "DROP DATABASE librarydb; CREATE DATABASE librarydb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### The fine accrual

The nightly job runs at 02:00. `POST /api/admin/fines/accrual` runs it on demand, and
`?asOf=2026-09-19T05:56:37` prices it as of any moment you choose — which is the only sane way to
investigate anything that depends on the clock.

---

## Where to start

[`API.md`](API.md), then [`DEBUGGING_GUIDE.md`](DEBUGGING_GUIDE.md).

`SOLUTION.md` is the answer key.

---

## Tests

```bash
mvn -B test
```

They pass. Nothing in this project is the kind of thing a unit test sees.
