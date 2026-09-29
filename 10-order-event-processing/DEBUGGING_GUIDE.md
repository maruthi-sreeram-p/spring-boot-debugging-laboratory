# Debugging Guide — Riverstone Order Event Processing

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

---

## 1. Business context

Riverstone sells hardware. The order flow was rebuilt last quarter as an event pipeline so
that the three things an order needs — stock reserved, customer told, status updated —
could scale and fail independently.

The topology on paper:

```
order accepted ──► orders.created ──► inventory ──► inventory.events ──┬─► notifications
                                                                       └─► order status
```

Three teams own the three consumers. They deploy separately, they scale separately, and
they were told the pipeline would let them do that.

What matters to the business:

- **Fulfilment** ships on `CONFIRMED`. A confirmed order must have stock actually reserved
  behind it.
- **Customers** expect a confirmation. Silence after an order is placed generates support
  tickets.
- **Finance** reconciles `stock_items` against `reservations` monthly. They must agree.
- **The platform team** sized the topics at three partitions and the consumers at three
  threads so the pipeline could handle a launch.

---

## 2. How the system is supposed to behave

**Every accepted order produces all three effects.** A reservation decision, a notification,
and a final status — for every order, every time, within a couple of seconds.

**Each downstream service sees every inventory event.** Notifications and order status are
two separate concerns run by two separate teams. Both need every event; neither should ever
see only some of them.

**Events about one order are processed in order.** Kafka guarantees ordering within a
partition, so the pipeline places everything about one order on one partition.

**The pipeline uses the capacity it was given.** Three partitions and three consumer threads
exist so that work can happen in parallel. Traffic should spread across them.

**An event that cannot be processed is not silently abandoned.** If a record fails, it is
retried and then set aside somewhere a human can find it — not dropped, and not left to
block the pipeline.

**Records and events agree.** A `CONFIRMED` order has a reservation row and a matching
movement in `stock_items`. An event that says stock was reserved was published by a
transaction that actually reserved it.

---

## 3. Symptoms

Nobody has told you how many distinct defects there are.

---

### Ticket RS-901 — "Customers never get confirmation emails"

> Filed by: Customer support
>
> We have had no confirmation emails since the pipeline went live. Not a small number — I
> mean the `order_notifications` table has **zero rows**.
>
> The notification service is running. It is connected. It logs that it started and
> subscribed. It has simply never processed a single event.
>
> Order statuses are updating perfectly, so the events are definitely being produced.

---

### Ticket RS-908 — "We paid for three partitions and use one"

> Filed by: Platform team
>
> We sized `orders.created` at three partitions and run three consumer threads so we could
> handle the launch. I have been looking at the event log.
>
> Every single event is on partition 2. All of them. Seventy-two events, one partition.
>
> Throughput under load is exactly what one thread can do. Adding threads changes nothing,
> because there is nothing for them to be assigned to.
>
> Curiously, `inventory.events` **does** spread across all three partitions. Same cluster,
> same topic config, same application.

---

### Ticket RS-915 — "An order has been CREATED for two days"

> Filed by: Fulfilment
>
> Order for a discontinued line, still sitting at CREATED. Nothing has happened to it since
> it was placed.
>
> The log has a burst of the same error ten times in a row, about two seconds apart, and
> then nothing. It has been quiet since.
>
> There is no reservation and no notification. The order is not rejected either — it is just
> stuck. Where did the event go?

---

### Ticket RS-921 — "Confirmed order, no stock reserved"

> Filed by: Finance, month-end reconciliation
>
> An order for a pre-release line shows CONFIRMED. There is no row in `reservations` for it
> and the stock counts for that SKU have not moved at all — available is exactly what it was
> before.
>
> The event log for that order is stranger. It shows the inventory consumer handling the
> order created event **ten times**, and the order status consumer handling ten separate
> "reserved" events.
>
> One order. Ten claims that stock was reserved. Zero reservations.

---

## 4. Investigation hints

Read **one** hint at a time and go back to the code before reading the next.

The single most useful command in this project:

```bash
docker exec -it lab-kafka bash -lc "/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group order-pipeline"
```

---

### RS-901 — one consumer has never run

**Hint 1.** The notification consumer is subscribed and idle, and the status consumer is
doing all the work. Ask what Kafka does when two consumers subscribe to the same topic, and
what decides whether they both get every message.

