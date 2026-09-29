# SOLUTION — Riverstone Order Event Processing

> **Answer key. Do not read this until you have finished investigating.**

Four defects, all deterministic, plus one latent hazard that only becomes reachable once K2
is fixed — which is the most important thing in this file.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| K1 | RS-901 | Both downstream consumers share one consumer group, so one of them starves | `messaging/NotificationConsumer.java` + `messaging/OrderStatusConsumer.java` |
| K2 | RS-908 | `orders.created` is published with no message key | `service/OrderService.java` |
| K3 | RS-915 | The offset is acknowledged before the work, and there is no dead-letter recovery | `messaging/InventoryConsumer.java` |
| K4 | RS-921 | The event is published inside the transaction that reserves the stock | `messaging/InventoryConsumer.java` |
| — | (latent) | Unguarded read-modify-write on `stock_items`, masked today by K2 | `messaging/InventoryConsumer.java` |

---

## K1 — The notification service has never processed an event

### Symptom
`order_notifications` is empty. Across 62 consumed inventory events, **every one** was
handled by `OrderStatusConsumer` and **none** by `NotificationConsumer`. The notification
consumer starts, subscribes, and does nothing forever.

```
 handled_by          | count
---------------------+-------
 OrderStatusConsumer |    62
```

### Root cause
Both downstream listeners join the same group:

```java
// OrderStatusConsumer
@KafkaListener(topics = "${riverstone.topics.inventory-events}",
               groupId = "${riverstone.groups.downstream}")

// NotificationConsumer
@KafkaListener(topics = "${riverstone.topics.inventory-events}",
               groupId = "${riverstone.groups.downstream}")
```

Both resolve to `order-pipeline`. A consumer group exists precisely to **divide** a topic's
partitions among its members so each message is processed once by the group. Two logically
independent services in one group are not two subscribers — they are two workers competing
for the same messages.

Why the split is total rather than roughly half: `spring.kafka.listener.concurrency: 3`
means each `@KafkaListener` creates three consumer threads, so the group has **six**
members for **three** partitions. Three members get a partition each and three get nothing.
Which three is decided by the group coordinator at rebalance, and here all three winners
happened to be `OrderStatusConsumer` threads. That is why the outcome looks like a
deliberate design rather than a race — and why a restart could silently change which service
works and which starves.

### Exact location
The `groupId` attribute on the `@KafkaListener` in
`src/main/java/com/riverstone/orderevents/messaging/NotificationConsumer.java` and
`.../OrderStatusConsumer.java`, both pointing at `riverstone.groups.downstream`.

### Correct fix
Give each service its own group. In `RiverstoneProperties`:

```java
public static class Groups {
    private String inventory = "inventory-service";
    private String notification = "notification-service";
    private String orderStatus = "order-status";
}
```

```yaml
riverstone:
  groups:
    inventory: inventory-service
    notification: notification-service
    order-status: order-status
```

```java
@KafkaListener(topics = "...", groupId = "${riverstone.groups.notification}")   // NotificationConsumer
@KafkaListener(topics = "...", groupId = "${riverstone.groups.order-status}")   // OrderStatusConsumer
```

Two consequences to plan for:

- New groups start from `auto-offset-reset: earliest`, so the notification consumer will
  process the entire backlog on first start. That is probably what you want here; be aware
  of it, because on a large topic it is a stampede.
- With three partitions and `concurrency: 3`, each group now has exactly one thread per
  partition. Threads beyond the partition count are always idle — `concurrency` above the
  partition count buys nothing.

### Affected components
Both downstream consumers, `RiverstoneProperties`, `application.yml`, and every customer who
never got an email.

### Underlying concept
**A consumer group is a unit of work-sharing, not of subscription.** Publish-subscribe
across services is achieved by *separate groups*; parallelism within a service is achieved
by *more members in one group*. Confusing the two is the most common Kafka mistake there is,
and it produces a failure that looks like a dead service rather than a configuration error.

The related trap is the **partition ceiling**. A group can never usefully have more active
members than the topic has partitions. Scaling a consumer past the partition count adds
idle threads, and "we added consumers and throughput did not move" is the classic symptom.

