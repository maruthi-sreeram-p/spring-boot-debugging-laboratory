# Debugging Guide — Vantage Supply Inventory Service

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

---

## 1. Business context

Vantage Supply distributes electronics and packaging from three warehouses. This service is
the stock system of record, and four things depend on it:

- **The picking floor** looks up stock on a handheld before walking to a bay. A wrong number
  sends a picker to an empty shelf.
- **Order capture** asks how much is available before promising a customer a date.
  Over-promising means a cancelled order; under-promising means a lost sale.
- **Goods-in** posts deliveries against purchase orders. Their count is the only record of
  what the supplier actually sent, and it is what the supplier gets paid against.
- **Finance** runs a reconciliation at month end: the sum of a product's movements in a
  warehouse must equal its `quantity_on_hand`. A difference means either the snapshot or the
  ledger is lying, and neither answer is acceptable.

---

## 2. How the system is supposed to behave

**Three quantities, and one of them is derived.** `quantityOnHand` is what is in the
building. `quantityReserved` is already committed to open orders. `quantityAvailable` is
what may still be promised, and it is on hand *minus* reserved. It is never equal to on hand
unless nothing is reserved.

**The cache is a copy, never a source.** A cached figure is a copy of a committed database
figure. Asking for the same warehouse through the cached per-warehouse endpoint and through
the uncached network-wide endpoint must give the same numbers, always.

**Every stock change leaves a ledger row.** Receipts, issues and adjustments all write a
signed `stock_movements` row. The sum of movements for a product in a warehouse equals that
row's `quantity_on_hand`. This is the property finance reconciles against, and it must hold
for every row, every month.

**A receipt is all or nothing.** A delivery is posted as one unit of work. If any line is
rejected, nothing changes anywhere — not the stock, not the ledger, not the line
quantities, and not the cache.

**A line cannot be over-received.** The *running total* received against a line may never
exceed the quantity ordered, however many part-deliveries it arrives in. An order that has
reached `RECEIVED` is closed.

---

## 3. Symptoms

Nobody has told you how many distinct defects there are, and at least one of these tickets
describes two separate problems.

---

### Ticket INV-402 — "Month-end does not reconcile"

> Filed by: Finance
>
> Three product/warehouse combinations came out with a difference between `quantity_on_hand`
> and the sum of their movements. Two of them are short by exactly the amount of a stock
> correction the controller made during the month, and there is no movement row for that
> correction at all.
>
> I can see the stock level changed because `updated_at` moved. I cannot see *why* it
> changed, which means I cannot sign this off.
>
> The corrections that *increased* stock are all there. It seems to be only the ones that
> reduced it.

---

### Ticket INV-407 — "The handheld and the report disagree"

> Filed by: Warehouse supervisor, Bengaluru
>
> The handheld says we have 1920 of VS-CBL-0101. The stock report says 1420. The physical
> count says 1420.
>
> The 1920 figure is not random — it is exactly what we would have had if a delivery we
> tried to post yesterday had gone through. It did not: the system rejected it because one
> of the lines had more units on it than we had ordered, and we had to redo the paperwork.
>
> The handheld has been showing the wrong number ever since. It sorted itself out overnight
> at some point but it is back again today.

---

### Ticket INV-411 — "Mumbai is showing Bengaluru numbers"

> Filed by: Warehouse supervisor, Mumbai
>
> Our screen says we have 1420 of VS-CBL-0101 with 180 reserved. We have 860, and 40
> reserved. 1420 and 180 are Bengaluru's numbers.
>
> The screen header says WH-MUM, so it thinks it is showing us. Delhi say they are seeing
> the same thing.
>
> Our developer says it works fine when he tests it, and it did work fine for me first
> thing this morning before anyone else was in.

---

### Ticket INV-418 — "We promised stock we did not have"

> Filed by: Order capture
>
> We sold 300 of VS-SSD-0330 out of Bengaluru because the API told us 310 were available.
> 95 of those were already reserved against an order that ships Thursday, so there were only
> 215 to sell.
>
> We take `quantityAvailable` from your response and treat it as sellable. Is that wrong?

---

### Ticket INV-424 — "The supplier is disputing our receipt"

> Filed by: Goods-in, Bengaluru
>
> We ordered 1000 cables. The supplier sent two pallets of 600, which is their problem, but
> our system accepted both of them and now says we received 1200 against a purchase order
> for 1000.
>
> Each receipt went through cleanly. Neither of them warned us.
>
> Separately: we posted another delivery against a purchase order that was already marked
> RECEIVED last month, by mistake, and it accepted that too.

---

