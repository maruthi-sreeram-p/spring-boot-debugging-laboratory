# Solution — Athenaeum Circulation Service

Six defects. Do not read this until you have finished with the project.

---

## L1 — the copy is chosen with an unlocked read

**Report:** R1.

**Symptom.** Six simultaneous borrow requests for a title with five copies on the shelf all
succeed, and all six are issued the **same** physical copy.

```
firing 6 simultaneous borrow requests for book 1
{"member": 1, "status": 201, "loan": 13, "copy": 1, "barcode": "ATH-000101"}
{"member": 2, "status": 201, "loan":  9, "copy": 1, "barcode": "ATH-000101"}
{"member": 3, "status": 201, "loan": 12, "copy": 1, "barcode": "ATH-000101"}
{"member": 4, "status": 201, "loan": 11, "copy": 1, "barcode": "ATH-000101"}
{"member": 6, "status": 201, "loan": 10, "copy": 1, "barcode": "ATH-000101"}
{"member": 7, "status": 201, "loan": 14, "copy": 1, "barcode": "ATH-000101"}

issued: 6   refused: 0   distinct copies: 1
```

```
copy_id  active_loans        id  barcode     status
1        6                   1   ATH-000101  ON_LOAN
                             2   ATH-000102  AVAILABLE
                             3   ATH-000103  AVAILABLE
                             4   ATH-000104  AVAILABLE
                             5   ATH-000105  AVAILABLE
```

**Root cause.** `LoanService#borrow` selects a copy and then claims it:

```java
BookCopy copy = copyRepository
        .findFirstByBookIdAndStatusOrderByIdAsc(bookId, CopyStatus.AVAILABLE)
        .orElseThrow(() -> new NoCopyAvailableException(bookId));

copyRepository.updateStatus(copy.getId(), CopyStatus.ON_LOAN);
```

The `SELECT` is a plain consistent read. Under InnoDB's REPEATABLE READ it takes **no lock at
all**, so every concurrent transaction sees copy 1 as `AVAILABLE` and every one of them chooses
it. The `UPDATE` that follows does take an exclusive lock, so the six updates serialise — but
serialising the write does not undo the fact that all six decisions were made from the same stale
read. Each one simply sets `ON_LOAN` over `ON_LOAN` and commits.

The `version` column on `book_copies` is a red herring worth noticing: it exists in the schema and
is mapped on the entity as a plain `long`. It has no `@Version` annotation, so Hibernate never
reads it, never increments it, and never uses it to detect a conflict. Somebody intended optimistic
locking and stopped one annotation short.

