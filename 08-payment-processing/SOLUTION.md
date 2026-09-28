# SOLUTION — Ledgerline Payment Processing Service

> **Answer key. Do not read this until you have finished investigating.**

Five defects. All are deterministic, driven by the sandbox amounts. Three of them can lose
or duplicate real money.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| P1 | PAY-891 | The idempotency lookup only matches `SUCCEEDED` payments | `service/PaymentService.java` |
| P2 | PAY-880 | The order is set to `PAID` before the acquirer is called, and never reverted | `service/PaymentService.java` |
| P3 | PAY-887 | `@Retryable` wraps `@Transactional` around a non-idempotent capture | `service/PaymentService.java` |
| P4 | PAY-895 | Reconciliation marks stale payments failed without asking the acquirer | `scheduling/ReconciliationJob.java` |
| P5 | PAY-902 | The charged amount comes from the request, not from the order | `service/PaymentService.java` |

---

## P1 — One idempotency key, two payments

### Symptom
A request is replayed with the same `Idempotency-Key` after a decline. A second payment row
is created with the same key and the acquirer is contacted again.

```
PAY-977977  demo-decline-01  FAILED
PAY-388693  demo-decline-01  FAILED
```

Replaying the key of a *successful* payment correctly returns the original reference, which
is what makes this look like it works.

### Root cause
```java
Optional<Payment> alreadyTaken =
        paymentRepository.findByIdempotencyKeyAndStatus(idempotencyKey, PaymentStatus.SUCCEEDED);
if (alreadyTaken.isPresent()) {
    return paymentMapper.toResponse(alreadyTaken.get());
}
```

The lookup asks "did this key produce a *successful* payment?" when the rule is "was this
key used at all?". A key whose payment is `FAILED`, `PROCESSING` or `PENDING` does not match,
so the method falls through and starts a fresh charge.

`findByIdempotencyKey` — the method that asks the right question — already exists in
`PaymentRepository` and is never called.

The schema does not help either: `idempotency_key` carries a plain index (`KEY
idx_payments_idempotency`), not a unique constraint, so nothing stops two rows sharing a key.

The dangerous case is not the decline in the ticket. It is a payment left `PROCESSING`
because the acquirer's response was lost: the client sees a network error, retries with the
same key exactly as documented, the lookup does not match, and the customer is charged
twice.

### Exact location
`src/main/java/com/ledgerline/payments/service/PaymentService.java`, the first statement of
`submit`.

### Correct fix
Ask the real question:

```java
Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
if (existing.isPresent()) {
    return paymentMapper.toResponse(existing.get());
}
```

Then make the database enforce it, because a check-then-insert is racy — two concurrent
requests with the same key can both pass the check:

```sql
ALTER TABLE payments DROP INDEX idx_payments_idempotency;
ALTER TABLE payments ADD UNIQUE KEY uk_payments_idempotency (idempotency_key);
```

and catch `DataIntegrityViolationException` on insert, re-reading and returning the row the
winner created. With the constraint in place the application check becomes an optimisation
rather than the guarantee.

A production-grade implementation also stores a fingerprint of the request body against the
key and returns `422` when the same key arrives with a different payload — otherwise a
client bug can silently reuse a key for a different order.

### Affected components
`PaymentService`, `PaymentRepository`, the `payments` table and its indexes, every merchant
client that retries.

### Underlying concept
**An idempotency key identifies a request, not an outcome.** The whole point is that the
caller does not know what happened — that is why they are retrying. Making the lookup depend
on the result inverts the contract: it protects the case where the client already knows it
succeeded, and abandons them in the case they actually need help with.

The second concept: **uniqueness is a database property.** An application-level check under
concurrency is a suggestion. If a column must be unique, say so in the schema.