**Hint 2.** Run the `--describe --group` command above, and also `--list` all groups. Count
the groups. Compare that with the number of independent services in the README diagram.

**Hint 3.** Look at the `@KafkaListener` annotation on each downstream consumer and compare
the one attribute that decides which group a consumer joins.

**Hint 4.** Once you see it, work out why the split is total rather than roughly half. How
many consumer threads does each listener create, how many partitions are there, and — given
RS-908 — how many of those partitions actually carry traffic?

---

### RS-908 — everything on one partition

**Hint 1.** The platform team gave you the key observation: `orders.created` uses one
partition and `inventory.events` uses three. Same broker, same application. So the
difference is in how each one is **produced**.

**Hint 2.** Find the two places that publish. Put the two `send(...)` calls side by side and
compare their arguments.

**Hint 3.** Read the Kafka facts section of the README. What does a producer do when it is
not given a key, and why does that not spread evenly at low volume?

**Hint 4.** Before you fix it, decide *what* the key should be. Think about which events must
be ordered relative to each other, and check what the other producer chose.

**Hint 5.** After you fix it, run the burst script and watch the stock arithmetic:

```bash
python scripts/burst_orders.py --sku RS-WIDGET-01 --count 40
```

The pipeline is about to start doing genuinely concurrent work for the first time. Something
that has never been exercised is about to be.

---

### RS-915 — the stuck order

**Hint 1.** Reproduce it with `RS-POISON-01`, then read the event log for that order. Count
the rows and compare with the number of errors in the application log.

**Hint 2.** Ten attempts then silence is a specific, documented default. Find which component
produced it, and what that component does when the attempts run out.

**Hint 3.** Now look at the first line of the consumer method, before any work is done. What
does it tell the broker, and when?

**Hint 4.** Work out the two separate hazards here. What happens to this record after the
retries are exhausted, given there is nowhere for it to go? And separately — what would
happen if the application were restarted in the middle of those ten attempts, given what the
first line already did?

**Hint 5.** Look up what Spring Kafka offers for records that cannot be processed, and where
the acknowledgement belongs relative to the work.

---

### RS-921 — confirmed with nothing behind it

**Hint 1.** Reproduce it with `RS-GHOST-01`. Then check three places: `reservations`,
`stock_items`, and the event log.

**Hint 2.** Read the inventory consumer from top to bottom and write down the order of
operations. Mark which ones are database writes and which one is not.

**Hint 3.** The handler is `@Transactional`. Work out which of those operations a rollback
undoes and which it does not.

**Hint 4.** Now explain the ten. Combine what you learned in RS-915 about retries with what
the handler does on each attempt.

**Hint 5.** This is a well-known problem with a well-known name — writing to a database and
a message broker in one logical operation, where only one of them can be rolled back. Look
up what pattern is normally used to make those two writes agree, and what it would mean
here.

---

## 5. Before you call it fixed

- **RS-901:** place five orders. Every one must end with a notification **and** a status
  update. Check `--describe` for both groups and confirm each has its own members and
  partitions.
- **RS-908:** place twenty orders and check the partitions used in the event log. Traffic
  must spread. Confirm that events for a single order reference still all share one
  partition — spreading load must not cost you ordering.
- **RS-915:** order `RS-POISON-01`. After the retries, the record must be somewhere you can
  find it rather than gone, and the pipeline must keep processing other orders.
- **RS-921:** order `RS-GHOST-01`. There must be **no** `RESERVED` event for it at all, the
  order must not be `CONFIRMED`, and the stock counts must be untouched.
- Then run the burst script and confirm the stock arithmetic still balances:

```bash
python scripts/burst_orders.py --sku RS-WIDGET-01 --count 40 --quantity 1
```

```bash
docker exec -it lab-postgres psql -U labuser -d ordereventsdb -c "
SELECT s.sku, s.reserved, COALESCE(SUM(r.quantity),0) AS reservation_rows
  FROM stock_items s LEFT JOIN reservations r ON r.sku = s.sku
 GROUP BY s.sku, s.reserved HAVING s.reserved <> COALESCE(SUM(r.quantity),0);"
```

That query must return nothing, before and after the burst.

- Confirm no order is left in `CREATED` once the pipeline has drained.
- Re-run `mvn test`.

When you are done, ask for **verification mode** and I will check your work.
