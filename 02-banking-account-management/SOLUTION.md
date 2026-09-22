# SOLUTION — Meridian Bank Core Banking Service

> **Answer key. Do not read this until you have finished investigating.**

Five defects. Four are deterministic; one needs concurrent traffic to show itself.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| B1 | BANK-812 | The credit leg runs in `REQUIRES_NEW`, so it survives a rollback of the transfer | `service/LedgerService.java` + `service/TransferService.java` |
| B2 | BANK-817 | Read-modify-write on `balance` with no locking and no `@Version` — lost updates | `service/AccountTransactionService.java` + `entity/Account.java` |
| B3 | BANK-836 | `TransferRequest.amount` has no lower bound, so a negative transfer reverses direction | `dto/TransferRequest.java` |
| B4 | BANK-830 | The `to` date is converted to the *start* of that day, excluding it | `service/StatementService.java` |
| B5 | BANK-823 | `freezeAccount` is `@Transactional(readOnly = true)`, so the change is never flushed | `service/AccountService.java` |

---

## B1 — A rejected transfer still credits the recipient

### Symptom
`POST /api/transfers` for an amount over the daily limit returns `422 Daily transfer limit
exceeded`. The sender's balance is untouched, which is correct. But the **recipient's
balance has increased by the full amount**, and there are no `transactions` rows at all.
Money is created out of nothing, and the ledger cannot explain it.

Reproduce:

```bash
curl -s -u 'devika.rao@example.com:Password123!' -X POST localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -d '{"fromAccountNumber":"MB-0000-1001","toAccountNumber":"MB-0000-1003","amount":60000.00}'
# HTTP 422, and MB-0000-1003 is now 60000.00 richer
```

### Root cause
`TransferService.transfer` is `@Transactional`, and almost everything it does is inside
that transaction: the debit, both ledger rows, and the limit check. But the credit leg is
delegated:

```java
BigDecimal targetBalance = ledgerService.creditAccount(target.getId(), amount);
```