## 4. Investigation hints

Read **one** hint at a time and go back to the code before reading the next.

---

### INV-402 — corrections missing from the ledger

**Hint 1.** Finance has already narrowed it for you: increases are recorded, decreases are
not. Find the one method that handles adjustments and read it with that in mind.

**Hint 2.** The stock level and the movement row are written by two different statements.
Check whether they are reached under the same conditions.

**Hint 3.** Once you have found it, ask the broader question: how many other places in this
service change `quantity_on_hand`, and does each of them write a movement? The fix that
prevents recurrence is structural, not a one-line change.

---

### INV-407 — handheld shows a figure the database never had

**Hint 1.** The supervisor has told you the reproduction steps: post a delivery that will be
rejected, then look at the stock. Do exactly that, and check the database and Redis
separately afterwards.

```bash
docker exec -it lab-redis redis-cli GET 'inv:stock:1'
```

**Hint 2.** The rejection rolled the database back. Work out what else the request had
already done by the time the rejection happened, and whether a rollback can undo it.

**Hint 3.** Run `docker exec -it lab-redis redis-cli MONITOR` in one terminal and post the
failing receipt from another. The order of operations will be on screen.

**Hint 4.** "It sorted itself out overnight" is a clue about the *duration* of the
inconsistency, not about a second cause. Find the setting that explains it.

**Hint 5.** The fix is about *when* the cache is touched relative to the transaction. Look
up how Spring lets you run code after a transaction commits, and consider whether writing to
the cache at all is safer than removing from it.

---

### INV-411 — one warehouse showing another warehouse's stock

**Hint 1.** The developer cannot reproduce it and the supervisor saw it work first thing in
the morning. Both observations point at the same precondition. What is different about the
system early in the morning?

**Hint 2.** Look at the Redis keyspace after reading stock for several warehouses:

```bash
docker exec -it lab-redis redis-cli FLUSHALL
curl -s -u "$WH" "$BASE/api/stock/products/1/warehouses/1" > /dev/null
docker exec -it lab-redis redis-cli KEYS 'inv:*'
```

Count the keys and compare with the number of things you looked up.

**Hint 3.** Find the method that builds the cache key and read its parameters against what
it actually uses.

**Hint 4.** The response still showed the right warehouse code, which is why it looks
plausible. Work out which parts of the response come from the cache and which do not — that
tells you how far the corruption reaches.

---

### INV-418 — available quantity over-promised

**Hint 1.** Order capture is reading the field correctly. Check the arithmetic that produces
it against the definition in `API.md`.

**Hint 2.** There is exactly one place where all three quantities are assembled into a
response. Read the three lines that set them.

---

### INV-424 — over-receipt accepted

**Hint 1.** There *is* a check against the ordered quantity, and it does fire — a single
receipt of 1200 is refused. Work out what makes two receipts of 600 different.

**Hint 2.** Write down the two values the comparison uses, then write down the two values it
should use. The difference is one word.

**Hint 3.** The second half of the ticket — receiving against a closed order — is a separate
omission in the same method. Look at what the method does with the order's status before it
starts, versus after it finishes.

---

## 5. Before you call it fixed

- Run the reconciliation query from the README. Every row must show a difference of zero.
  Make several adjustments in both directions first.

```bash
docker exec -it lab-postgres psql -U labuser -d inventorydb -c "
SELECT s.product_id, s.warehouse_id, s.quantity_on_hand,
       COALESCE(SUM(m.quantity),0) AS ledger, s.quantity_on_hand - COALESCE(SUM(m.quantity),0) AS diff
  FROM stock_levels s LEFT JOIN stock_movements m
    ON m.product_id=s.product_id AND m.warehouse_id=s.warehouse_id
 GROUP BY s.product_id, s.warehouse_id, s.quantity_on_hand HAVING s.quantity_on_hand <> COALESCE(SUM(m.quantity),0);"
```

- Flush Redis, then read the same product in all three warehouses and confirm each returns
  its own figures. Then read them again and confirm the cached answers still match the
  database.
- Post a receipt that will be rejected, then check Redis **and** the database. They must
  agree, and both must show the pre-receipt values.
- Compare the cached and uncached endpoints for the same warehouse after every kind of
  write: receipt, adjustment up, adjustment down.
- Over-receive in parts (600 + 600 against 1000) and confirm the second one is refused with
  `422` and that nothing moved. Then confirm a legitimate part-receipt (600 + 400) still
  works.
- Post against a `RECEIVED` order and confirm it is refused.
- Re-run `mvn test`.

When you are done, ask for **verification mode** and I will check your work.