### Why this is realistic
`findByIdempotencyKeyAndStatus(key, SUCCEEDED)` reads like a careful, specific query, and
the reasoning behind it is easy to reconstruct: "if it already succeeded, do not charge
again." That sentence is true; it is just not the whole rule. The method also passes the
obvious test — pay, replay, get the same payment — because that test uses a successful
payment.

The unused `findByIdempotencyKey` sitting next to it is the tell, and unused repository
methods are easy to skim past.

### Detecting it faster next time
- **Test idempotency across every outcome, not just success.** Approve, decline, timeout;
  replay each. The bug is in the second and third rows of that table.
- Count rows per key. One query finds every instance, including historical ones.
- Any `...AndStatus(X)` inside a duplicate or uniqueness check deserves a second look.

### Prevention
- Unique constraint on the key, with the application handling the violation.
- Store the request fingerprint alongside the key.
- Tests covering replay for every terminal and non-terminal state.

---

## P2 — The order is marked paid before the money is taken

### Symptom
Paying `ORD-5006` (760.11, the sandbox decline amount) produces a payment with status
`FAILED` and reason "Insufficient funds" — and an order with status `PAID`. Fulfilment ships
against `PAID`.

### Root cause
```java
Payment saved = paymentRepository.save(payment);

order.setStatus(OrderStatus.PAID);          // before the acquirer has been asked

saved.setAttempts(saved.getAttempts() + 1);
GatewayResult result = gateway.capture(...);

if (result.approved()) { ... } else {
    saved.setStatus(PaymentStatus.FAILED);
    saved.setFailureReason(result.message());
    ...                                      // the order is never put back
}
```

The order is advanced optimistically before the outcome is known, and the decline branch
updates only the payment. The transaction commits with a `FAILED` payment and a `PAID`
order.

The timeout path is different but no better: the exception propagates and the transaction
rolls back, so the premature `PAID` is undone — by accident, not by design.

### Exact location
`src/main/java/com/ledgerline/payments/service/PaymentService.java`, the
`order.setStatus(OrderStatus.PAID)` before the `gateway.capture` call.

### Correct fix
Move the order transition into the branches, where the outcome is known:

```java
GatewayResult result = gateway.capture(...);

if (result.approved()) {
    saved.setStatus(PaymentStatus.SUCCEEDED);
    saved.setGatewayRef(result.gatewayRef());
    order.setStatus(OrderStatus.PAID);
    recordAttempt(saved, AttemptOutcome.APPROVED, result.gatewayRef(), result.message());
} else {
    saved.setStatus(PaymentStatus.FAILED);
    saved.setFailureReason(result.message());
    order.setStatus(OrderStatus.PAYMENT_FAILED);
    recordAttempt(saved, AttemptOutcome.DECLINED, null, result.message());
}
```

Leave the order **unchanged** when the acquirer does not answer. `PROCESSING` means "we do
not know", and pretending otherwise in either direction is what PAY-895 is about.

Worth adding: a guard so an order already `PAID` cannot be paid again, which is a separate
door to the same loss.

### Affected components
`PaymentService`, the `orders` table, every merchant fulfilment system watching `PAID`.

### Underlying concept
**Do not record an outcome before you have one.** Optimistic state updates are fine when the
operation cannot fail or when there is a compensating action that definitely runs. A call to
an external system has three outcomes — success, failure, and *unknown* — and code that
handles two of them will be wrong about the third.

The related discipline: **every status transition should be written in the branch that
learned the fact.** Setting it up front and correcting it later means every new failure path
has to remember to correct it, and eventually one will not.

### Why this is realistic
The line sits with the other setup code, where it reads as part of "prepare the order for
payment". The happy path is correct, the timeout path is accidentally correct because of the
rollback, and only the decline path is wrong — so the defect is one branch out of three, in
a method that otherwise handles outcomes carefully.

### Detecting it faster next time
- **When two rows disagree, find every line that writes each one.** Two `setStatus` calls,
  one of them before the decision point. Thirty seconds once you look.