and `LedgerService.creditAccount` is annotated:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public BigDecimal creditAccount(Long accountId, BigDecimal amount) { ... }
```

`REQUIRES_NEW` **suspends** the caller's transaction, runs the body in a brand new
transaction on a second connection, and **commits it immediately**. That commit is
independent and permanent. When the outer transaction subsequently rolls back, it rolls
back only its own work — the debit and the two ledger rows. The credit is already durable
and there is nothing left that knows it should be undone.

The trigger is `enforceDailyTransferLimit`, which runs *after* both legs are booked. That
ordering is deliberate and is documented in the method: the limit is computed by summing
today's `TRANSFER_OUT` rows, so the row for the transfer in flight must already be visible
to the query. Running it last is defensible. Committing half the money before it runs is
not.

Any other failure after the credit — an optimistic lock failure, a constraint violation, a
connection drop, the JVM being killed — produces the same orphaned credit. The daily limit
is just the reproducible one.

### Exact location
`src/main/java/com/meridian/banking/service/LedgerService.java` — the
`Propagation.REQUIRES_NEW` on `creditAccount`, combined with the call site in
`src/main/java/com/meridian/banking/service/TransferService.java`.

### Correct fix
Both legs of a transfer belong in one transaction. Remove the propagation override so the
credit joins the caller's transaction:

```java
@Transactional          // REQUIRED is the default: joins the transfer transaction
public BigDecimal creditAccount(Long accountId, BigDecimal amount) { ... }
```

Then decide what to do about the ordering of the limit check. Two defensible options:

1. **Keep the check last.** Now that the credit is in the same transaction, a rollback
   correctly undoes everything. This is the minimal fix and it is correct.
2. **Check first, without relying on the in-flight row.** Sum today's existing
   `TRANSFER_OUT` rows *plus* the requested amount, and reject before touching any balance:

   ```java
   BigDecimal movedToday = transactionRepository.sumByTypeSince(...);
   if (movedToday.add(amount).compareTo(limit) > 0) { throw ...; }
   ```

   This is better engineering — it avoids doing work that will be thrown away, and it makes
   the guard independent of flush timing — but note it changes nothing about the atomicity
   bug. Fixing the ordering *without* fixing the propagation would leave B1 alive for every
   other failure mode.

Also worth doing while you are here: `creditAccount` reads and writes a balance with no
lock, which is B2 on the credit side.

### Affected components
`TransferService`, `LedgerService`, `AccountRepository`, `TransactionRepository`, Spring's
`JpaTransactionManager`, and the correctness of the entire ledger.

### Underlying concept
**Transaction propagation decides what "rollback" means.** `REQUIRES_NEW` is not "extra
safety" — it is an explicit statement that this work must survive independently of the
caller. That is occasionally what you want (writing an audit record that must persist even
when the business operation fails), and it is exactly wrong for one half of a two-sided
money movement.

The deeper rule: **a business invariant can only be enforced inside a single transaction.**
"Debits equal credits" is an invariant. The moment the two legs commit separately you have
a distributed transaction, and you need compensation, outbox or saga machinery to make it
safe — none of which is here, and none of which is warranted when both rows live in the
same database.

### Why this is realistic
Read the Javadoc on `LedgerService`: *"the credit leg runs in its own transaction so that an
incoming payment is durable as soon as it has been applied."* That sentence sounds
responsible. Somebody was probably reasoning about a retry scenario — "if the transfer is
retried, I do not want the recipient to lose the credit" — and reached for the strongest
persistence guarantee available.

`REQUIRES_NEW` is one of the most commonly misused settings in Spring. It is frequently
added to fix a specific symptom (usually a rollback that was undoing something the author
wanted kept) without anyone tracing what else flows through that method. And it is
invisible in review: the annotation is on `LedgerService`, the transaction boundary is on
`TransferService`, and the two files are rarely opened together.

### Detecting it faster next time
- **Transaction logging is the fastest possible diagnosis here.** With
  `org.springframework.orm.jpa.JpaTransactionManager=DEBUG` the log literally prints
  `Suspending current transaction, creating new transaction` followed by
  `Initiating transaction commit`, and later `Initiating transaction rollback` for the
  outer one. Two commits and one rollback for one HTTP request tells you the whole story
  before you have read any code.
- When state is inconsistent after a failure, ask **"which writes were in the transaction
  that rolled back?"** rather than "why did it fail". The failure is usually correct; the
  scope is what is wrong.
- Grep the codebase for `REQUIRES_NEW` and `NOT_SUPPORTED` whenever you meet a partial
  write. There are rarely many occurrences and each one deserves justification.

### Prevention
- Treat every `Propagation` other than the default as requiring a comment that explains
  what must survive an outer rollback, and a test that proves it.
- An integration test that forces a failure after the credit and asserts **both** balances
  are unchanged. Three lines of assertion, permanently protective.
- A reconciliation job that alerts when `accounts.balance` disagrees with the newest
  `balance_after`. In a real bank this is mandatory, and it would have caught B1 on day one
  rather than at month end.

---

## B2 — Concurrent deposits are lost

### Symptom
Under simultaneous deposits every request returns `201` with its own reference and its own
`balanceAfter`, but the final balance is far short of the sum. Sequential deposits are
always correct.

```
requests      60 sent, 60 accepted, 0 rejected
balance after  90150.00
expected       95250.00
difference     -5100.00
```

### Root cause
`AccountTransactionService.deposit` does a classic read-modify-write:

```java
Account account = loadOperableAccount(customerId, accountId);   // SELECT balance
account.setBalance(account.getBalance().add(amount));           // compute in Java
accountRepository.save(account);                                // UPDATE ... SET balance = <absolute value>
```

The `UPDATE` writes an **absolute** value computed from a balance that was read earlier.
Two concurrent requests both read 89250.00, both compute 89350.00, and both write it. The
second write blocks on the row lock until the first commits and then overwrites it with a
value derived from a stale read. One deposit is gone. Nothing errors, because from the
database's point of view both statements were perfectly legal.

`Account` has no `@Version` column, so JPA has no way to detect that the row changed
between the read and the write. PostgreSQL's default `READ COMMITTED` isolation does not
prevent this either — lost update is precisely the anomaly that `READ COMMITTED` permits.

`withdraw` has the same shape, and so does `LedgerService.creditAccount`. On the withdrawal
side it is worse than lost money: two concurrent withdrawals can each see sufficient funds
and both succeed, overdrawing the account.

### Exact location
`src/main/java/com/meridian/banking/service/AccountTransactionService.java` (`deposit` and
`withdraw`), `src/main/java/com/meridian/banking/service/LedgerService.java`
(`creditAccount`), and the missing version field in
`src/main/java/com/meridian/banking/entity/Account.java`.

### Correct fix
Three legitimate strategies. Pick one deliberately.

**Optimistic locking** — add a version column and let Hibernate detect the conflict:

```java
@Version
@Column(name = "version", nullable = false)
private long version;
```

plus `ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;` in `schema.sql`.
A conflicting write now throws `ObjectOptimisticLockingFailureException`, which you must
handle — either retry the operation (`@Retryable`, or a short retry loop around the whole
transaction) or return `409` and let the caller retry. **Optimistic locking without a retry
policy converts lost updates into user-visible failures**, which is better but not good.

**Pessimistic locking** — serialise the readers:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Account a where a.id = :id")
Optional<Account> findByIdForUpdate(@Param("id") Long id);
```