### Why this is realistic
`riverstone.groups.downstream` reads like a sensible name for "the downstream stage of the
pipeline", and the two consumers genuinely are the downstream stage. The property was
plausibly added when there was only one downstream consumer, and the second one reused it
because it was there and the name still fitted.

The total starvation rather than a 50/50 split is what makes the report confusing: support
reported a dead feature, not an intermittent one, so nobody thought about message
distribution.

### Detecting it faster next time
- **`kafka-consumer-groups.sh --describe --group <name>` is the first command for any
  "consumer not receiving" report.** It shows every member and the partitions it owns. A
  member with no partitions is a consumer that will never work.
- `--list` the groups and compare the count against the number of services that should be
  subscribing. Fewer groups than services means someone is sharing.
- Log the group id at startup in each consumer. It costs one line and makes this visible
  without a broker CLI.

### Prevention
- One group per service, named after the service, never after the pipeline stage.
- Assert the mapping in configuration review: a shared `groupId` between two classes is
  almost always a bug.
- Alert on a consumer group whose members hold zero partitions.

---

## K2 — Every event lands on one partition

### Symptom
All 72 `orders.created` events were consumed from partition 2. The event log shows
`messageKey: null` for every one of them. Throughput equals one thread regardless of how
many are configured. `inventory.events`, produced by the same application to the same
cluster, spreads correctly across partitions 0, 1 and 2.

### Root cause
`OrderService` publishes without a key:

```java
kafkaTemplate.send(properties.getTopics().getOrdersCreated(), event);
```

`InventoryConsumer` publishes with one:

```java
kafkaTemplate.send(properties.getTopics().getInventoryEvents(), event.getOrderRef(), event);
```

When a key is present Kafka hashes it to pick a partition, which both spreads load and
guarantees that everything sharing a key stays in order. When the key is null the producer
chooses, and modern clients use a **sticky partitioner**: they keep filling one partition
until a batch is dispatched, then may switch. At this volume batches never fill, so the
producer stayed on partition 2 indefinitely.

Two things are broken by this, and only one of them is visible today:

1. **No parallelism.** Two of three partitions are empty, so two of three consumer threads
   are idle.
2. **No ordering guarantee per order.** Today each order produces one event, so nothing is
   out of order. The moment a second event about an order exists — a cancellation, an
   amendment, a replay — it can land on a different partition and be processed concurrently
   with, or before, the first.

### Exact location
`src/main/java/com/riverstone/orderevents/service/OrderService.java`, the
`kafkaTemplate.send(...)` in `createOrder`.

### Correct fix
```java
kafkaTemplate.send(properties.getTopics().getOrdersCreated(), saved.getOrderRef(), event);
```

Match the other producer and key by order reference, so all events about one order share a
partition.

Choosing the key deliberately matters. The key defines your **unit of ordering** and your
**unit of parallelism** at the same time:

- Key by `orderRef` — everything about one order is ordered. Best spread. This is right
  here, because the ordering requirement is per order.
- Key by `sku` — everything touching one stock row is serialised on one partition, which
  would also remove the concurrency hazard described below. But it creates hot partitions
  for popular SKUs and couples unrelated orders together.

Key by `orderRef` and solve the stock race with a lock; do not solve a locking problem with
partitioning.

### Affected components
`OrderService`, the `orders.created` topic, the inventory consumer's parallelism, and every
future event type on that topic.

### Underlying concept
**The message key is the ordering contract.** Kafka guarantees ordering within a partition
and nothing across partitions, so "which partition" is a correctness decision, not a
performance one. A null key means you have opted out of ordering entirely.

And **a null key does not mean round-robin.** That is the intuition most people carry, and
it is wrong for modern clients: the sticky partitioner deliberately concentrates messages to
build efficient batches. At low volume that looks like a stuck producer.

### Why this is realistic
`kafkaTemplate.send(topic, payload)` is the shortest overload and the one every example
shows. It compiles, it works, and nothing about it suggests a missing argument. The bug has
no error and no symptom until someone looks at partition distribution — which nobody does
until throughput disappoints.

The contrast with the correctly-keyed producer in the same codebase is the realistic detail:
one developer knew, the other used the simpler overload.

### Detecting it faster next time
- **Print the partition and key.** The console consumer with `--property print.key=true
  --property print.partition=true` answers this in one command, and this project's event log
  answers it in one query.
