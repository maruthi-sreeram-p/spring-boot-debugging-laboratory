# Debugging Guide — Northwind Shop Order Service

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

---

## 1. Business context

Northwind sells consumer electronics online. `shop-order-service` is the only backend
behind the storefront and behind the small internal back-office tool the merchandising
team uses to edit prices and stock.

Three groups depend on it:

- **Customers** browse the catalogue, keep a cart, place orders, and look at their own
  order history.
- **The warehouse** works from `products.stock_quantity`. When that number says 24, they
  expect 24 units to be physically on the shelf, and they expect the number to fall the
  moment an order is accepted.
- **Merchandising** edits products through the back office. When they drop a price, the
  storefront is supposed to show the new price immediately — price changes are usually
  made minutes before a campaign goes live.

Carts are kept in Redis because they are throwaway state; orders and stock are in MySQL
because they are money.

---

## 2. How the system is supposed to behave

**Catalogue**
- `GET /api/products` returns a page of active products together with paging metadata.
  The storefront paginator uses `totalElements` to render "showing 1–20 of N" and to decide
  whether a "next page" control should exist.
- `GET /api/products/{id}` returns one product. This read is cached in Redis for 30
  minutes because it is the single hottest endpoint in the system.
- A back-office edit through `PUT /api/admin/products/{id}` is expected to be visible on
  the very next catalogue read, both in the list and in the detail view.

**Cart**
- One cart per customer, stored in Redis, surviving logout and browser restarts.
- Quantities are constrained to 1–20 per line. A quantity outside that range is a client
  bug and must be rejected with `400`, whichever endpoint it arrives on.

**Checkout — `POST /api/orders`**

This is the operation the whole service exists for. It should be all-or-nothing:

1. Read the current cart.
2. For every line, check stock and reduce `products.stock_quantity` by the ordered amount.
3. Write the `orders` row and its `order_items` rows.
4. Empty the cart.
5. Return `201` with the order.

If any line has insufficient stock, the whole request fails with `409` and **nothing**
changes — no order row, no stock movement, and the cart is left intact so the customer can
adjust it.

**Order history**
- `GET /api/orders` returns the orders of the authenticated customer, newest first.
- `GET /api/orders/{id}` returns one order **belonging to the caller**. An order id that
  belongs to somebody else must be indistinguishable from an id that does not exist:
  `404`. The order body contains a shipping address, so this matters.

---

## 3. Symptoms

These are the reports that came in. They are written the way the people who filed them
wrote them, which means some of them describe the same underlying problem twice and some
of them describe a consequence rather than the problem.

Nobody has told you how many distinct defects there are.

---

### Ticket SHOP-241 — "Warehouse count never moves"

> Filed by: Warehouse operations
>
> We ran a reconciliation this morning. The system says we have 52 units of NW-AUD-2201.
> We counted 47 on the shelf. Looking at the orders table there are clearly orders for
> that SKU that went out. As far as I can tell `stock_quantity` has not changed for any
> product since the data was loaded, even though orders keep being created successfully.
>
> The API is not erroring. Customers get their order confirmation. The number just does
> not move.

---

### Ticket SHOP-248 — "Customer was charged twice for the same basket"

> Filed by: Customer support
>
> A customer says she placed one order and got two confirmation emails with two different
> order numbers and the same items. When we asked her what she did, she said the page
> seemed to hang so she pressed the button again.
>
> I tried it myself: add something to the cart, place the order, and then place another
> order without touching the cart. The second one goes through. I would have expected the
> second attempt to come back with "your cart is empty".

---

### Ticket SHOP-252 — "Storefront pagination is broken"

> Filed by: Frontend team
>
> Your paging envelope is not internally consistent. On `GET /api/products?size=5` we get
> back `totalElements: 5` and `totalPages: 3`. Those two cannot both be true. Our
> paginator computes the last page from `totalElements` and therefore thinks there is only
> one page, so customers can never reach anything past the first screen of results.
>
> Same envelope shape on `GET /api/orders`, same problem there.

---

### Ticket SHOP-255 — "Price change did not go live"

> Filed by: Merchandising
>
> I dropped NW-AUD-2201 from 279.00 to 249.00 in the back office at 09:40. The back office
> showed 249.00 straight after saving. The product page on the storefront was still showing
> 279.00 at 10:05.
>
> The odd part: the category listing page showed 249.00 the whole time. Only the product
> detail page was wrong. It eventually corrected itself without anyone doing anything.
>
> Our QA engineer cannot reproduce this on his machine at all. He says it works fine every
> time he tries it.

---

### Ticket SHOP-259 — "Order total is negative"