This issues `SELECT ... FOR UPDATE`, so the second transaction blocks at the *read*, not at
the write, and computes from a fresh value. Simple and correct for a hot single row. Beware
lock ordering in transfers: always lock the two accounts in a deterministic order (by id)
or two opposing transfers will deadlock.

**Atomic relative update** — let the database do the arithmetic:

```java
@Modifying
@Query("update Account a set a.balance = a.balance + :delta where a.id = :id")
int adjustBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);
```

No lost update is possible because no value is read into the application. This is the
cheapest and most scalable option, but it needs care: you still have to read the resulting
balance for `balance_after`, and the overdraft check must become part of the statement
(`... and a.balance + :delta >= 0`, then treat a zero row count as insufficient funds).

For this codebase, pessimistic locking on the account rows is the smallest correct change
that also fixes withdrawals and transfers.

### Affected components
`AccountTransactionService`, `LedgerService`, `TransferService`, `Account`,
`AccountRepository`, `schema.sql`, and the transaction isolation behaviour of PostgreSQL.

### Underlying concept
**A read-modify-write across a transaction boundary is not atomic unless you make it so.**
The three steps are separated in time; anything can happen in between. `READ COMMITTED` —
the default in PostgreSQL, MySQL/InnoDB uses `REPEATABLE READ` — guarantees you never read
uncommitted data, but says nothing about whether what you read is still true when you write.

The second concept is that **absolute writes and relative writes behave differently under
concurrency.** `SET balance = 89350.00` destroys concurrent work; `SET balance = balance +
100.00` composes with it. When a value is a running total, prefer the relative form or
protect the read.

### Why this is realistic
This is the single most common concurrency defect in business applications, and it is
invisible in every normal development activity: it passes code review (the code is clear
and obviously correct when read top to bottom), passes unit tests, passes integration
tests, and passes manual QA. It requires simultaneous traffic on the same row, which is
exactly what production has and a laptop does not.

It is also genuinely easy to miss because nothing fails. There is no exception, no log
line, no failed request. The only evidence is arithmetic that does not add up, discovered
hours later by someone in a different team.

### Detecting it faster next time
- **"Cannot reproduce one at a time" is a concurrency signature.** When a ticket says the
  problem only happens during a busy period, or only when someone double-clicks, stop
  trying to reproduce it sequentially and write the concurrent reproduction first. That is
  what `scripts/concurrent_deposits.py` is for, and writing one takes ten minutes.
- Learn to spot the shape in review: load an entity, compute a new value from one of its
  fields, save. If that field is shared, it is a lost update waiting for traffic.
- Ask of any counter or balance: *what happens if two requests do this at the same time?*
  If the answer requires reasoning about timing, add a lock or make the update relative.

### Prevention
- `@Version` on every entity whose fields are updated by concurrent requests. It costs one
  column and turns silent corruption into a loud, handleable exception.
- A concurrency test in CI for each hot row — fire N parallel requests, assert the total.
- Reconciliation and alerting, so that if one does slip through you find out in minutes.

---

## B3 — A negative transfer runs backwards

### Symptom
`POST /api/transfers` with `"amount": -5000.00` returns `201`. The sender's balance goes
**up** by 5000 and the recipient's goes **down** by 5000. Both ledger rows are written and
share a reference, so the ledger reconciles perfectly — it just records a transfer that ran
in the wrong direction. Any authenticated customer can drain any account whose number they
know.

The same value sent to `POST /api/accounts/{id}/deposits` is correctly rejected with `400`.

### Root cause
`AmountRequest`, used by deposits and withdrawals, constrains the amount:

```java
@NotNull
@DecimalMin(value = "0.01", message = "must be at least 0.01")
@Digits(integer = 16, fraction = 2)
private BigDecimal amount;
```

`TransferRequest` does not:

```java
@NotNull
@Digits(integer = 16, fraction = 2)
private BigDecimal amount;
```

`@Valid` **is** present on the controller parameter, so validation runs — there is simply
no lower bound to enforce. The arithmetic then does what it is told:

- `source.setBalance(balance.subtract(-5000))` → the source gains 5000
- `creditAccount(target, -5000)` → the target loses 5000

None of the guards catch it. The funds check `balance.compareTo(-5000) < 0` is false. The
daily limit sums a negative number and stays under the limit — worse, a negative transfer
*reduces* today's running total, so it can be used to reset the limit.

### Exact location
`src/main/java/com/meridian/banking/dto/TransferRequest.java`, the `amount` field.

### Correct fix
```java
@NotNull
@DecimalMin(value = "0.01", message = "must be at least 0.01")
@Digits(integer = 16, fraction = 2)
private BigDecimal amount;
```

Then add a second line of defence in `TransferService`, because a DTO constraint only
protects the one door it is attached to:

```java
if (amount.compareTo(BigDecimal.ZERO) <= 0) {
    throw new IllegalArgumentException("Transfer amount must be positive");
}
```

The same guard belongs in `LedgerService.creditAccount` and in the debit path. A ledger
primitive that accepts a negative credit is a hazard regardless of who calls it today.

### Affected components
`TransferRequest`, `MoneyMovementController`, `TransferService`, `LedgerService`, the
`accounts` and `transactions` tables, and the daily-limit control.

### Underlying concept
**Validation lives per-endpoint, but invariants live in the domain.** Three request objects
carry an amount; two of them bound it and one does not. That is the normal fate of
validation expressed only in DTOs — it drifts as endpoints are added.

The security framing matters too. This is not merely a data-quality bug: it is a
**privilege escalation through arithmetic**. The authorization logic is correct — the
service carefully checks that the *source* account belongs to the caller — but a negative
amount inverts the meaning of "source", so the ownership check ends up protecting the
attacker's own account rather than the victim's. Whenever a sign, an index or a direction
is attacker-controlled, re-read your access checks with the inverted value in mind.

### Why this is realistic
`AmountRequest` and `TransferRequest` were written at different times. The author of the
second one copied the shape of the fields, kept `@NotNull` and `@Digits` (which are about
format, and are the ones you notice), and dropped `@DecimalMin` (which is about business
rules, and is the one you do not). Nothing in the diff looks wrong, and every test in the
suite uses positive amounts because that is what a transfer obviously is.

This exact bug has appeared in real payment systems, real expense tools and real loyalty
point systems. "Negative quantity" and "negative amount" are the first two things a
security tester tries.

### Detecting it faster next time
- The ticket contains the diagnosis: *"her row says `TRANSFER_IN` but her balance went
  down."* When a direction is inverted, look for a sign, not for a permission.
- Compare sibling DTOs field by field. Constraint drift between near-identical request
  objects is extremely common and takes seconds to check.
- For every numeric input, test `0`, `-1`, and something enormous. Three requests per
  endpoint.

### Prevention
- A shared `@PositiveMoney` composed constraint, or a `Money` value object whose
  constructor refuses non-positive values, so no DTO can express the illegal state.
- Enforce invariants at the lowest layer that can enforce them — the ledger primitive —
  rather than only at the edge.
- An automated check that every `BigDecimal` amount field in a request DTO carries a lower
  bound.

---

## B4 — Statements exclude the final day

### Symptom
`GET /api/accounts/1/transactions?from=2024-09-01&to=2024-11-02` omits the withdrawal
booked on 2024-11-02 at 15:20 UTC. Asking for `to=2024-11-03` includes it. A monthly
statement therefore silently loses the last day of every month, and since the next month's
statement starts on the 1st, those transactions appear on no statement at all.

### Root cause
`StatementService.statement` converts both bounds the same way:

```java
Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
Instant toInstant   = to.atStartOfDay(ZoneOffset.UTC).toInstant();
```

`to.atStartOfDay()` is `2024-11-02T00:00:00Z`. The query is
`createdAt between fromInstant and toInstant`, so it includes only transactions booked at
exactly midnight on the final day, and excludes the other 86 399 seconds of it.