- "We added partitions/consumers and throughput did not change" is almost always either a
  missing key or more consumers than partitions. Check both.
- `kafka-consumer-groups.sh --describe` shows lag per partition. Lag on one partition and
  zeros elsewhere is this defect.

### Prevention
- Make the key a required argument in a small publisher wrapper, so `send` without one is
  not expressible.
- Document the key for every topic alongside its schema.
- A test that publishes several events and asserts they are distributed, and that events
  sharing a key share a partition.

---

## K3 — The stuck order, and where its event went

### Symptom
An order for a discontinued line sits at `CREATED` indefinitely. The log shows the same
failure ten times, roughly two seconds apart, then silence. No reservation, no notification,
no rejection. The event log records ten handling attempts for that order.

### Root cause
Two problems compound.

**1. The offset is acknowledged before any work is done.**

```java
public void onOrderCreated(ConsumerRecord<String, OrderCreatedEvent> record, Acknowledgment acknowledgment) {
    acknowledgment.acknowledge();      // first statement
    ...
```

With `ack-mode: manual_immediate`, this commits the offset immediately — before the record
has been processed and before anyone knows whether it can be. The record is, from the
broker's point of view, done.

**2. There is no dead-letter recovery.**

The ten attempts come from Spring Kafka's `DefaultErrorHandler`, whose default is a
`FixedBackOff` of ten attempts. It reacts to the exception by seeking the consumer back to
the failed record — which is why the message is retried at all despite the premature
acknowledgement. When the attempts are exhausted and no recoverer is configured, it logs and
**skips the record**. The event is gone.

The premature acknowledgement is the more dangerous half even though it is not what you see:
during those ten attempts the offset is already committed, so a restart, a rebalance or a
crash at any point in that window drops the record instantly, with no retries and no log
line to say so.

### Exact location
`src/main/java/com/riverstone/orderevents/messaging/InventoryConsumer.java` — the
`acknowledgment.acknowledge()` at the top of `onOrderCreated`, and the absence of any error
handler bean.

### Correct fix
Move the acknowledgement to the end, after the work has succeeded:

```java
public void onOrderCreated(ConsumerRecord<String, OrderCreatedEvent> record, Acknowledgment acknowledgment) {
    OrderCreatedEvent event = record.value();
    ...
    acknowledgment.acknowledge();     // last statement, only on success
}
```

and give failures somewhere to go:

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
            (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
}
```

Three attempts with a second between them, then the record is published to
`orders.created.DLT` where it can be inspected and replayed. Add
`ExponentialBackOff` if the failures you expect are load-related rather than
data-related.

Worth distinguishing while you are here: a *permanently* bad record (a discontinued SKU)
should not be retried at all — `DefaultErrorHandler.addNotRetryableExceptions(...)` sends it
straight to the DLT. Retrying a record that can never succeed wastes the partition's
throughput for the duration.

### Affected components
`InventoryConsumer`, the `orders.created` topic, every order behind a poison record in the
same partition, and the on-call engineer.

### Underlying concept
**An acknowledgement is a promise that the work is done.** Committing an offset before
processing converts at-least-once delivery into at-most-once, silently. The ordering is the
whole point of manual acknowledgement mode — moving the call to the top removes the only
benefit it offers over auto-commit while keeping all of the complexity.

**And a consumer needs a terminal destination for records it cannot process.** Without one,
the choice is between blocking the partition forever and discarding the record. Both are
bad; a dead-letter topic is how you get a third option.

Note also what this does to the partition: while the poison record is being retried, **every
other message on that partition waits**. Combined with K2, where every message is on one
partition, a single bad record stalls the entire pipeline.

### Why this is realistic
`acknowledgment.acknowledge()` at the top of a handler is a surprisingly common pattern. It
appears when someone reasons "I have received it, so acknowledge receipt" — which is how
acknowledgement works in some other systems and in ordinary English. The code then works
perfectly for every record that succeeds, which is almost all of them.

The absence of an error handler is invisible by construction: it is a bean that is not
there. Spring supplies a default that retries ten times and gives up, so the application
behaves reasonably enough in development that nobody notices the gap.

### Detecting it faster next time
- **A burst of identical errors followed by silence is a retry budget being exhausted.**
  Count them: ten is Spring Kafka's default and worth recognising on sight.
- Check consumer lag against the data. Lag at zero while records are unprocessed means the
  offsets were committed without the work being done.
- For any manual-ack consumer, the first thing to check is *where* the ack call sits.

### Prevention
- Acknowledge last, always, and make it the final line of the method.
- Configure an error handler with a bounded backoff and a dead-letter recoverer as part of
  the baseline setup, not per consumer.
- Alert on DLT depth, and on partitions whose lag stops falling.

---

## K4 — Confirmed with nothing behind it, ten times over

### Symptom
An order for a pre-release line is `CONFIRMED`, with no row in `reservations` and no movement
in `stock_items`. The event log shows the inventory consumer handling the order **ten times**
and the status consumer handling **ten separate `RESERVED` events**. One order, ten claims
that stock was reserved, zero reservations.

### Root cause
`InventoryConsumer.onOrderCreated` is `@Transactional` and interleaves database writes with a
Kafka publish:

```java
item.setAvailable(item.getAvailable() - event.getQuantity());   // database
item.setReserved(item.getReserved() + event.getQuantity());     // database
reservationRepository.save(reservation);                        // database