- Test the unhappy paths first for anything involving money. The happy path is the one that
  gets written and demonstrated; the failure paths are where the losses are.
- The reconciliation query for `PAID` orders with no successful payment finds every instance
  in the data.

### Prevention
- Assign terminal status only in the branch that determined it.
- An invariant check — no `PAID` order without a `SUCCEEDED` payment — as a test and as a
  monitor.
- Explicitly enumerate success / failure / unknown for every external call.

---

## P3 — One payment, three captures, no record

### Symptom
One `POST /api/payments` for `ORD-5007` (1120.22, the amount whose response is lost)
produces:

```
acquirer:  3 captures, three different payment refs, three authorisation codes
payments:  no rows at all
orders:    still AWAITING_PAYMENT
HTTP:      504
```

The customer has been charged three times and the system has no record of any of it.

### Root cause
Two annotations on the same method:

```java
@Retryable(retryFor = GatewayTimeoutException.class,
        maxAttemptsExpression = "${payments.retry.max-attempts}",
        backoff = @Backoff(delayExpression = "${payments.retry.backoff-millis}"))
@Transactional
public PaymentResponse submit(String idempotencyKey, SubmitPaymentRequest request) { ... }
```

**Retry advice wraps transaction advice.** `@EnableRetry` registers its advisor at
`Ordered.LOWEST_PRECEDENCE - 1`, ahead of the transaction advisor at
`LOWEST_PRECEDENCE`, so each retry runs a *complete new transaction*.

The consequences compound:

1. Attempt 1 opens a transaction, writes a payment row, calls the acquirer. The acquirer
   **captures the money** and then throws. The transaction rolls back — the payment row, the
   attempt row and the order change all disappear.
2. Retry advice catches the exception and calls the method again. A new transaction, a new
   payment row, a **new payment reference** (`nextPaymentRef()` runs again), and a second
   capture at the acquirer.
3. Same again for attempt 3. Then retries are exhausted, the exception propagates, the last
   transaction rolls back too, and the caller gets `504`.

The acquirer sees three unrelated payment references and treats them as three separate
payments, because from its point of view that is exactly what they are.

The rollback is what destroys the evidence. The attempt history — the one thing that would
let operations answer the cardholder — is written inside the same transaction and is rolled
back with everything else.

### Exact location
`src/main/java/com/ledgerline/payments/service/PaymentService.java`, the combination of
`@Retryable` and `@Transactional` on `submit`, together with `nextPaymentRef()` being called
inside the retried scope.

### Correct fix
This is a design fix, not a one-liner. Three things have to change.

**1. Retry only the call, not the unit of work.** Move the acquirer call behind a narrow
client whose only job is the network hop:

```java
@Component
public class AcquirerClient {
    @Retryable(retryFor = GatewayTimeoutException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 200))
    public GatewayResult capture(String paymentRef, BigDecimal amount, String instrument) {
        return gateway.capture(paymentRef, amount, instrument);
    }
}
```

`submit` is no longer retried, so the payment row and its reference survive across attempts.

**2. Make the retry idempotent at the acquirer.** Retrying with the *same* payment reference
is what lets the acquirer recognise a repeat and return the original authorisation instead of
capturing again. That is the whole purpose of sending a stable reference — and generating a
new one per attempt, as this code does, defeats it. Fixing the annotation placement fixes
this too, because the reference is then allocated once.

**3. Persist the payment before calling the acquirer, in its own transaction.** The record
that "we are about to charge this" must outlive a failure:

```java
Payment saved = createPaymentRecord(...);   // REQUIRES_NEW, commits immediately
try {
    GatewayResult result = acquirerClient.capture(saved.getPaymentRef(), ...);
    applyOutcome(saved, result);            // separate transaction
} catch (GatewayTimeoutException ex) {
    recordAttempt(saved, AttemptOutcome.TIMEOUT, null, ex.getMessage());
    // leave PROCESSING for reconciliation
}
```