`from` is correct — the start of the day *is* the right instant for an inclusive lower
bound. The two ends need different treatment, and they got the same treatment.

### Exact location
`src/main/java/com/meridian/banking/service/StatementService.java`, the `toInstant`
assignment.

### Correct fix
Use a half-open interval, which is the standard way to express an inclusive day range:

```java
Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
Instant toInstant   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
```

and switch the repository method from `Between` (inclusive on both sides) to
`GreaterThanEqual` / `LessThan`:

```java
Page<AccountTransaction> findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        Long accountId, Instant from, Instant toExclusive, Pageable pageable);
```

Avoid the tempting `to.atTime(23, 59, 59)`: it silently drops anything in the final second,
and `23:59:59.999999` merely makes the gap smaller rather than removing it. Half-open is
exact at any precision.

While you are here, note that `from` and `to` are interpreted in UTC. That is correct for
this service because the ledger is UTC, but it means a customer in Mumbai asking for
"November" gets 05:30 to 05:30. Whether that is right is a product decision, and it should
be written down either way.

### Affected components
`StatementService`, `TransactionRepository`, `AccountController`, and every statement,
export and reconciliation that uses the date filter.

### Underlying concept
**A date is an interval, not an instant.** Converting a `LocalDate` to an `Instant` forces
you to pick a point inside that interval, and the correct point depends on which end of the
range you are converting. The reliable pattern is the **half-open interval**: `[start of
from, start of to + 1 day)`. It has no rounding edge, no precision assumption, and composes
cleanly — consecutive ranges tile the timeline without gaps or overlaps, which is exactly
what monthly statements require.

`BETWEEN` in SQL is inclusive on both ends, which makes it a poor fit for timestamps. It is
fine for dates and integers and a trap for anything with a time component.

### Why this is realistic
The two lines are adjacent and symmetric. Symmetry is what makes the bug invisible: the
code *looks* balanced, and balanced code reads as correct. Seed data in development almost
never has a transaction on the last day of the queried range, so it passes every casual
test. It surfaces in production on the first month-end.

### Detecting it faster next time
- The ticket handed you the boundary: it works with `to=2024-12-01` and fails with
  `to=2024-11-30`. **An off-by-one in the output means an off-by-one in the input.** Go
  straight to the conversion.
- Log or breakpoint the two `Instant` values actually passed to the query. Seeing
  `2024-11-02T00:00:00Z` as the *upper* bound ends the investigation immediately.
- Whenever you see a date range, check both ends against a record placed deliberately on
  each boundary.

### Prevention
- One shared utility for date-range conversion, e.g. `DateRange.ofInclusiveDays(from, to)`
  returning a half-open `Instant` pair, used everywhere.
- Tests that place a record at `to 00:00:00`, at `to 23:59:59.999` and at `to + 1 day
  00:00:00`, and assert which are included.
- Make the API contract explicit in `API.md` (it is) and test against the contract.

---

## B5 — Freezing an account does nothing

### Symptom
`POST /api/back-office/accounts/MB-0000-1004/freeze` returns `200` with
`"status": "FROZEN"`. The database still says `ACTIVE`. The account keeps transacting.
Reading the account again later shows `ACTIVE`, so the fraud desk believes the freeze was
reverted by someone.

### Root cause
```java
@Transactional(readOnly = true)
public AccountResponse freezeAccount(String accountNumber) {
    Account account = accountRepository.findByAccountNumber(accountNumber)...;
    account.setStatus(AccountStatus.FROZEN);
    accountRepository.save(account);
    ...
}
```

`readOnly = true` makes Spring set the Hibernate session to `FlushMode.MANUAL`. The
persistence context still tracks the entity and still records the change, but **it never
flushes**, so no `UPDATE` is ever issued. `save()` on an already-managed entity is a
`merge` that returns the same instance and schedules nothing extra — it does not force a
write.

Nothing fails, because nothing is attempted. The method returns the in-memory `Account`,
whose `status` field genuinely is `FROZEN`, and the mapper faithfully serialises it. The
response is a truthful description of an object that was never persisted.

The status check on the withdrawal path is correct and does reject frozen accounts — it
just never sees a frozen account, because none is ever written.

### Exact location
`src/main/java/com/meridian/banking/service/AccountService.java`, the `@Transactional`
annotation on `freezeAccount`.

