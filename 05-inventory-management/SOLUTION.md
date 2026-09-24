# SOLUTION — Vantage Supply Inventory Service

> **Answer key. Do not read this until you have finished investigating.**

Five defects. Two of them are invisible on a cold cache, which is why they are reported as
unreproducible.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| I1 | INV-407 | The cache is written inside the transaction, so a rollback leaves uncommitted data in Redis | `service/ReceivingService.java` |
| I2 | INV-411 | The cache key omits the warehouse, so all warehouses share one entry | `cache/StockCache.java` |
| I3 | INV-402 | Only positive adjustments write a movement row | `service/StockService.java` |
| I4 | INV-418 | `quantityAvailable` is set to on hand instead of on hand minus reserved | `mapper/InventoryMapper.java` |
| I5 | INV-424 | Over-receipt is checked per request, not cumulatively; order status is never checked | `service/ReceivingService.java` |

---

## I1 — A rejected receipt leaves phantom stock in the cache

### Symptom
A goods receipt is rejected with `422`. The database correctly rolls back. Redis keeps the
figure the receipt *would* have produced, and the per-warehouse endpoint serves it for the
next 15 minutes. The uncached network-wide endpoint reports the correct value at the same
time.

Reproduce:

```bash
docker exec lab-redis redis-cli FLUSHALL
curl -s -u "$WH" -X POST localhost:8080/api/purchase-orders/1/receipts \
  -H 'Content-Type: application/json' \
  -d '{"lines":[{"productId":1,"quantity":500},{"productId":5,"quantity":500}]}'
# 422 — only 150 of product 5 were ordered
docker exec lab-redis redis-cli GET 'inv:stock:1'   # 1920:180
# database still says 1420
```

### Root cause
`ReceivingService.receive` is `@Transactional` and processes lines in a loop. For each line
it updates the stock level, writes a movement, and then — still inside the loop, still
inside the transaction — writes the new figure to Redis:

```java
stockCache.write(line.getProduct().getId(), warehouse.getId(),
        level.getQuantityOnHand(), level.getQuantityReserved());
```

Line 1 completes and its cache write goes to Redis immediately. Line 2 fails the
over-receipt check and throws. Spring rolls back the database transaction. **Redis has no
idea a transaction exists**, so the line-1 write stands, holding a value that was never
committed anywhere.

The 15-minute TTL is why it "sorted itself out overnight" and then came back: the entry
expires, the next read repopulates it correctly from the database, and the next failed
receipt poisons it again.

### Exact location
`src/main/java/com/vantage/inventory/service/ReceivingService.java`, the `stockCache.write`
call inside the per-line loop.

