# Debugging Guide — Ledgerline Payment Processing Service

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

---

## 1. Business context

Ledgerline takes card payments on behalf of merchants. A merchant creates an order, sends
us a payment request, and watches the order status. When it says `PAID`, their warehouse
ships.

Behind us is the acquiring bank. In this lab it is simulated in-process — no real provider
is contacted — but it behaves like one, including the part that makes payments hard: **the
acquirer sometimes takes the money and then loses the response.** When that happens we do
not know whether the customer was charged, and the only way to find out is to ask the
acquirer.

Three groups depend on this service:

- **Merchants** need `PAID` to mean paid. Shipping against a payment that did not happen is
  a direct loss.
- **Customers** need to be charged once. Being charged twice is a chargeback, a refund, and
  a complaint to the card network.
- **Payment operations** reconcile daily. They compare our records against the acquirer's
  settlement file, and every mismatch is investigated by a human.

The acquirer's record is the truth. Ours is a copy, and copies drift.

---

## 2. How the system is supposed to behave

**`PAID` means the money is in.** An order advances to `PAID` only after a payment has
actually succeeded, for the full order amount. A declined payment leaves the order
`PAYMENT_FAILED`. A payment whose outcome is unknown leaves the order untouched.

**The order carries the amount, not the request.** The request body is a statement from a
client that may be stale or hostile. The amount charged is the amount the order says is
owed.

**An idempotency key identifies one attempt, forever.** Replaying a request with a key that
has been used returns the original payment in whatever state it reached, and does not
contact the acquirer again. That is what makes a client retry safe. Retrying for real —
after a genuine decline — means a new key.

**A retry may never capture twice.** A lost response is retried, because the request may not
have arrived. But the acquirer must never end up with two captures for one payment; the
settlement view should show at most one per payment reference.

**Reconciliation asks the acquirer.** A payment stuck in `PROCESSING` is settled by finding
out what the acquirer actually did — not by assuming. Only a payment the acquirer has no
record of may be marked `FAILED`.

---

## 3. Symptoms

Nobody has told you how many distinct defects there are.

---

### Ticket PAY-880 — "We shipped against a declined card"

> Filed by: Northwind fulfilment
>
> We shipped an order this morning because your API said `PAID`. The customer's bank
> declined the card. There is no money.
>
> I pulled the record: the payment row says `FAILED` with "Insufficient funds", and the
> order row on the same screen says `PAID`.
>
> We ship on `PAID`. What are we supposed to watch instead?

---

### Ticket PAY-887 — "Charged three times, and you have no record of any of them"

> Filed by: Payment operations, from a cardholder complaint
>
> A customer is disputing three identical charges of 1120.22 on the same day. The acquirer's
> settlement file has all three, each with its own authorisation code.
>
> Our `payments` table has **nothing**. No row for that order at all. The order is still
> `AWAITING_PAYMENT`.
>
> The merchant says the checkout page showed an error and they only pressed pay once.
>
> I cannot refund a payment I have no record of, and I cannot tell the network what
> happened.

---

### Ticket PAY-891 — "The same idempotency key produced two payments"

> Filed by: Spicebox engineering
>
> Our checkout retries on a network error, reusing the same `Idempotency-Key` — that is what
> your docs tell us to do.
>
> We have a customer whose card was declined, our client retried with the same key, and your
> API ran it again. There are two payment rows with the identical key.
>
> This time it was only a decline so nothing was taken. If the first attempt had succeeded
> and our connection had dropped, would we have charged them twice?

---

### Ticket PAY-895 — "Marked failed, money taken"

> Filed by: Payment operations
>
> A payment was sitting in `PROCESSING`. Your reconciliation job ran and marked it `FAILED`
> with "No response from the acquirer within 5 minutes", and moved the order to
> `PAYMENT_FAILED`.
>
> The acquirer captured that payment. I can see the authorisation code in the settlement
> view for exactly that payment reference.
>
> So we have told the merchant the payment failed, and the customer has been charged. That
> is the worst possible combination.

---

### Ticket PAY-902 — "A 50,000 order was paid with 1 rupee"

> Filed by: Northwind finance
>
> `ORD-5005` is for 50,000.00. There is a `SUCCEEDED` payment against it for 1.00 and the
> order is `PAID`.
>
> I do not think this was a mistake by the customer. Somebody worked out what to send.

---

## 4. Investigation hints

Read **one** hint at a time and go back to the code before reading the next.

For every experiment in this project, ask the three questions in the README in order: what
does the acquirer say, what do our payments say, what do our orders say. Reset the acquirer
first so its list contains only what you just did.

---

### PAY-880 — order says PAID, payment says FAILED