publish(new InventoryEvent(..., "RESERVED", ...));              // Kafka — not transactional

confirmAllocationPlan(item);                                    // throws for pre-release lines
```

The transaction rolls back and undoes all three database writes. **Kafka has no idea a
transaction existed.** The `RESERVED` event is already on the topic, permanently, and the
downstream consumers act on it: the status consumer confirms the order, the notification
consumer would have told the customer.

The ten is K3 compounding: the error handler retries ten times, and each attempt publishes
another event before failing again. One order produced ten phantom reservation events.

This is the **dual write** problem: two systems written in one logical operation, where only
one of them can be rolled back.

### Exact location
`src/main/java/com/riverstone/orderevents/messaging/InventoryConsumer.java`, the `publish(...)`
call inside the transactional handler, before `confirmAllocationPlan`.

### Correct fix
The minimal, immediate fix is to publish only after the transaction has committed:

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        publish(inventoryEvent);
    }
});
```

or the same thing via a Spring event with
`@TransactionalEventListener(phase = AFTER_COMMIT)`. Nothing is published unless the
reservation actually committed.

That closes the "phantom event" hole but leaves the opposite one: if the process dies between
commit and publish, the stock is reserved and nobody downstream ever hears. The standard
answer is the **transactional outbox**:

1. In the same transaction as the reservation, insert the event into an `outbox` table.
2. A separate relay reads unpublished outbox rows and publishes them to Kafka, marking them
   sent.

Now there is only one transactional write, so the database and the event can never disagree.
Publishing at least once is acceptable because consumers should be idempotent anyway — which
is the other half of this design, and which this pipeline does not yet have.

Kafka transactions (`KafkaTransactionManager` plus a chained transaction manager) are
sometimes offered as an alternative. They do not actually solve it: they make the Kafka
write atomic with *other Kafka writes*, not with your database.

Also move `confirmAllocationPlan` to the top of the handler. A validity check that runs after
the work is a check in the wrong place regardless of anything else here.

### Affected components
`InventoryConsumer`, `inventory.events`, both downstream consumers, `stock_items`,
`reservations`, and month-end reconciliation.

### Underlying concept
**A database transaction cannot span a message broker.** Anything published inside a
transaction is published whether or not that transaction commits. The rule is the same one
as for caches, emails and HTTP calls: inside the transaction, touch only the database;
everything else goes after commit, or through an outbox.

The second lesson is about **retries multiplying non-idempotent side effects**. Each retry
was a fresh attempt that got as far as publishing before failing, so the retry policy turned
one phantom event into ten. Retry is only safe over operations that can be repeated
harmlessly; an unconditional publish is not one.

### Why this is realistic
The publish sits exactly where a reader expects it — the stock has been reserved, so tell
everyone. The validation that fails afterwards was added later, by someone tightening the
rules for pre-release lines, who put the check at the end because that is where the
allocation is finally known. Neither change is wrong on its own; the ordering that results
is.

And it only misbehaves on the failure path, which is the path nobody demos.

### Detecting it faster next time
- **When downstream acts on something upstream does not have, look for a publish inside a
  transaction.** Grep for `send(` or `convertAndSend(` in any `@Transactional` method.
