# Debugging Guide — Meridian Bank Core Banking Service

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

---

## 1. Business context

Meridian Bank is a small retail bank. `core-banking-service` is where the money actually
lives: customers, accounts, balances, and the transaction ledger behind every statement.

Three groups depend on it:

- **Customers** use the mobile app to check balances, deposit, withdraw, transfer and
  download statements.
- **The fraud desk** freezes accounts. When a card is reported stolen or a mule account is
  identified, a teller freezes it and expects money to stop moving *immediately*.
- **Finance** reconciles. At the end of every day they compare `accounts.balance` against
  the ledger. Every rupee that moved must have a `transactions` row explaining it, and the
  latest `balance_after` on an account must equal that account's balance.

The last point is the one that matters most. In a bank, an unexplained balance is a bigger
problem than a failed request.

---

## 2. How the system is supposed to behave

**Balances and the ledger are two views of the same truth.** Nothing may change
`accounts.balance` without writing a matching `transactions` row in the same unit of work.
If the row is not written, the balance must not move either.

**Deposits and withdrawals are independent and additive.** If the API accepts *n* deposits
of *x*, the balance has increased by exactly *n × x*. This holds whether the requests
arrive one after another or all at once. The mobile app retries on timeout, the teller UI
has a double-click problem nobody has fixed, and the overnight importer runs sixteen
workers — simultaneous credits to one account are normal traffic, not an edge case.

**Transfers are atomic.** A transfer is a single business event with two legs. Either:

- the response is `201`, both balances have moved, and exactly two ledger rows exist
  sharing one reference; or
- the response is not `2xx`, **neither** balance has moved, and **no** ledger rows exist.

There is no third outcome. In particular, a transfer that is rejected for exceeding the
daily limit must leave the world exactly as it found it.

**Transfers move money in one direction.** `amount` is what leaves the source and arrives
at the destination. A transfer cannot be used to pull money out of an account the caller
does not own.

**A frozen account is frozen.** After the fraud desk freezes an account, every deposit,
withdrawal and transfer touching that account is rejected with `409`, and the freeze
survives a restart of the service.

**Statements are inclusive at both ends.** `from=2024-11-01&to=2024-11-30` returns
everything booked in November, including transactions booked on the 30th.

---

## 3. Symptoms

These are the reports that came in. Some describe the same underlying problem from two
angles; some describe a consequence rather than the problem. Nobody has told you how many
distinct defects there are.

---

### Ticket BANK-812 — "End of day did not reconcile"

> Filed by: Finance, daily reconciliation
>
> Account `MB-0000-1003` closed yesterday 60,000.00 higher than the ledger explains. There
> is no `transactions` row for the difference. The balance simply went up.
>
> I checked the other side. No account went down by 60,000.00. The money was not moved from
> anywhere. It was created.
>
> The customer says nothing unusual happened. The sender says she *tried* to send a large
> payment and the app told her it was declined because of a daily limit, so she gave up and
> paid by card instead. Her own balance is untouched, which matches what the app told her.
>
> This is the second time this month.

---

### Ticket BANK-817 — "Deposits go missing during the morning rush"

> Filed by: Branch operations
>
> Between 09:00 and 09:30 we process a lot of cash deposits and the teller app is slow, so
> staff click Deposit more than once. Every click comes back with a success and a receipt,
> and every receipt has its own reference number.
>
> The balance does not match the receipts. Yesterday one account had eleven deposit
> receipts of 100.00 each and the balance had gone up by 400.00.
>
> I cannot reproduce it at my desk. One at a time it is always correct.

---

### Ticket BANK-823 — "Account we froze is still spending"

> Filed by: Fraud desk
>
> We froze `MB-0000-1004` on Tuesday after the customer reported the card stolen. I have
> the screenshot: the API returned the account with `"status": "FROZEN"`.
>
> There are three withdrawals on that account since Tuesday. Every one of them succeeded.
>
> I opened the account screen again today and it shows `ACTIVE`. Nobody unfroze it — there
> is no unfreeze endpoint.

---

### Ticket BANK-830 — "November statement is missing the last day"

> Filed by: Customer support
>
> A customer asked why his November statement does not show a withdrawal he made on the
> 30th. I pulled `from=2024-11-01&to=2024-11-30` and he is right, it is not there. If I ask
> for `to=2024-12-01` it appears.
>
> He says his December statement does not show it either, which I suppose makes sense.
> So that transaction is on no statement at all.

---

### Ticket BANK-836 — "Money taken from an account by someone else"

