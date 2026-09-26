# Debugging Guide — Spicebox Food Ordering Service

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

---

## 1. Business context

Spicebox is a food delivery marketplace. A customer picks a restaurant, builds a basket and
checks out. From that moment the order is in the hands of three parties who never talk to
each other directly:

- **The restaurant** sees the order on a tablet, accepts it, cooks it, and marks it ready.
- **Dispatch** sends a rider once an order is ready, and the customer's tracking screen
  starts moving.
- **The customer** gets a confirmation and watches the status.

None of that happens on the checkout request. Checkout writes the order and returns; every
step after it is asynchronous. That is deliberate — nobody wants the pay button to wait on
a mail gateway — but it means **a failure after checkout is invisible to the customer and
invisible to the HTTP response.** The only place those failures surface is the log and the
state of the data.

---

## 2. How the system is supposed to behave

**One order, one kitchen.** Every item in an order comes from the same restaurant. A basket
that mixes two restaurants is rejected at checkout; the customer places two orders.

**Checkout is fast and the rest catches up within seconds.** After `POST /api/orders`
returns `201`, three things happen in the background and all three should be done almost
immediately:

1. Kitchen intake moves the order `PLACED → ACCEPTED`.
2. The `order.placed` event reaches the analytics queue.
3. A confirmation row appears in `order_notifications`, addressed to the customer who
   placed the order.

**The status track is a track, not a set of labels.** `PLACED → ACCEPTED → PREPARING →
READY_FOR_PICKUP → OUT_FOR_DELIVERY → DELIVERED`. One step forward at a time. No skipping,
no going backwards, and `DELIVERED` is final. `CANCELLED` and `REJECTED` are early exits.

**Marking an order ready summons a rider.** When the kitchen sets `READY_FOR_PICKUP`, the
dispatch service receives it and moves the order to `OUT_FOR_DELIVERY` by itself.
Operations never set that status by hand.

---

## 3. Symptoms

Nobody has told you how many distinct defects there are.

---

### Ticket SPX-520 — "The tablet never rings"

> Filed by: Restaurant operations
>
> Curry Leaf called to say they have had no orders all morning. Customers are ordering — the
> orders are in the database, the customers have been charged, the app shows them as
> placed.
>
> The intake queue screen has 140 orders on it. Every one of them is still PLACED. Nothing
> has moved to ACCEPTED since we deployed on Tuesday.
>
> Nothing failed. The checkout API returns 201 every time.

---

### Ticket SPX-527 — "No confirmation emails"

> Filed by: Customer support
>
> Customers have stopped getting order confirmations. We checked `order_notifications` and
> there are no rows at all for recent orders. The older ones from March are there.
>
> The customers are otherwise fine — orders exist, totals are right.
>
> Our developer says the confirmation code looks correct to him and he cannot see anything
> wrong with it.

---

### Ticket SPX-533 — "Orders stuck at ready"

> Filed by: Dispatch
>
> We have orders sitting at READY_FOR_PICKUP that nobody has come to collect. The
> restaurants say they marked them ready hours ago.
>
> Our dispatch consumer is connected — I can see it in the RabbitMQ console with one
> consumer on `delivery.dispatch` — but the queue is empty and has never had a message in
> it. The analytics team say their queue is working fine and they are getting every order.
>
> Both feeds come from the same exchange, so I do not understand how one can work and the
> other not.

---

### Ticket SPX-541 — "An order went backwards"

> Filed by: Customer support
>
> A customer sent us a screen recording. Their tracking screen said DELIVERED, then a few
> minutes later said PREPARING, then said READY FOR PICKUP.
>
> I looked at the audit log and operations really did set those statuses in that order,
> presumably by mistake. My question is why the system let them.

---

### Ticket SPX-548 — "The kitchen got half an order"

> Filed by: Restaurant operations
>
> Napoli Corner received a ticket for a Margherita Pizza. The customer had also ordered a
> Masala Dosa in the same basket, which is Curry Leaf's dish, not theirs.
>
> The order in our system says restaurant: Curry Leaf Kitchen. Curry Leaf got the ticket
> and made the dosa. Nobody made the pizza, but the customer paid for it.

---

## 4. Investigation hints

Read **one** hint at a time and go back to the code before reading the next.

---

### SPX-520 — orders never leave PLACED