> Filed by: Finance
>
> We have an order in the reporting extract with a negative `total_amount`. Not zero —
> negative. There is a line on it with a negative quantity.
>
> We do not know how a customer produced this. The web UI has a quantity stepper that
> cannot go below 1. Whatever created that order did not come through the stepper, or the
> stepper is not the only way in.

---

### Ticket SHOP-263 — "I can see somebody else's order"

> Filed by: A customer, forwarded by support
>
> I was looking at my order history and changed the number in the address bar out of
> curiosity. It showed me an order that is not mine, with a delivery address in Delhi. I
> live in Mumbai. Please look into this.

---

## 4. Investigation hints

Hints are graded. Read **one** at a time and go back to the code before reading the next.
If a hint tells you something you already knew, that is a sign you are on the right track,
not a sign the hint was useless.

---

### SHOP-241 — stock never changes

**Hint 1.** The order row is written and the stock row is not, in the same request. So the
question is not "is the code trying to update stock" — read it, it clearly is. The question
is why one write reaches the database and the other does not.

**Hint 2.** Nothing in the checkout path calls `save()` on a product. That is not
automatically wrong; there is a mechanism that would normally make the update happen
anyway. Work out what that mechanism requires in order to fire.

**Hint 3.** Turn on `logging.level.org.hibernate.SQL=DEBUG` (it already is) and count the
statements a single `POST /api/orders` produces. Look specifically for an `update products
set ...`. Then look at how many separate database transactions that one HTTP request
opens, and where each one begins and ends.

**Hint 4.** Put a breakpoint on the line that reduces the quantity and, when it hits,
evaluate whether the object you are mutating is attached to a persistence context that is
going to be flushed. Then look at how the method containing that line was reached.

---

### SHOP-248 — second checkout succeeds

**Hint 1.** Reproduce it exactly as support did, then look at Redis directly rather than at
the API response. `docker exec -it lab-redis redis-cli KEYS '*'` before checkout and after.

**Hint 2.** Checkout both reads the cart and empties it. Compare how it does each of those
two things.

**Hint 3.** There is a single place in the codebase responsible for knowing what a cart is
called in Redis. Find it, then find every piece of code that talks to a cart, and check
whether they all go through it.

---

### SHOP-252 — inconsistent paging envelope

**Hint 1.** Both broken endpoints return the same wrapper type. The wrapper is built in one
place.

**Hint 2.** `totalPages` is right and `totalElements` is wrong. They are supposed to be
derived from the same source. Find what each one is actually derived from.

---

### SHOP-255 — stale price on the detail endpoint

**Hint 1.** The list endpoint is correct and the detail endpoint is not, and the difference
corrects itself after about half an hour. That interval is configured somewhere; find it,
and you will know which subsystem you are debugging.

**Hint 2.** Your QA engineer cannot reproduce it because of the state his environment is in
when he starts testing, not because of his code. What has to be true before this bug can
show itself at all?

**Hint 3.** Inspect Redis around the write: `docker exec -it lab-redis redis-cli KEYS
'products*'` before the back-office update, immediately after it, and after the next read.
Note the exact key names.

**Hint 4.** Read the two annotations on `ProductService` — the one on the read path and the
one on the write path — side by side, and ask whether they are naming the same thing.

---

### SHOP-259 — negative order total

**Hint 1.** The rule that quantity must be between 1 and 20 is declared on the request
object. Declaring a constraint is not the same as enforcing it.

**Hint 2.** There is more than one way to get a quantity into a cart. Try the same illegal
quantity through each of them and compare the status codes.

**Hint 3.** Once a bad quantity is in Redis, follow it forward. Which downstream numbers
does it corrupt, and how far from the entry point does the damage first become visible?

---

### SHOP-263 — cross-customer order access

**Hint 1.** Compare the two order-reading endpoints. One of them constrains the query by
the caller.

**Hint 2.** Being authenticated and being authorized are different questions. Which one
does this endpoint actually ask?

**Hint 3.** Decide deliberately what the response should be when the order exists but
belongs to someone else, and why `403` leaks information that `404` does not.

---

## 5. Before you call it fixed

- Reproduce every symptom **once more** after your change, using the same commands.
- Re-run `mvn test`. The suite passed before you started and it should still pass. Note
  that it also passed while all of these tickets were open — think about what that tells
  you about the suite.
- Check the reset path: after a fix to SHOP-241, place two orders in a row for the same
  cart and confirm the stock arithmetic is still right. Some of these defects were hiding
  each other.
- Look at the database, not the HTTP response, to confirm a state change happened.

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass shopdb \
  -e "select id, sku, stock_quantity from products order by id;"
```

When you are done, ask for **verification mode** and I will check your work.