> Filed by: Customer, escalated by support
>
> A customer noticed 5,000.00 leave her account. She did not authorise it, there is no card
> payment, and the ledger row on her account is a `TRANSFER_IN`, which she says makes no
> sense because her balance went *down*.
>
> The counterparty is a real customer of ours. His account went *up* by 5,000.00 and his
> row says `TRANSFER_OUT`.
>
> Both rows share a reference, so as far as the system is concerned this was one transfer
> that completed normally.

---

## 4. Investigation hints

Hints are graded. Read **one** at a time and go back to the code before reading the next.

---

### BANK-812 — money created by a rejected transfer

**Hint 1.** The customer is not confused: the request really was rejected, and her balance
really is untouched. Take that at face value and ask how a rejected operation can leave a
side effect behind on the other account.

**Hint 2.** Everything the transfer does is supposed to be inside one transaction. Read the
whole method and list, in order, every write it performs. Then ask, for each one, whether a
rollback of the outer transaction would actually undo it.

**Hint 3.** Not every write in that method is performed by the same component. Follow the
one that is delegated elsewhere, and read the annotation on the method it lands in.

**Hint 4.** Turn on transaction logging and repeat the failing transfer:

```
logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG
```

Count how many transactions one HTTP request opens, and note which of them commits and
which rolls back. The log will tell you about a suspended transaction. That word is the
whole answer.

**Hint 5.** Finally, ask *why* the check that rejected the transfer runs where it runs.
Moving it is one possible fix, but understand what it needs to read before you move it, or
you will break the limit instead of fixing the atomicity.

---

### BANK-817 — deposits lost under load

**Hint 1.** You cannot reproduce this one request at a time, and that is the most important
fact in the ticket. Reproduce it the way it happens:

```bash
python scripts/concurrent_deposits.py --account 1 --count 50 --amount 100.00
```

**Hint 2.** Write out, as three steps, what the deposit method does to the balance. Then
imagine two requests executing those three steps interleaved, and work out what the final
value is.

**Hint 3.** The database is doing exactly what it was asked to do. Look at the `update`
statement Hibernate emits for the balance and ask what value it writes — a computed
absolute value, or a relative change.

**Hint 4.** There is nothing in the `accounts` mapping that would make the database reject
a write based on a stale read. Compare it with what JPA offers for exactly that purpose,
and consider what the alternative locking strategies would cost here.

---

### BANK-823 — freeze does not stick

**Hint 1.** The response body is generated from an in-memory object. The database is the
only thing that decides whether the account is really frozen. Check which one you have been
believing.

**Hint 2.** Watch the SQL log while you call the freeze endpoint. Count the statements.
That number is the finding.

**Hint 3.** The freeze method loads, mutates and saves, which is the normal pattern and is
not wrong. Look above the method signature instead, and ask what that annotation instructs
the persistence context to do at commit time.

**Hint 4.** Compare that annotation with the ones on the methods in the same file that *do*
persist their changes.

---

### BANK-830 — statement excludes the final day

**Hint 1.** The API contract says both bounds are inclusive. Find where the two dates the
caller supplies are converted into the values the query actually uses.

**Hint 2.** A date is not an instant. When you turn `2024-11-30` into a point in time, you
have to choose *which* point in that day, and the two ends of a range need different
choices.

---

### BANK-836 — transfer that runs backwards

**Hint 1.** Look at the numbers in the ticket rather than the words. The sender gained and
the recipient lost. Under what input does the arithmetic in the transfer method produce
that?

**Hint 2.** Try the same value through the deposit endpoint and compare what happens. The
difference between the two responses tells you where to look, and it is not in the service.

**Hint 3.** Compare the validation annotations on the two request objects, field by field.

**Hint 4.** Once you have found it, ask a second question: should the service itself also
refuse this, independently of what the controller validated? Consider what else might call
that method in a year.

---

## 5. Before you call it fixed

- Reproduce every symptom once more, using the same commands, and confirm each one is gone.
- Run the reconciliation query from the README. The balance and the last `balance_after`
  must agree for every account:

```bash
docker exec -it lab-postgres psql -U labuser -d bankdb -c "
SELECT a.account_number, a.balance,
       (SELECT t.balance_after FROM transactions t
         WHERE t.account_id = a.id ORDER BY t.created_at DESC, t.id DESC LIMIT 1) AS ledger
  FROM accounts a ORDER BY a.id;"
```

- Re-run the concurrency script after your fix and confirm the difference is `0.00`. Then
  run it again with a higher `--count`; a fix that works for 50 and fails for 500 is not a
  fix.
- Check that your transfer fix did not break the daily limit itself. Push a source account
  past 50,000.00 in one day across several transfers and confirm the *last* one is rejected
  and that nothing at all moved for it.
- Re-run `mvn test`. It passed before you started. Think about why.

When you are done, ask for **verification mode** and I will check your work.