**Hint 1.** The checkout response is `201` and cannot tell you anything about what happened
afterwards. Read the application log around a checkout instead. Search for the word
`async`.

**Hint 2.** The intake step is an in-process Spring application event, not a RabbitMQ
message. There is nothing to look for in the broker. Find the listener and read what it
does first.

**Hint 3.** The listener fails with an error saying it cannot find an order that plainly
exists in the database. Take that literally. Under what circumstances is a row invisible to
a query even though it has been written?

**Hint 4.** Write down the order of operations inside `placeOrder`: when is the row written,
when is the event published, and when does the transaction commit? Then ask which thread
each of those happens on.

**Hint 5.** Spring has an annotation specifically for listeners that must not run until the
publishing transaction has committed. Find it, and note which phase you want.

---

### SPX-527 — confirmations never recorded

**Hint 1.** Same starting point as SPX-520: the failure is on a background thread, so read
the log, not the response. The exception type names the problem almost completely.

**Hint 2.** The confirmation method needs to know who to address the message to. Look at
where it gets that from, and ask whether that source is available on the thread the method
is actually running on.

**Hint 3.** `SecurityContextHolder` stores the authentication in a `ThreadLocal` by default.
Work out what that implies when the method runs on a pool thread, and then look up what
Spring Security offers for propagating it.

**Hint 4.** There is a second, more robust fix available that does not involve the security
context at all. The order already knows who placed it.

---

### SPX-533 — one consumer works and the other does not

**Hint 1.** Dispatch is right that both feeds come from the same exchange. On a topic
exchange, what determines whether a published message reaches a particular queue?

**Hint 2.** List the bindings and compare them with the routing keys the application
actually publishes:

```bash
docker exec -it lab-rabbitmq rabbitmqctl list_bindings source_name routing_key destination_name
grep "routing key" app.log
```

Put the two lists side by side.

**Hint 3.** A message published to a topic exchange that matches no binding is **discarded
silently**. There is no error, no log line, and nothing in any queue. Absence of evidence is
the evidence here.

**Hint 4.** Both values come from the same place in configuration — or at least they look as
though they do. Check whether they really do.

---

### SPX-541 — status moving backwards and skipping stages

**Hint 1.** Read `API.md` for the rules the endpoint is supposed to enforce, then read the
method that handles it and count how many of those rules appear in the code.

**Hint 2.** The method does validate one thing about the requested status. Work out what
that check covers and what it does not.

**Hint 3.** There is a second method in the same class that gets this right. Compare them.

---

### SPX-548 — items from two restaurants on one order

**Hint 1.** Reproduce it first: place an order with item 1 (Curry Leaf) and item 6 (Napoli
Corner) and look at what comes back.

**Hint 2.** The order has to be assigned to exactly one restaurant. Find the line that
decides which one, and ask what it does with the rest of the items.

**Hint 3.** The loop that builds the order lines validates each item — read what it checks
and what it does not. The missing check is the one that would compare each item against a
decision already made above the loop.

---

## 5. Before you call it fixed

- Place an order, wait two seconds, and check all three background effects: the status is
  `ACCEPTED`, a notification row exists with the right recipient, and the analytics consumer
  logged it.
- Check the intake queue is empty:

```bash
curl -s -u 'ops@spicebox.test:Admin123!' http://localhost:8080/api/ops/intake-queue
```

- Drive an order all the way to `DELIVERED` through every legal step, and confirm each
  illegal move — skipping a stage, going backwards, moving anything out of `DELIVERED` — is
  refused with `409`. A fix that also blocks the legal path is not a fix.
- Mark an order `READY_FOR_PICKUP` and confirm the dispatch consumer logs it and the order
  reaches `OUT_FOR_DELIVERY` on its own.
- Try to place a mixed-restaurant basket and confirm it is refused, then confirm a
  single-restaurant basket still works.
- Check no order has foreign items:

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass fooddb -e "
SELECT o.id, o.order_code, o.restaurant_id, m.restaurant_id AS item_restaurant
  FROM food_orders o JOIN order_items i ON i.order_id=o.id JOIN menu_items m ON m.id=i.menu_item_id
 WHERE m.restaurant_id <> o.restaurant_id;"
```

- Watch the log for `Unexpected exception occurred invoking async method` and confirm it no
  longer appears.
- Re-run `mvn test`.

When you are done, ask for **verification mode** and I will check your work.