**Hint 1.** Reproduce it in one request: `ORD-5006` is for 760.11, which the sandbox
declines. Then read both rows.

**Hint 2.** There is exactly one line that sets the order to `PAID`. Find it, and note where
it sits relative to the call to the acquirer.

**Hint 3.** Ask what happens on each of the three outcomes — approved, declined, no answer —
and which of them the code currently distinguishes.

**Hint 4.** When you fix it, decide deliberately what the order status should be for a
payment left `PROCESSING`. There is a right answer and the ticket for PAY-895 depends on it.

---

### PAY-887 — three captures, no record

**Hint 1.** `ORD-5007` is for 1120.22, the amount the sandbox captures before losing the
response. Reset the acquirer, submit it once, then look at all three places.

**Hint 2.** Three captures came from one HTTP request. Something in this application called
the acquirer three times. Find what, and read its configuration.

**Hint 3.** Now explain the second half: why is there no row in `payments`, given that the
code clearly creates one before calling the acquirer? Turn this on and submit again:

```
logging.level.org.springframework.retry=DEBUG
logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```

Count the transactions, and note which of them commit.

**Hint 4.** Two annotations sit on the same method. Work out which one wraps the other, and
what that ordering means for work already done when the exception is thrown.

**Hint 5.** The deeper question is not how to stop the retry — retrying a lost response is
reasonable. It is how a retry can be made safe when the first attempt may already have taken
the money. Look up what a payment system sends so the acquirer can recognise a repeat, and
ask what this code sends on the second attempt that differs from the first.

---

### PAY-891 — one key, two payments

**Hint 1.** The idempotency check exists and does work in some cases. Find the case where it
works: take a payment that succeeds, replay the same key, and compare the payment reference
you get back.

**Hint 2.** Now do the same with a declined payment. The difference between the two
experiments is the defect.

**Hint 3.** Read the repository method the check calls and count its arguments. Then read the
idempotency rule in `API.md` and count the conditions it states.

**Hint 4.** There is also nothing in the schema stopping two rows sharing a key. Look at the
index on `idempotency_key` and ask what kind it is, and what it would take to make the
database enforce the rule rather than the application.

---

### PAY-895 — reconciliation guessed

**Hint 1.** Read the reconciliation job and list everything it consults before deciding a
payment failed.

**Hint 2.** The service has a way to ask the acquirer what happened for a payment reference.
Find it, and check whether the job uses it.

**Hint 3.** This is an absence rather than a mistake, so reading the existing lines
carefully will not find it. Compare what the job does against what `API.md` says
reconciliation is for.

**Hint 4.** Once you have it, think about ordering: which of the other defects has to be
fixed before reconciliation can even find the right payment to reconcile?

---

### PAY-902 — underpayment accepted

**Hint 1.** Trace the amount from the HTTP request to the row written in `payments`, and
then to the call to the acquirer. Note every place it could have been replaced by something
more trustworthy.

**Hint 2.** The order row is loaded a few lines earlier and has an `amount` field that is
never read.

**Hint 3.** Decide what should happen when the request and the order disagree — reject, or
silently use the order amount? Both are defensible; one of them hides client bugs. Say which
you chose and why.

---

## 5. Before you call it fixed

Reset the acquirer before each check so its list is clean.

- **PAY-880:** pay `ORD-5006` (declines). Payment must be `FAILED` and the order
  `PAYMENT_FAILED`. Then pay `ORD-5008` (approves) and confirm `SUCCEEDED` / `PAID`.
- **PAY-887:** pay `ORD-5007` (response lost). The acquirer must show **exactly one**
  capture. A payment row must exist, in `PROCESSING`, with its attempt history intact.
- **PAY-891:** replay one key across all three outcomes — approved, declined, no answer. In
  every case the second call must return the original payment reference, and the acquirer
  call count must not increase.
- **PAY-895:** leave a payment in `PROCESSING` that the acquirer captured, run
  reconciliation, and confirm it becomes `SUCCEEDED` with the acquirer's reference and the
  order becomes `PAID`. Then do the same for a payment the acquirer never saw and confirm it
  becomes `FAILED`.
- **PAY-902:** try to pay less than the order amount, and more. Both must be refused.

Then run the three reconciliation queries from the README. All three must come back empty:

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass paymentsdb -e "
SELECT o.order_ref FROM orders o WHERE o.status='PAID'
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.status='SUCCEEDED');
SELECT idempotency_key, COUNT(*) FROM payments GROUP BY idempotency_key HAVING COUNT(*)>1;"
```

Re-run `mvn test`.

When you are done, ask for **verification mode** and I will check your work.