Now a lost response leaves a `PROCESSING` payment with a reference, an attempt history, and
something for P4's reconciliation to work with.

If you keep both annotations on one method for now, at minimum add
`@Transactional(propagation = Propagation.REQUIRES_NEW)` on the record-creation step so the
audit trail survives — but the structural fix is the right one.

### Affected components
`PaymentService`, `SimulatedPaymentGateway`, `payment_attempts`, `ReconciliationJob` (which
has nothing to reconcile without the surviving row), and the cardholder.

### Underlying concept
**Retrying a non-idempotent side effect multiplies it.** Retry is only safe when the
operation can be repeated without additional effect — which means the remote side must be
able to recognise the repeat. For payments that recognition is the payment reference or an
idempotency key sent *to the acquirer*. Retrying with a fresh identifier each time is not a
retry; it is three payments.

**Retry scope must be narrower than the transaction, not wider.** Wrapping a transaction in
retry means each attempt discards its own evidence and starts clean — which is exactly wrong
when the attempt had an external effect. The rule is: retry the call, keep the record.

And: **a rolled-back transaction erases your audit trail too.** Anything you need in order to
investigate a failure must be committed outside the transaction that failed.

### Why this is realistic
Both annotations are individually correct and idiomatic. `@Retryable` on a service method
that talks to a flaky dependency is the first thing every tutorial shows. `@Transactional`
on a method that writes several rows is obviously right. The interaction is invisible unless
you know the advisor ordering, and nothing in either annotation hints at it.

The failure is also silent in development, because a sandbox that always approves never
exercises the retry path at all.

### Detecting it faster next time
- **Count the calls at the far end.** The acquirer view exists for this. One request, three
  captures, is the entire diagnosis.
- `logging.level.org.springframework.retry=DEBUG` plus
  `JpaTransactionManager=DEBUG` shows the retries and the transactions interleaved, which
  makes the wrapping order obvious.
- Whenever you see `@Retryable` and `@Transactional` together, work out the order before
  anything else, and ask what the method does that cannot be undone.

### Prevention
- Retry at the client, never around a transaction.
- Send a stable idempotency token to every external system that moves money.
- Write the "about to do this" record in its own committed transaction.
- A test that forces a timeout and asserts the far end received exactly one call.

---

## P4 — Reconciliation guesses instead of asking

### Symptom
A payment stuck in `PROCESSING` is marked `FAILED` with "No response from the acquirer
within 5 minutes", and the order becomes `PAYMENT_FAILED` — while the acquirer's settlement
view shows a `CAPTURED` record for that exact payment reference.

```
acquirer:  PAY-406185  CAPTURED  1120.22  SIMGW-4471003
ours:      PAY-406185  FAILED    "No response from the acquirer within 5 minutes"
```

### Root cause
```java
List<Payment> stale = paymentRepository
        .findByStatusAndUpdatedAtBefore(PaymentStatus.PROCESSING, cutoff);

for (Payment payment : stale) {
    payment.setStatus(PaymentStatus.FAILED);
    payment.setFailureReason("No response from the acquirer within ...");
    payment.getOrder().setStatus(OrderStatus.PAYMENT_FAILED);
}
```

The job never contacts the acquirer. It treats "we do not know" as "it failed", which is the
one interpretation that is unsafe: if the capture did happen, the customer has been charged
and we have just told the merchant it did not.

`SimulatedPaymentGateway.lookup(paymentRef)` exists precisely to answer this question, and
the job does not use it. The `PayOpsController` does, which is how the ticket was diagnosed
— a human did by hand what the job should do automatically.

### Exact location
`src/main/java/com/ledgerline/payments/scheduling/ReconciliationJob.java`, the loop in
`reconcile`.