### Correct fix
```java
@Transactional
public AccountResponse freezeAccount(String accountNumber) { ... }
```

`accountRepository.save(account)` then becomes redundant (dirty checking flushes the change
at commit) but is harmless and arguably clearer. Consider also recording who froze the
account and when — a freeze is an auditable event — and adding an explicit unfreeze
endpoint so the fraud desk is not left guessing.

### Affected components
`AccountService`, `BackOfficeController`, the `accounts` table, and the freeze control
that the fraud desk relies on.

### Underlying concept
**`readOnly = true` is not a hint, it is an instruction.** It sets the flush mode to
`MANUAL` and marks the JDBC connection read-only, and its whole purpose is to let Hibernate
skip dirty-checking work on read paths. Applied to a write path, it converts the write into
a silent no-op — the worst possible failure mode, because success is reported.

The second concept is that **a response body built from an in-memory entity proves nothing
about the database.** The mapper cannot tell the difference between a persisted change and
an uncommitted one. Whenever you are verifying a write, verify it in the store.

### Why this is realistic
Every read method in this service is `@Transactional(readOnly = true)`, and that is correct
and good practice. `freezeAccount` sits among `getAccount` and `listAccounts` and was
written in the same sitting. Copying the annotation down one method is a single keystroke,
and the result compiles, runs, returns `200`, and reads as correct in a diff — the
annotation is the *right* annotation two lines above and two lines below.

This is also a bug that actively misleads the investigation. The fraud desk concluded
someone was unfreezing accounts, because that is the only explanation consistent with a
`200` response and an `ACTIVE` row. A whole afternoon can go into auditing who had teller
access.

### Detecting it faster next time
- **Confirm writes at the database, never at the response.** One `psql` query would have
  ended this in thirty seconds.
- Watch the SQL log during the operation. A write endpoint that emits a `SELECT` and no
  `UPDATE` is the entire diagnosis, and requires no understanding of the business logic.
- When a change appears to revert itself, first ask whether it was ever applied. "Reverted"
  and "never written" look identical from the outside, and the second is far more common.

### Prevention
- Never annotate at the class level with `readOnly = true` and override per method; declare
  read-only explicitly on read methods only, so a missing annotation fails safe (a write
  that is not transactional at all will at least throw or behave visibly).
- Integration tests for write endpoints that re-read from the database — ideally after
  `entityManager.clear()` or in a separate transaction — rather than asserting on the
  response body.
- Static analysis: both IntelliJ and SonarQube flag entity mutation inside a `readOnly`
  transaction.

---

## How these interact

**B1 and B2 share a root discipline.** Both are about the boundaries of a unit of work: B1
about which writes are inside it, B2 about what else can happen while it is open. Fixing B1
by joining the credit to the outer transaction also makes the lock ordering in B2 relevant,
because both account rows are now locked by one transaction — fix B2 with pessimistic locks
and you must order them by id or two opposing transfers will deadlock. That is a genuine
consequence, not a trick.

**B3 is amplified by B2.** With no locking, a negative transfer racing against a legitimate
one can also lose the legitimate update, so the audit trail and the balance disagree in two
independent ways at once.

**B5 hides itself behind correct code.** The status checks in
`AccountTransactionService.loadOperableAccount` and `TransferService.requireOperable` are
right. If you start from "why is the frozen account transacting?" and read those, you find
nothing wrong, and you can lose a long time there. The fastest route is always to ask
whether the state you are checking against is actually in the database.

**Suggested fix order:** B4 (isolated, quick, builds confidence), B5 (isolated, one word),
B3 (stops active abuse), B1 (the atomicity bug, most important), B2 (the hardest and the
one that needs a deliberate strategy choice).

---

## What the test suite tells you

`mvn test` passes with all five defects live.

- `AccountMapperTest` and `TransactionMapperTest` test mapping, which was never wrong.
- `AccountRepositoryTest` tests that account lookup is scoped to the owning customer, which
  is correct — and notice that it makes the file *look* security-conscious, which is
  exactly the kind of signal that makes a reviewer relax.

None of these tests touch a transaction boundary, a propagation setting, a flush mode, a
concurrent request or a date conversion. Every defect in this project lives in one of those
five places. That is not a coincidence: those are the places where behaviour is decided by
the framework and the database rather than by the code you can read, which is precisely why
they need integration tests, transaction logs, concurrent reproductions and a habit of
checking the database instead of the response.