### Correct fix
Move every cache mutation to **after** the transaction commits:

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        stockCache.evict(productId, warehouseId);
    }
});
```

Two decisions are worth making deliberately:

1. **Evict, do not write.** Writing after commit is still risky — two concurrent receipts
   can commit in one order and write their cache entries in the other, leaving the older
   value behind permanently. Eviction is idempotent and order-independent: whoever reads
   next repopulates from the database. Write-through is only safe when you also have a
   version or a lock to order the writes.
2. **Accept that eviction can still fail.** If Redis is down when `afterCommit` runs, the
   entry is stale until its TTL. That is why the TTL exists and why it should be short for
   data this important. A cache must always be able to heal itself.

The same change is needed in `StockService.adjust`, which evicts inside the transaction —
less dangerous, because a rollback merely causes an unnecessary cache miss, but it has the
mirror-image race: a concurrent read between the evict and the commit repopulates the cache
with the *old* value and it stays wrong until the TTL. Evicting after commit fixes both.

### Affected components
`ReceivingService`, `StockService`, `StockCache`, Redis, the picking handhelds.

### Underlying concept
**External systems do not participate in your database transaction.** Redis, Kafka, an email
send, an HTTP call to another service — none of them roll back. Any side effect on a
non-transactional system performed inside a transaction is a bet that the transaction will
commit, and that bet is lost every time something throws.

The general rule: **inside the transaction, only touch the database. Everything else goes
after commit.** Spring gives you `TransactionSynchronization.afterCommit`, or Spring events
with `@TransactionalEventListener(phase = AFTER_COMMIT)`, precisely for this.

The related rule for caches: **prefer invalidation to update.** An eviction is idempotent
and commutative; a write is neither.

### Why this is realistic
The cache write sits immediately after the stock update, which reads as good practice —
"change the number, refresh the cache, right there, so they cannot drift". The loop
structure means it is also correct-looking for the single-line receipts that make up most
traffic. Nothing about the line hints that it will outlive a rollback.

And the TTL turns it into an intermittent bug, which guarantees it gets closed as
unreproducible at least once.

### Detecting it faster next time
- **When two endpoints disagree about the same row, one of them is reading a cache.** Diff
  them, then go to the store that backs the stale one.
- `redis-cli MONITOR` while reproducing shows exactly when the application writes and what.
  It is the fastest diagnostic in the project.
- After any failed write operation, check the non-transactional systems, not the database.
  The database is the one thing you can trust to have rolled back.

### Prevention
- A rule, enforced in review: no calls to Redis, brokers or HTTP clients inside a
  `@Transactional` method — schedule them for after commit.
- An integration test that forces a rollback and asserts the cache is untouched.
- Short TTLs on cached business data, so any inconsistency is bounded.

---

## I2 — Every warehouse shares one cache entry

### Symptom
With a warm cache, `GET /api/stock/products/1/warehouses/2` returns warehouse 1's
quantities, while still reporting `"warehouseCode": "WH-MUM"`. All three warehouses return
whichever one was read first. On a cold cache the first read of each warehouse is correct,
which is why it works "first thing in the morning" and for a developer with an empty Redis.

### Root cause
```java
public String keyFor(Long productId, Long warehouseId) {
    return properties.getCache().getKeyPrefix() + productId;
}
```

The method takes `warehouseId` and never uses it. Every warehouse maps to `inv:stock:1` for
product 1, so the first warehouse read wins and serves everyone until the TTL expires.

The response still shows the correct warehouse code because `StockService.stockAt` loads the
`Warehouse` entity from the database for the response and takes only the *numbers* from the
cache. That mixture of fresh and stale data is what makes the output look credible.

### Exact location
`src/main/java/com/vantage/inventory/cache/StockCache.java`, `keyFor`.

### Correct fix
```java
public String keyFor(Long productId, Long warehouseId) {
    return properties.getCache().getKeyPrefix() + productId + ":" + warehouseId;
}
```

Flush existing keys after deploying, or the old shared entries keep serving until they
expire. Consider a version segment in the prefix (`inv:stock:v2:`) so a key-layout change
never has to rely on a manual flush.

### Affected components
`StockCache`, `StockService`, `ReceivingService`, Redis, every picking screen.

### Underlying concept
**A cache key must contain every input that the cached value depends on.** The value here
depends on `(productId, warehouseId)`; the key contains one of them. There is no type
system, no schema and no server-side check that will tell you — the store will happily give
one caller another caller's answer.

The unused parameter is the tell. **An argument that is accepted and not used in a key
builder is almost always a bug**, and it is the kind of thing a compiler warning or a linter
will flag if you let it.

Note also how the defect hides: the parts of the response that come from the database are
right, and only the cached numbers are wrong. Partial correctness is far more misleading
than total failure.

### Why this is realistic
`keyFor(productId, warehouseId)` was almost certainly written when stock was tracked per
product only, and the warehouse dimension was added later — the parameter was added to the
signature and every call site updated, and the one-line body was not. The method still reads
correctly at a glance because the signature says what it should do.

### Detecting it faster next time
- **Count keys.** Look up N distinct things and count what appears in Redis. If the numbers
  do not match, the key is wrong. Thirty seconds.
- `redis-cli KEYS 'prefix:*'` after exercising a feature tells you the layout you actually
  have, which is often not the layout you think you have.
- "Works on a cold cache" is a fingerprint: it means the bug is in the key or in
  invalidation, not in the query.

### Prevention
- Build keys in one place, from an explicit record of every input.
- A test that asserts two different inputs produce two different keys.
- Treat unused parameters as build failures.

---

## I3 — Negative adjustments leave no ledger row

### Symptom
Month-end reconciliation shows differences. Stock levels reduced by manual corrections have
no corresponding `stock_movements` row. Increases are recorded correctly.

Reproduce: adjust by `+15`, then by `-25`. On hand moves by `-10`; the ledger moves by
`+15`.

### Root cause
```java
level.setQuantityOnHand(updated);
level.setUpdatedAt(Instant.now());