### Correct fix
```java
for (Payment payment : stale) {
    List<GatewayCall> atAcquirer = gateway.lookup(payment.getPaymentRef());
    Optional<GatewayCall> capture = atAcquirer.stream()
            .filter(call -> "CAPTURED".equals(call.outcome()))
            .findFirst();

    if (capture.isPresent()) {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setGatewayRef(capture.get().gatewayRef());
        payment.getOrder().setStatus(OrderStatus.PAID);
    } else {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("No capture found at the acquirer");
        payment.getOrder().setStatus(OrderStatus.PAYMENT_FAILED);
    }
    recordAttempt(payment, ...);   // the reconciliation decision is itself auditable
}
```

Two refinements worth making:

- If the acquirer shows **more than one** capture for a reference, that is P3 in the data.
  Do not silently pick the first; flag it for a human, because it is a refund, not a status
  update.
- A payment the acquirer has never heard of should probably not be `FAILED` immediately
  either. Real systems retry the lookup for a bounded period before concluding, because
  settlement files lag.

### Affected components
`ReconciliationJob`, `SimulatedPaymentGateway`, the `payments` and `orders` tables, the daily
settlement process.

### Underlying concept
**Reconciliation means comparing two systems of record, not assuming one.** A job that
decides an outcome from a timestamp is not reconciling; it is guessing, and it will be wrong
in exactly the cases reconciliation exists to catch.

Underneath is the three-valued nature of any remote call. `PROCESSING` is a legitimate,
durable third state meaning "unknown". Collapsing unknown into failure is the unsafe
direction when money is involved — the safe default is to leave it unknown and escalate,
because a stuck payment costs an investigation while a wrongly-failed capture costs a
chargeback and a refund.

### Why this is realistic
The job reads as sensible operational hygiene: "payments should not sit in `PROCESSING`
forever, so close them off." The message it writes is even accurate — there genuinely was no
response within five minutes. The mistake is in the leap from that fact to a conclusion, and
that leap is one line with no obvious seam in it.

Reconciliation code is also often written under pressure, after the first incident where
payments piled up in `PROCESSING`, and the fastest fix for a pile-up is to drain it.

### Detecting it faster next time
- **Read what a job consults before it decides.** If a status-changing job never calls the
  other system, it is not reconciling.
- Compare the two records for a sample of rows. One mismatch proves it.
- Any failure reason phrased in terms of *our* timeouts rather than *their* answer is a
  signal that nobody asked them.

### Prevention
- Reconciliation must read both sides; no exceptions.
- Escalate ambiguity to a human rather than resolving it optimistically or pessimistically.
- Alert on `PROCESSING` payments older than the threshold, so the pile-up is visible without
  needing to be drained.

---

## P5 — The client chooses what to pay

### Symptom
`ORD-5005` is for 50,000.00. A payment request for 1.00 is accepted, succeeds, and the order
becomes `PAID`.

### Root cause
```java
payment.setAmount(request.getAmount());
```

The amount charged comes from the request body. The order was loaded four lines earlier and
its `amount` is never read, never compared, and never used.

Every downstream decision — the capture, the payment row, the order transition — is made
against a number the caller supplied.

Note how it combines with P2: because the order is marked `PAID` regardless, the underpayment
is not merely accepted, it is confirmed to the merchant as full settlement.

### Exact location
`src/main/java/com/ledgerline/payments/service/PaymentService.java`, the `setAmount` call in
`submit`.

### Correct fix
Charge what is owed, and reject a request that disagrees so client bugs surface instead of
hiding:

```java
if (request.getAmount().compareTo(order.getAmount()) != 0) {
    throw new PaymentRuleException("Payment amount " + request.getAmount()
            + " does not match order " + order.getOrderRef() + " (" + order.getAmount() + ")");
}
payment.setAmount(order.getAmount());
payment.setCurrency(order.getCurrency());
```

Silently substituting the order amount also works and is safe, but it lets a broken client
ship a wrong number forever. Rejecting is the better default; if partial payments are a real
requirement they need their own model, not an unvalidated field.