**Location.** [`LoanService.java:87-91`](src/main/java/com/athenaeum/lending/service/LoanService.java#L87),
[`BookCopyRepository.java`](src/main/java/com/athenaeum/lending/repository/BookCopyRepository.java),
[`BookCopy.java`](src/main/java/com/athenaeum/lending/entity/BookCopy.java) (the unannotated
`version` field).

**Why it happens in real systems.** The code is a faithful transcription of what the clerk does:
find a copy on the shelf, mark it out, write the slip. That mental model is single-threaded and it
is correct for a person standing at a desk. Nothing in the Java expresses "and nobody else may take
this copy between those two steps", because in the original system nobody else could.

It never reproduces during development or manual QA, because a human tester is a single thread. It
reproduces on a Saturday morning at Central, in front of a queue, which is the worst possible place
to first learn about it.

**The correct fix.** The read that makes the decision must hold the lock that protects it. Two
good options.

*Pessimistic locking* — take the row lock as part of the select:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
@Query("""
        select c from BookCopy c
        where c.book.id = :bookId and c.status = com.athenaeum.lending.entity.CopyStatus.AVAILABLE
        order by c.id asc
        limit 1
        """)
Optional<BookCopy> lockNextAvailable(@Param("bookId") Long bookId);
```

The second transaction blocks on the select, and when it proceeds it re-reads the row under the
lock and finds it `ON_LOAN`, so it moves on to the next copy. This is the right answer for a
queue-like "claim one of these" problem, and it is what MySQL's `SELECT ... FOR UPDATE` is for.
MySQL 8.0.1 and later also support `SKIP LOCKED`, which lets each transaction claim a *different*
copy instead of queueing behind the first:

```sql
SELECT id FROM book_copies WHERE book_id = ? AND status = 'AVAILABLE' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
```

*Conditional update* — make the claim itself the test, so the database decides the winner:

```java
@Modifying
@Query("""
        update BookCopy c set c.status = com.athenaeum.lending.entity.CopyStatus.ON_LOAN
        where c.id = :copyId and c.status = com.athenaeum.lending.entity.CopyStatus.AVAILABLE
        """)
int claim(@Param("copyId") Long copyId);
```

`claim` returns 1 for the winner and 0 for everybody else; a 0 means retry with the next copy, or
refuse. This is compare-and-swap, it needs no explicit lock, and it is often the simplest correct
answer.

*Optimistic locking* is the third option and, here, the weakest: annotate `version` with
`@Version` and Hibernate will throw `OptimisticLockException` on the losers. That turns silent
corruption into a visible failure, which is a real improvement, but it converts a routine
contention into an error the caller has to retry. For "claim one of N interchangeable rows",
prefer a lock or a conditional update. Do add `@Version` anyway — the column is already there and
it protects every *other* write to the row.

Do not "fix" this by adding a unique index on active loans per copy. It would stop the corruption,
but the losers would get a constraint-violation 500 rather than a copy, and the real problem — a
decision made on an unlocked read — would still be there.

**Underlying concept.** A read that is used to make a decision is part of the write. If the read
takes no lock, the decision is based on a value that another transaction is free to change before
you act on it. Under REPEATABLE READ this is worse than it looks: your snapshot stays stable and
consistent all the way to commit, so nothing ever tells you the world moved.

**Detecting it faster.** For any "find something free and take it" code, ask what lock the *find*
takes. `SELECT * FROM performance_schema.data_locks` during a held transaction answers it
directly. And test concurrency with a script, always: a manual test cannot produce the only
condition under which the bug exists.

**Prevention.** Treat claim-one-of-many as a known pattern with three known-correct
implementations, and write a concurrency test for every one of them. A load script that fires N
simultaneous requests and asserts N distinct outcomes belongs in CI.

---

## L2 — the borrowing counter is a read-modify-write

**Report:** R2.

**Symptom.** A standard member, limit four, ends up with six loans. Their account page says one.

```
id  membership_number  cached  actual
4   LIB-0004           1       6
```

```
 API says: 1 of 4
 loans the same member actually holds: 6
 another borrow: 201
```

**Root cause.** `LoanService#borrow` checks the limit against a stored counter and then writes a
new absolute value computed in application memory:

```java
int limit = properties.getCirculation().loanLimitFor(member.getTier());
if (member.getActiveLoanCount() >= limit) {
    throw new CirculationRuleException("Borrowing limit of " + limit + " has been reached");
}
...
memberRepository.updateActiveLoanCount(memberId, member.getActiveLoanCount() + 1);
```

Six concurrent transactions all read `active_loan_count = 0`, all pass the limit check, and all
write `0 + 1 = 1`. The database is asked to store the number one, six times. Five increments are
lost.

The damage outlives the burst. The counter is now permanently wrong, and because the limit is
checked against the counter rather than against reality, the member can keep borrowing for ever.
A transient race has become persistent bad data.

**Location.** [`LoanService.java:76-92`](src/main/java/com/athenaeum/lending/service/LoanService.java#L76)
and the corresponding decrement in `returnCopy`.

**Why it happens in real systems.** The counter is a reasonable optimisation — counting rows on
every borrow is wasteful, and the cached value makes the limit check a field read. The increment
is written the way anybody would write it. What is missing is the recognition that
`read, add one, write` is three steps, and that the database only ever sees the third.

It is also invisible in code review. `count + 1` is correct arithmetic. The defect is not in the
expression, it is in the gap between reading `count` and writing it, which is not represented in
the source at all.

**The correct fix.** In order of preference.

*Delete the counter.* It is derived data, and derived data that can drift will drift. Check the
limit against the table:

```java
long held = loanRepository.countByMemberIdAndStatus(memberId, LoanStatus.ACTIVE);
if (held >= limit) { throw new CirculationRuleException(...); }
```

with a pessimistic lock on the member row taken first, so the count cannot change between the
check and the insert:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select m from Member m where m.id = :memberId")
Optional<Member> lockById(@Param("memberId") Long memberId);
```

That lock is what actually enforces the limit; the count is just how you read it.

*If the counter must stay*, make the write relative so the database does the arithmetic:

```java
@Modifying
@Query("update Member m set m.activeLoanCount = m.activeLoanCount + 1 where m.id = :memberId")
int incrementActiveLoans(@Param("memberId") Long memberId);
```

`x = x + 1` inside an `UPDATE` is atomic — InnoDB holds the row lock for the duration, so
increments cannot be lost. That fixes the drift but **not** the limit: the check still happens
before the lock is taken, so a burst can still push a member past four. To enforce the limit with
a counter you need the conditional update:

```java
@Modifying
@Query("""
        update Member m set m.activeLoanCount = m.activeLoanCount + 1
        where m.id = :memberId and m.activeLoanCount < :limit
        """)
int claimLoanSlot(@Param("memberId") Long memberId, @Param("limit") int limit);
```

Zero rows updated means the limit was reached — refuse.

Whichever you choose, add a reconciliation job that recomputes `active_loan_count` from the loans
table and logs every discrepancy. Drift has already happened in production data; fixing the code
does not fix the rows.

**Note on sequencing.** Fixing L1 does not fix this. A pessimistic lock on the *copy* serialises
the choice of copy; it does nothing about the *member* row. Two members borrowing different copies
concurrently is the normal case, and the same member borrowing two different copies concurrently
is exactly R2. These are two locks on two rows and both are needed.

**Underlying concept.** A lost update is what you get when two transactions read the same value
and both write a result derived from it. The fix is always one of three things: hold a lock across
the read and the write, make the write relative so the database computes it, or make the write
conditional on the value you read so a conflict is detectable.

**Detecting it faster.** Cached counters are always worth a reconciliation query. One `LEFT JOIN`
comparing cached against actual, run periodically and alerted on, turns this from a customer
complaint into a dashboard.

**Prevention.** Be suspicious of every denormalised count. If one is genuinely needed, write it
relative, guard it with a condition, and reconcile it on a schedule.

---

## L3 — the return charge reads a row that has not been committed

**Report:** R3.

**Symptom.** A book sixteen days late is handed back. The return succeeds. No charge is created.

```
id  copy_id  member_id  due_at                      returned_at  days_late
5   16       5          2026-09-08 06:56:37.000000  NULL         16

-- return it: 200

-- fines for that loan:   (no rows)
-- loan row now:          returned_at 2026-09-24 06:57:05   status RETURNED
-- log:  Loan 5 is still out, no return charge to assess
```

The log line is the whole story: at the moment the charge was priced, the loan looked like it was
still out.

**Root cause.** `LoanService#returnCopy` is `@Transactional`. It sets `returnedAt` on the managed
entity and then calls:

```java
fineService.assessOnReturn(loan.getId());
```

`FineService#assessOnReturn` is annotated

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
```

so Spring **suspends** the return's transaction and opens a second one on a second connection. It
then re-loads the loan by id:

```java
Loan loan = loanRepository.findDetailed(loanId).orElseThrow(...);
if (loan.getReturnedAt() == null) {
    log.debug("Loan {} is still out, no return charge to assess", loanId);
    return Optional.empty();
}
```

The outer transaction has not committed, so its `returned_at` is invisible to the inner one. The
inner transaction reads `NULL`, concludes the book is still out, and returns without charging.
The outer transaction then commits the return quite happily — the charge was never part of it.

Nothing fails. The member gets a clean receipt for a book they had for six weeks.

The nightly accrual does not catch it either: that job looks at loans that are still `ACTIVE`, and
this one is now `RETURNED`. The loan falls between the two mechanisms.

**Location.** [`FineService.java:60-66`](src/main/java/com/athenaeum/lending/service/FineService.java#L60)
and the call site in
[`LoanService.java`](src/main/java/com/athenaeum/lending/service/LoanService.java).

**Why it happens in real systems.** Read the comment above the method — it is the honest reasoning
that produced the bug:

> This runs in a transaction of its own so that a problem while pricing the charge can never roll
> back the return itself; a book that is physically back on the shelf must always be recorded as
> back on the shelf.

That is a genuinely good instinct. Isolating a risky side effect so it cannot roll back the
primary operation is a real pattern. What was missed is that isolation cuts both ways: a
transaction that cannot be rolled back by the caller also cannot *see* the caller.

The confusion is compounded by the method taking an id rather than the entity. Had it taken the
`Loan` object, the in-memory `returnedAt` would have been set and the bug would not have appeared —
which would have been luck, not correctness, and would have broken the moment somebody refactored.

**The correct fix.** Decide what you actually want.

*If the charge belongs to the return* — and it does; a return that is not charged is a wrong
return — then it belongs in the same transaction:

```java
@Transactional
public Optional<FineResponse> assessOnReturn(Long loanId) { ... }
```

Default propagation (`REQUIRED`) joins the caller. The same persistence context is used, the
pending change is flushed before the query, and the charge is created and committed atomically
with the return. If pricing fails, the return rolls back too — which is correct, because a return
that silently loses a charge is worse than a return that fails loudly and is retried.

*If it genuinely must be isolated* — for example because the charge involves an external payment
provider that must not be retried — then it must run **after** the return has committed:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onLoanReturned(LoanReturnedEvent event) {
    fineService.assessOnReturn(event.loanId());
}
```

with `returnCopy` publishing the event. Now the row the listener reads is the committed one. Note
that this also introduces the possibility of the charge being lost if the process dies between
commit and listener — if that matters, write an outbox row inside the return transaction and have
a worker drain it.

*Either way*, pass the data you already have rather than re-reading it, and make the accrual job a
genuine safety net: have it also look at loans returned since the last run that have no charge
row. Belt and braces on money is not paranoia.

**Underlying concept.** `REQUIRES_NEW` is not "a nested transaction". It is a second, independent
transaction on a second connection, with its own snapshot, its own locks, and no visibility of the
suspended one. Anything the caller has written but not committed does not exist as far as it is
concerned. The same reasoning applies to `@Async`, to a second `@Transactional` bean called after a
write, and to any listener not bound to `AFTER_COMMIT`.

It is also a deadlock risk in general: the outer transaction may hold locks the inner one needs,
and the inner one cannot wait for a transaction that is waiting for it.

**Detecting it faster.** Whenever a method takes an id and re-reads it, ask which transaction that
read is in. A breakpoint on the re-read, inspecting the loaded row rather than the object you
expected, settles it in seconds. Turning on `DEBUG` for
`org.springframework.transaction.interceptor` prints "Suspending current transaction, creating new
transaction" and names the method.

**Prevention.** Default to `REQUIRED`. Treat `REQUIRES_NEW` as a decision that needs a comment
explaining what it is isolating and an explicit answer to "what does this transaction need to read,
and has it been committed yet?"

---

## L4 — days are counted in whole 24-hour periods

**Report:** R4.

**Symptom.** A book six calendar days late is charged for five. The number changes depending on
what time the accrual is run. A book twenty-three hours late is charged nothing.

```
-- loan 1 due 2026-09-18 06:56:37
-- asOf 2026-09-19T05:56:37  (23 hours late)
   accrual: {"loansExamined": 2, "finesCreated": 1, "totalAssessed": 25.0}   <- that charge is for loan 3
   fines for loan 1: (no rows)

-- asOf 2026-09-24T05:56:37  (5 days 23 hours late)
   id  loan_id  amount  days_overdue
   3   1        25.00   5
```

Six calendar days at 5.00 should be 30.00. The service charges 25.00. Run the same accrual an hour
later and it charges 30.00.

**Root cause.** `FineService#daysOverdue`:

```java
private int daysOverdue(LocalDateTime dueAt, LocalDateTime asOf) {
    long late = ChronoUnit.DAYS.between(dueAt, asOf);
    return late < 0 ? 0 : (int) late;
}
```

`ChronoUnit.DAYS.between` on two `LocalDateTime`s counts **complete** 24-hour periods and rounds
towards zero. The published rule is about calendar days: a book due on the 18th and still out on
the 24th is six days late regardless of the hour.

The two only agree when the time of day of `asOf` is later than the time of day of `dueAt`, which
is why the answer moves with the clock and why a job that runs at 02:00 systematically undercharges
books that were due during the working day.

**Location.** [`FineService.java`](src/main/java/com/athenaeum/lending/service/FineService.java),
the `daysOverdue` method — used by both the accrual and the return path.

**Why it happens in real systems.** `ChronoUnit.DAYS.between` reads like "the number of days
between these two things", and for two `LocalDate`s it is exactly that. The defect is entirely in
the argument type: the same method name means "whole elapsed periods" for a date-time and
"calendar days" for a date. Nothing warns you, because both compile.

It also hides well. Roughly half the time the answer is right, so spot checks pass, and the error
is one day out of many — nobody notices a 25.00 charge that should be 30.00 until somebody totals
a year of them.

**The correct fix.** Count the thing the rule is about:

```java
private int daysOverdue(LocalDateTime dueAt, LocalDateTime asOf) {
    long late = ChronoUnit.DAYS.between(dueAt.toLocalDate(), asOf.toLocalDate());
    return late < 0 ? 0 : (int) late;
}
```

Now a book due yesterday is one day late from the first minute of today, which is what the rule
says and what a member expects.

Two things to decide explicitly while you are in there, and to write down:

- **Which day does the count start on?** `toLocalDate()` on both sides means a book due at 23:55 is
  one day late at 00:05. That is the rule as published. If the library actually means "a full day
  of grace", say so in the rule and implement `ChronoUnit.DAYS.between` on the date-times *plus
  one*, not by leaving the truncation in place by accident.
- **Whose calendar?** `LocalDateTime` has no zone. The JVM is pinned to UTC in
  `LendingApplication`, so a book due at 23:00 local time in a different zone would be counted
  against the wrong date. For a single-city library this is fine; write down that it is a decision.

**Underlying concept.** "How many days" is two different questions — elapsed duration and calendar
difference — and business rules almost always mean the second. The type you call the method on is
what selects between them, and the compiler will not tell you that you picked the wrong one.

**Detecting it faster.** Give the job a way to be run against a chosen instant — this service has
`?asOf=` for exactly that — and the whole class of clock-dependent bug becomes a deterministic
experiment instead of a story about Tuesday afternoon.

**Prevention.** A table-driven unit test over the boundaries: due 23:59 checked at 00:01, due 00:01
checked at 23:59, exactly 24 hours, exactly 0. Four assertions pin the semantics permanently.

---

## L5 — a promoted hold does not actually hold anything

**Report:** R5.

**Symptom.** A hold goes `READY` and names the copy that is waiting. The copy stays on the shelf,
the catalogue offers it, and the next walk-in is issued that exact copy.

```
-- the queue for book 5
id  book_id  member_id  status   queue_position  held_copy_id  ready_until
1   5        6          READY    1               16            2026-09-26 06:57:05
2   5        7          WAITING  2               NULL          NULL

-- the copy it is supposedly holding
id  barcode     status
16  ATH-000502  AVAILABLE

-- the catalogue:  available 1 | holds waiting 1

-- a walk-in borrows book 5:
   {"id": 9, "copyId": 16, "barcode": "ATH-000502", "memberId": 4, "membershipNumber": "LIB-0004"}

-- what member 6 sees when they come to collect:
id  status  held_copy_id  copy_status
1   READY   16            ON_LOAN
```

**Root cause.** `ReservationService#promoteNextInQueue` updates the reservation and nothing else:

```java
next.setStatus(ReservationStatus.READY);
next.setHeldCopyId(copy.getId());
next.setReadyUntil(LocalDateTime.now().plusHours(holdShelfHours));
```

The copy itself was set to `AVAILABLE` by the return, and nothing changes it. `CopyStatus.RESERVED`
exists in the enum and is never assigned anywhere in the codebase. Meanwhile `borrow` selects the
first copy with status `AVAILABLE` and has no idea reservations exist.

So the hold is a note on a different table that nobody reads. Three independent things are wrong at
once: the copy is borrowable, the catalogue counts it as available, and the queue is not consulted
when issuing.

**Location.** [`ReservationService.java`](src/main/java/com/athenaeum/lending/service/ReservationService.java),
`promoteNextInQueue`; and
[`LoanService.java`](src/main/java/com/athenaeum/lending/service/LoanService.java), `borrow`.

**Why it happens in real systems.** The hold feature was built from the member's point of view:
join a queue, get an email. The email is the deliverable, and the email works. The physical
consequence — this copy must now be untouchable by everybody except one named person — lives in a
different service written by somebody else, and there is no compiler error for "you changed a
status that nothing enforces".

The `RESERVED` enum constant is the tell. Somebody knew what was needed. Adding the constant felt
like implementing it.

**The correct fix.** Make the hold a fact about the copy, not only about the reservation:

```java
@Transactional
public void promoteNextInQueue(BookCopy copy) {
    reservationRepository
            .findFirstByBookIdAndStatusOrderByQueuePositionAsc(copy.getBook().getId(), WAITING)
            .ifPresent(next -> {
                next.setStatus(ReservationStatus.READY);
                next.setHeldCopyId(copy.getId());
                next.setReadyUntil(LocalDateTime.now().plusHours(holdShelfHours));
                copyRepository.updateStatus(copy.getId(), CopyStatus.RESERVED);
            });
}
```

Then three more things follow, and all three are required:

1. `borrow` already selects only `AVAILABLE` copies, so a `RESERVED` copy is no longer offered to
   a walk-in. Good — but it must also be *issuable* to the member whose hold it is. Add a path
   that accepts a reservation id, checks the holder, issues the held copy, and marks the
   reservation `FULFILLED`.
2. `countAvailable` must stop counting `RESERVED` copies (see L6 — the same query is wrong for a
   second reason).
3. The 48-hour window must actually expire. Nothing currently does anything with `ready_until`. A
   scheduled sweep should mark expired holds `EXPIRED`, return the copy to `AVAILABLE`, and promote
   the next member in the queue — otherwise a member who never collects takes a copy out of
   circulation permanently.

**Underlying concept.** A status column on row A does not constrain row B. If a business rule says
"this thing is reserved for that person", something has to make the reserved-ness true where the
decision is made — a status the borrow path reads, or a row it has to lock. A note that only
humans read is documentation, not enforcement.

Note also the ordering hazard: promoting a hold *after* the copy was set to `AVAILABLE` leaves a
window in which the copy is genuinely borrowable, even after this fix. Set the copy to `RESERVED`
in the same transaction as the return, which the fix above does.

**Detecting it faster.** For any state change, list every table that should have changed and check
them all. Here, "a hold became ready" should touch `reservations` *and* `book_copies`; one `SELECT`
against the copy would have shown `AVAILABLE` immediately.

**Prevention.** When an enum gains a constant, grep for its assignments. A value that is never
assigned is a feature that was never finished, and it is trivial to check.

---

## L6 — availability is defined as "not on loan"

**Report:** R6.

**Symptom.** The catalogue advertises copies that cannot be lent.

```
  MySQL Cookbook | total 3 | available 3
    ATH-000301 AVAILABLE
    ATH-000302 LOST
    ATH-000303 REPAIR
   first borrow  201
   second borrow 409
   catalogue now says: available 2
```

**Root cause.** `BookCopyRepository#countAvailable`:

```java
@Query("select count(c) from BookCopy c where c.book.id = :bookId and c.status <> 'ON_LOAN'")
long countAvailable(@Param("bookId") Long bookId);
```

`CopyStatus` has five values. The query excludes one of them. `LOST`, `REPAIR` and `RESERVED` are
all counted as borrowable, while `borrow` correctly requires `status = AVAILABLE` — so the
catalogue and the circulation desk are working from two different definitions of the same word.

**Location.** [`BookCopyRepository.java:23-24`](src/main/java/com/athenaeum/lending/repository/BookCopyRepository.java#L23).

**Why it happens in real systems.** When the query was written the enum had two values:
`AVAILABLE` and `ON_LOAN`. With two values, `<> 'ON_LOAN'` and `= 'AVAILABLE'` are the same query,
and the negative form is the one that reads more naturally next to "how many are not out". `LOST`
and `REPAIR` were added a year later by somebody solving a stock-take problem, who had no reason
to go looking for queries that assumed a two-valued enum.

This is the cheapest defect in the project to fix and the one most likely to still be there in a
year, because it produces a slightly-too-large number rather than an error.

**The correct fix.**

```java
@Query("""
        select count(c) from BookCopy c
        where c.book.id = :bookId
          and c.status = com.athenaeum.lending.entity.CopyStatus.AVAILABLE
        """)
long countAvailable(@Param("bookId") Long bookId);
```

Positive, enumerated, and — because it now names the enum constant rather than a string literal —
it will fail to compile if the constant is ever renamed. The string `'ON_LOAN'` in the original
would have survived any rename silently.

While you are here, the catalogue would be more honest showing both numbers: copies on the shelf
and copies out. A member can act on "0 available, 2 out, 1 hold waiting" in a way they cannot act
on a single wrong number.

**Underlying concept.** Define membership of a set positively. A negative filter over an enum
silently acquires every value added after it was written; a positive filter acquires nothing and
forces a decision. This is the same reasoning as an allowlist over a denylist in security, and it
fails the same way — quietly, and in the permissive direction.

**Detecting it faster.** Compare the definition used by the read path with the definition used by
the write path. Here, `borrow` says `status = AVAILABLE` and the catalogue says `status <>
ON_LOAN`; putting those two lines next to each other is the entire diagnosis.

**Prevention.** Reference enum constants rather than string literals in queries so renames break
the build, and when an enum gains a value, grep every query that mentions the type.

---

## Why the test suite is green

`mvn -B test` passes with all six defects live.

- `CirculationPropertiesTest` checks the loan lengths and limits. They are correct; the defects are
  in how they are enforced, not in what they are.
- `LoanRepositoryTest` is a `@DataJpaTest` slice on H2. It verifies the overdue query and the copy
  counts against data it created itself. It runs on a single thread, in one transaction, against a
  database that is not InnoDB — so L1 and L2 are outside its universe by construction, L3 depends
  on propagation behaviour that a slice test does not exercise, and L4, L5 and L6 live in service
  logic and a different query.

Two of these six defects **cannot** be caught by any single-threaded test, ever. One is a
transaction-boundary bug that only exists when two real transactions overlap on two real
connections. The tests that would have caught this project are a concurrency harness firing
simultaneous requests at a real MySQL and asserting on the resulting rows, plus a table-driven date
test, plus one integration test per business rule that reads the *database* afterwards rather than
the HTTP response.

That is the lesson of the whole project: the HTTP response told you everything went well, six
times, while the library lost track of four books.