if (delta > 0) {
    recordMovement(level.getProduct(), level.getWarehouse(),
            MovementType.ADJUSTMENT, delta, request.getReason(), "ADJ");
}
```

The stock level is updated unconditionally; the movement is written only when `delta > 0`.
Negative adjustments change the snapshot and leave no trace.

`stock_movements.quantity` is a signed integer and the seed data already contains a negative
`ADJUSTMENT` row, so the schema was always designed for this. The guard is the mistake, not
the data model.

### Exact location
`src/main/java/com/vantage/inventory/service/StockService.java`, the `if (delta > 0)` in
`adjust`.

### Correct fix
```java
recordMovement(level.getProduct(), level.getWarehouse(),
        MovementType.ADJUSTMENT, delta, request.getReason(), "ADJ");
```

Drop the condition entirely — a zero delta is already excluded by validation, and a signed
quantity is exactly what the ledger is for.

The structural fix is better: make it impossible to change a stock level without writing a
movement. Funnel every mutation through one method:

```java
private void applyMovement(StockLevel level, MovementType type, int signedQuantity, String reason) {
    level.setQuantityOnHand(level.getQuantityOnHand() + signedQuantity);
    level.setUpdatedAt(Instant.now());
    recordMovement(level.getProduct(), level.getWarehouse(), type, signedQuantity, reason, prefixFor(type));
}
```

Then `adjust` and `receive` both call it and neither can forget. A database trigger or a
nightly reconciliation alert is the belt-and-braces version.

### Affected components
`StockService`, `ReceivingService`, `stock_levels`, `stock_movements`, month-end
reconciliation.

### Underlying concept
**When a snapshot and a ledger both exist, they must be written together or not at all.**
Two representations of the same fact will diverge the moment one of them can be updated
alone. Either derive the snapshot from the ledger, or make a single code path responsible
for writing both.

Second: **guard clauses around side effects are where audit trails go to die.** Any
condition that decides whether to record something is a decision that the event sometimes
did not happen. For audit data the answer is always "record it".

### Why this is realistic
`if (delta > 0)` is the kind of line that gets added while thinking about a different
problem — perhaps "do not record a no-op adjustment", which would have been `delta != 0`.
It reads as a sanity check rather than as a decision about auditing.

The symptom also surfaces a month later, in a different team, as a number on a
reconciliation report. Nobody associates it with the adjustment screen.

### Detecting it faster next time
- **Reconcile the two representations directly.** The `GROUP BY` query in the README turns
  a vague "the numbers are wrong" into an exact product, warehouse and amount.
- The difference *is* the missing transaction. Look for a code path that produces changes of
  that sign or size.
- Grep every write to the snapshot field and check each one writes a ledger row. Two call
  sites, two checks.

### Prevention
- One method that owns "change stock", used everywhere.
- A scheduled reconciliation job that alerts on any non-zero difference, rather than finding
  out at month end.
- A test that adjusts up and down and asserts the invariant holds.

---

## I4 — Available quantity ignores reservations

### Symptom
`quantityAvailable` equals `quantityOnHand`. Order capture sells stock that is already
committed to another order.

### Root cause
```java
response.setQuantityOnHand(onHand);
response.setQuantityReserved(reserved);
response.setQuantityAvailable(onHand);
```

Three adjacent lines; the third should subtract the second. The correct value is never
computed anywhere, so every consumer of this API over-promises.

It also propagates: `InventoryMapper.toProductStock` sums `getQuantityAvailable()` across
warehouses for `totalAvailable`, so the network-wide total is wrong by the sum of all
reservations.

### Exact location
`src/main/java/com/vantage/inventory/mapper/InventoryMapper.java`, the `build` method.

### Correct fix
```java
response.setQuantityAvailable(onHand - reserved);
```

Better, put the rule in the domain rather than the mapper — a method on `StockLevel`, or a
computed property — so every caller gets it and no future mapper can restate it wrongly.

Consider whether `belowReorderLevel` should compare against available rather than on hand.
The current comparison uses on hand, which is defensible for replenishment but worth an
explicit decision rather than an accident.

### Affected components
`InventoryMapper`, every stock endpoint, order capture, the storefront.

### Underlying concept
**A derived field must be derived in exactly one place.** The moment a computation is
inlined into a response assembler, it is one copy-paste away from being wrong and no test of
the underlying data will catch it.

And: **a field that is sometimes correct is the hardest kind to spot.** When nothing is
reserved, `available == onHand` is right, and most rows in most systems have zero
reservations. The bug only shows on the rows that matter.

### Why this is realistic
The three lines are a block of near-identical setters. Copying the second into the third and
changing only the setter name is a classic mechanical slip, and the result reads fine
because `onHand` is a perfectly sensible-looking argument in that position.

The unit test in this project even asserts on `quantityOnHand` and `quantityReserved` and
never on `quantityAvailable` — which is how such a test gets written when the author is
thinking about mapping rather than about meaning.

### Detecting it faster next time
- When a consumer reports bad data, **check the arithmetic against the documented
  definition** before investigating the consumer. `API.md` states the rule.
- Find rows where the invariant can actually be violated — here, rows with a non-zero
  `quantity_reserved` — and test with those.
- Three setters in a row from two source values: read them character by character.

### Prevention
- Compute derived values in the domain object, not in mappers.
- Test the derivation with non-trivial inputs: reserved must be greater than zero.
- Write the definition in the API contract, and test against the contract.

---

## I5 — Over-receipt and receipts against closed orders

### Symptom
Two receipts of 600 against a line ordered for 1000 are both accepted; the line ends up with
`quantity_received = 1200`. A single receipt of 1200 is correctly refused. A delivery posted
against an order already marked `RECEIVED` is also accepted.

### Root cause
Two omissions in the same method.

**The over-receipt check compares the wrong quantities:**

```java
if (received.getQuantity() > line.getQuantityOrdered()) {
    throw new ReceiptRuleException(...);
}
```

This compares *this request* against the total ordered. It never consults
`line.getQuantityReceived()`, so it cannot see what earlier deliveries already brought in.
Each 600 passes on its own; the cumulative 1200 is never evaluated.

**The order status is never checked.** `receive` loads the order and goes straight into the
loop. `deriveStatus` is called at the end to set the new status, which makes the method look
status-aware while never reading the status on the way in.

### Exact location
`src/main/java/com/vantage/inventory/service/ReceivingService.java` — the over-receipt
condition, and the missing status guard at the top of `receive`.

### Correct fix
```java
if (order.getStatus() == PurchaseOrderStatus.RECEIVED
        || order.getStatus() == PurchaseOrderStatus.CANCELLED) {
    throw new ReceiptRuleException(order.getPoNumber() + " is " + order.getStatus()
            + " and cannot accept further deliveries");
}
```

and

```java
int alreadyReceived = line.getQuantityReceived();
int afterThisReceipt = alreadyReceived + received.getQuantity();
if (afterThisReceipt > line.getQuantityOrdered()) {
    throw new ReceiptRuleException("Cannot receive " + received.getQuantity() + " of "
            + line.getProduct().getSku() + "; " + alreadyReceived + " of "
            + line.getQuantityOrdered() + " already received against " + order.getPoNumber());
}
```

If the business wants to allow a small over-receipt, `inventory.receiving.over-receipt-tolerance-percent`
already exists in the configuration and is currently ignored — wiring it in is the honest
implementation of that intent.

Worth noting: a receipt has no idempotency key, so a retried HTTP request posts the goods
twice. Cumulative checking limits the damage but does not prevent a legitimate-looking
duplicate. A client-supplied delivery-note reference, unique per order, would.

### Affected components
`ReceivingService`, `PurchaseOrderLine`, `PurchaseOrder`, `stock_levels`,
`stock_movements`, supplier invoice matching.

### Underlying concept
**A cumulative limit must be checked cumulatively.** Validating each request against the
total is a standard shape of mistake in anything that arrives in instalments — part
deliveries, partial refunds, instalment payments, rate limits. The correct test is always
*existing + incoming ≤ limit*.

**A state machine needs guards on entry, not only transitions on exit.** Computing the new
status at the end is not the same as refusing to act in the current one.

### Why this is realistic
The check is present, it references the right line, and it fires for the obvious test case.
It was almost certainly written when receipts were assumed to be one-shot; partial deliveries
were added later (that is why `quantityReceived` accumulates with `+=`), and the check was
not revisited.

The consequence is expensive rather than merely wrong: over-received stock is stock the
company may be invoiced for.

### Detecting it faster next time
- **Any limit on a repeatable operation: try it twice.** Half the limit, twice over. This
  finds the entire class in one request pair.
- Read validation conditions asking "what does this compare against, and is that the whole
  history or just this call?"
- For state machines, check both directions: can this operation run in every status it
  should, and is it refused in every status it should not be?

### Prevention
- Express the limit as an invariant on the entity — `quantityReceived <= quantityOrdered` —
  and enforce it in one place, ideally with a database check constraint as well.
- Tests that exercise instalments, not just single shots.
- Idempotency keys on any endpoint that mutates stock or money.

---

## How these interact

**I1 and I2 are both cache defects and they mask each other.** With one shared key (I2),
the phantom value from a rolled-back receipt (I1) is served to *every* warehouse, not just
the one that was being received into. Fix I2 first and I1 looks smaller — the wrong number
is confined to one warehouse — which can read as progress on the wrong ticket.

**I1 and I3 both break the same promise from different sides.** I3 makes the database
internally inconsistent; I1 makes the cache inconsistent with the database. A reconciliation
report that reads the database will catch I3 and be blind to I1, and a supervisor looking at
a handheld will see I1 and be blind to I3.

**I5 supplies the failure that I1 needs.** The over-receipt rejection is the most likely way
a receipt transaction rolls back in normal operation, which is exactly how INV-407 was
produced. Fix I5 and the phantom-cache symptom becomes rarer without I1 being fixed at all —
another correlation waiting to be mistaken for a cause.

**I4 is independent of everything.** Not every defect is connected, and recognising an
isolated one quickly is as valuable as connecting the others.

**Suggested fix order:** I4 (isolated, one line, stops over-selling now), I3 (stops ongoing
ledger corruption), I5 (stops over-receipts), I2 (key layout — remember to flush), I1 (the
transactional side-effect, and the one worth the most thought).

---

## What the test suite tells you

`mvn test` passes with all five defects live.

`InventoryMapperTest` asserts on `quantityOnHand`, `quantityReserved` and
`belowReorderLevel` — every field except the derived one that is wrong.
`StockLevelRepositoryTest` proves the repository can find one level per product and
warehouse, which it can; the defect is in the cache key that sits in front of it.

Neither test starts Redis, forces a rollback, posts two receipts, or compares a snapshot
against a ledger. Every defect in this project lives in an interaction — between a
transaction and an external store, between a key and its inputs, between a snapshot and a
ledger, between two requests in sequence — and interactions are exactly what unit tests with
mocks are designed not to see.