### Affected components
`PaymentService`, `SubmitPaymentRequest`, the `payments` and `orders` tables, merchant
revenue.

### Underlying concept
**Never let the client state a value the server already knows.** Price, amount, discount,
tax, user id, role — anything the server can derive must be derived. A request field that
duplicates server-side state is an attack surface, and this is one of the oldest
vulnerabilities on the web.

The field can still exist in the API as a **confirmation** — "this is what I believe I owe" —
but then its only legitimate use is to be compared, never to be used.

### Why this is realistic
`amount` belongs in the request from the client's point of view: they are telling you what
they are paying. Bean Validation is present and correct on the field (`@NotNull`,
`@DecimalMin("0.01")`, `@Digits`), which makes it *look* guarded — those constraints check
the number is well formed, not that it is the right number.

The order is right there in the method, loaded and unused, which is the tell.

### Detecting it faster next time
- **For every money field in a request, ask who is entitled to decide it.** If the answer is
  the server, the field must be validated or ignored.
- Trace the value from the wire to the ledger and look for the point where it should have
  met server-side truth.
- Test with an amount that differs from the order — one request, one answer.

### Prevention
- Derive amounts server-side; use request amounts only as an assertion to compare.
- A test per payment endpoint sending a mismatched amount and expecting a rejection.
- The reconciliation query comparing order totals against successful payments, run
  continuously.

---

## How these interact

**P3 destroys the evidence P4 needs.** With retry rolling back every attempt, there is no
`PROCESSING` payment row for reconciliation to find — so P4 is *invisible* until P3 is fixed.
Fix P3, and payments start surviving in `PROCESSING`, and the very next reconciliation run
marks them `FAILED` despite the money having been taken. That is the sharpest layering in
this project: fixing the worse bug makes a quieter one start doing damage.

**P2 amplifies P5.** Underpaying an order would be bad enough as a `SUCCEEDED` payment for
the wrong amount; because the order is marked `PAID` regardless of outcome, it is also
reported to the merchant as settled in full.

**P1 and P3 are the same failure at two levels.** P1 fails to recognise a repeated request
from the client; P3 fails to present a recognisable repeat to the acquirer. Both are
idempotency, one facing inward and one facing outward, and a system that gets one right and
the other wrong is still double-charging.

**P4 depends on P2's decision.** Reconciliation can only conclude anything about an order if
`PROCESSING` genuinely means the order was left alone. Fixing P2 correctly — leaving the
order untouched on an unknown outcome — is what makes P4's fix meaningful.

**Suggested fix order:** P5 (isolated, stops theft immediately), P2 (stops shipping against
nothing), P1 (stops double charging from the client side), P3 (the big one — restructure the
retry and stop double charging at the acquirer), P4 (last, because it needs P3's surviving
records to have anything to reconcile).

---

## What the test suite tells you

`mvn test` passes with all five defects live.

`SimulatedPaymentGatewayTest` tests the *acquirer simulator* — that it declines `.11`,
approves ordinary amounts, and records the capture even when the response is lost. All true,
and none of it says anything about the code that calls it. It is a test of the test double.

`PaymentRepositoryTest` proves `findByStatusAndUpdatedAtBefore` finds stale `PROCESSING`
payments. It does. The defect is that the job which calls it then decides the wrong thing.

Nothing in the suite submits a payment, replays an idempotency key, triggers a retry, or runs
reconciliation. Every defect here lives in the orchestration between a transaction, a retry
policy, an external system and a state machine — and orchestration is exactly what unit tests
with a real collaborator on one side and no collaborator on the other cannot see.

The tests that would have caught these all look the same: submit a payment against the
sandbox, then assert on **three** things together — what the acquirer recorded, what the
`payments` table says, and what the `orders` table says. That triple assertion is the whole
discipline of payments testing.