- Compare event counts against row counts. Ten events and zero rows is unambiguous.
- After any rolled-back operation, check the non-transactional systems. The database is the
  one thing you can trust to have rolled back.

### Prevention
- No broker, cache or HTTP calls inside a transaction — schedule them for after commit.
- Use an outbox wherever a database write and an event must agree.
- Make consumers idempotent, so at-least-once publication is safe.
- A reconciliation query comparing `stock_items.reserved` against the sum of `reservations`.

---

## The latent defect that K2's fix exposes

This is the most valuable part of this project, and it is not in the tickets.

`InventoryConsumer` performs an unguarded read-modify-write on a shared row:

```java
StockItem item = stockItemRepository.findBySku(event.getSku()).orElseThrow(...);
...
item.setAvailable(item.getAvailable() - event.getQuantity());
item.setReserved(item.getReserved() + event.getQuantity());
```

There is no `@Version`, no pessimistic lock, and no relative update. Two consumer threads
processing orders for the same SKU concurrently will lose one of the updates.

**Today this cannot happen**, and I verified it: 40 concurrent orders for one SKU produced
perfectly consistent stock arithmetic. The reason is K2. With every event on a single
partition, exactly one consumer thread ever runs, so the handler is effectively serialised by
the broker. The absence of parallelism is hiding a concurrency bug.

Fix K2 — add the message key, spread events across three partitions — and three consumer
threads start processing concurrently for the first time. Run the burst script then:

```bash
python scripts/burst_orders.py --sku RS-WIDGET-01 --count 40
```

and the stock will drift. A developer who fixes the partitioning, deploys, and then sees
inventory go wrong will naturally blame the change they just made. The change was correct;
it removed an accident that was compensating for a real defect.

The fix is a lock, not a partitioning choice:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from StockItem s where s.sku = :sku")
Optional<StockItem> findBySkuForUpdate(@Param("sku") String sku);
```

or an atomic relative update (`set available = available - :qty where sku = :sku and
available >= :qty`), which is cheaper and also enforces the non-negative constraint.

**The general lesson:** a performance defect can mask a correctness defect, and fixing the
performance defect is what surfaces it. Before you remove a bottleneck, ask what that
bottleneck has been serialising for you.

---

## How these interact

**K2 masks the concurrency hazard** described above — the single most important interaction
here, and the one that will bite in production rather than in the lab.

**K2 amplifies K3.** With every message on one partition, a poison record being retried
blocks *every* other order behind it. Spread the load across three partitions and one bad
record stalls a third of the pipeline instead of all of it.

**K3 amplifies K4.** The ten retries turned one phantom `RESERVED` event into ten. Fix the
retry policy and the damage shrinks; fix the dual write and it disappears.

**K1 hides half of K4's blast radius.** Because the notification consumer never ran, nobody
was told their order was confirmed when it was not. Fix K1 and the same defect starts
emailing customers about orders that have no stock behind them — worse, not better, which is
another instance of a fix making a symptom louder.

**Suggested fix order:** K1 (isolated, restores a dead feature), K4 (stops phantom events
reaching a now-working notification consumer), K3 (acknowledgement and dead-lettering), then
K2 **together with** the locking fix — because K2 alone releases the concurrency hazard and
you do not want those two in separate deploys.

---

## What the test suite tells you

`mvn test` passes with all four defects live.

`PipelineMapperTest` maps an order and an event-log row built by hand. `OrderRecordRepositoryTest`
proves orders can be found by reference and status. Both are correct and neither can observe
a single defect in this project, because **all four live in the space between a producer, a
broker and a consumer** — a partition assignment, a group membership, an offset commit, and
a transaction boundary that a message crossed.

No unit test can see any of that. What would have caught these is an integration test with a
real broker (Testcontainers has a Kafka module), asserting things unit tests cannot express:

- that two consumer groups each receive every message;
- that events sharing a key share a partition, and that traffic uses every partition;
- that a record which always fails ends up on a dead-letter topic and stops being retried;
- that a handler which rolls back publishes nothing;
- and that N concurrent orders for one SKU move the stock by exactly N units.

That last one is the test that would have caught the latent defect — and it is the test you
should write *before* fixing K2, not after.
