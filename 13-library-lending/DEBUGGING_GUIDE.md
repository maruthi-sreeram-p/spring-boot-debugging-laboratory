# Debugging guide — Athenaeum Circulation Service

## The business

Athenaeum runs four branches: Central, Riverside, Hillside and a small reading room. The
circulation service is what the desk staff, the self-service kiosks and the mobile app all talk
to. It is nineteen months old. It replaced a system that ran on one machine in the Central branch
basement and served exactly one clerk at a time.

That last sentence matters. Everything about the domain — one clerk, one book, one member in front
of them — is still baked into how the code is written, and the traffic no longer looks like that.
On a Saturday morning four kiosks and the app all talk to it at once.

## What the system does

- **Copies, not titles.** A member borrows a physical copy with a barcode. The catalogue shows
  how many copies of a title are borrowable.
- **A cached borrowing count.** `members.active_loan_count` is kept in step with the loans table
  so the borrowing limit can be checked without counting rows.
- **Holds.** A member who wants a title that is entirely out joins a queue. When a copy comes
  back, the member at the head of the queue has it held for 48 hours.
- **Charges.** A charge of 5.00 accrues for each day a book is late. Books still out are priced
  by an accrual job at 02:00. A book handed back at the desk is priced there and then.

The full published rules are in [`README.md`](README.md). They are the specification; where the
service disagrees with them, the service is wrong.

## Reports from the business

Eight weeks of them, from desk staff, from the finance office, and from one very patient member.

---

### R1 — "Two people have the same book"

> This has happened four times now, always on a Saturday, always at Central.
>
> The system says copy ATH-000101 is out to Mrs Desai. Mrs Desai has it. But the system also has
> it out to Mr Verma, who is standing at the desk asking where his book is. And to two other
> people.
>
> All four of them got a confirmation. Nobody got an error.
>
> The really odd bit: there are five copies of that title. Four of them are sitting on the shelf
> behind me, marked available, and the system has issued the same one four times.

Nobody has ever reproduced this at the desk. The desk serves one person at a time.

---

### R2 — "The limit does not limit anything"

> Standard members are allowed four books. Joseph Kuriakose has six. His account page says he has
> one.
>
> He did not do anything clever. He says he tapped the borrow button on the app a few times
> because it seemed slow.
>
> Since his account page says one, the system happily lets him take more.

The finance office add: the cached count and the real count disagree for about a dozen members,
always in the same direction.

---

### R3 — "Nobody is being charged for late returns"

> Income from overdue charges fell off a cliff. Finance pulled the numbers.
>
> Books that are **still out** and overdue do get charged — the amounts look roughly right, we
> will come back to that.
>
> Books that come **back late** are charged nothing at all. Not a smaller amount. Nothing. There
> is no row in the fines table.
>
> The return itself works perfectly. The book goes back on the shelf, the loan closes, the member
> gets their receipt. The charge simply never appears.

---

### R4 — "The charges that do exist are short"

> Same investigation as R3, different problem.
>
> We took a book that was six days late and ran the accrual. It charged for five days.
>
> Then we ran it again in the afternoon and it charged for six. Same book, same due date, same
> day. The number depends on what time we run it.
>
> And anything less than a full day late is charged nothing, even though the rule says a book
> that comes back the day after it was due is one day late.

---

### R5 — "My hold was ready and the book was gone"

> A member placed a hold on *Introduction to Algorithms*. Both copies were out. When one came
> back she got the notification: ready for collection, 48 hours.
>
> She came in the next morning. The book was not on the hold shelf. It had been issued to
> somebody else who walked in off the street that evening.
>
> Her hold record still says READY. It even names the copy. That copy is out to another member.

---

### R6 — "The catalogue is lying about what is on the shelf"

> The terminal says three copies of *MySQL Cookbook* are available. There is one.
>
> One was lost in 2023 and written off. One has been with the binder since last spring. Both are
> recorded correctly in the system — you can see the statuses on the copy list.
>
> A member borrowed the real one. The terminal then said two available. The next member tried and
> got an error saying no copy is on the shelf.

---

## How to work on this

Six reports. R1 and R2 have something in common and the fix for one does not fix the other. R3
and R4 are both about money and are not the same bug.

This project is mostly settled with SQL, a load script and a careful reading of statement order.

- **Reproduce the concurrent ones concurrently.** `scripts/concurrent_borrows.py` fires
  simultaneous requests. Nothing you do one request at a time will show R1 or R2, which is exactly
  why they were never reproduced at the desk.
- **Reset between runs.** A dirty database from the previous experiment will mislead you. The
  command is in `README.md`.
- **Do not trust the HTTP responses.** For R1 and R2 every response is a success. Ask the
  database:

  ```sql
  SELECT copy_id, COUNT(*) FROM loans WHERE status='ACTIVE' GROUP BY copy_id HAVING COUNT(*) > 1;
  ```

  ```sql
  SELECT m.id, m.active_loan_count AS cached, COUNT(l.id) AS actual
  FROM members m LEFT JOIN loans l ON l.member_id = m.id AND l.status='ACTIVE'
  GROUP BY m.id, m.active_loan_count;
  ```

- **Read the statements in order.** `org.hibernate.SQL` is at `DEBUG`. For any single request,
  write down the SQL it sent and, crucially, *when* each statement was sent relative to the others.
  Two of these defects are entirely about ordering.
- **Ask InnoDB what locks were taken.** `SHOW ENGINE INNODB STATUS\G` and
  `SELECT * FROM performance_schema.data_locks;`. If a read takes no lock, a later write cannot
  defend the value that was read.
- **Control the clock.** `POST /api/admin/fines/accrual?asOf=...` prices the run as of any moment.
  Anything that "depends on what time we run it" can be turned into a deterministic experiment
  with one query parameter.
- **Follow one return all the way through.** For R3, put a breakpoint where the charge is priced
  and look at the loan row it is reading — not the object you expect, the row it actually read.

One habit that pays off throughout: for every write in this service, ask *what did I read to
decide this value, and could that reading still be true when I write it?*

---

## Hints

One at a time. Go back to the terminal between them.

### Level 1 — the shape of the problem

1. Two of these six (R1 and R2) only exist when two requests overlap. Four of them are
   deterministic and will reproduce with a single curl.
2. R1 and R2 are the same shape of mistake made about two different rows. Fixing one row does not
   protect the other.
3. R3 is not about the amount. The charge is never even attempted. Something upstream decides
   there is nothing to charge.
4. R4 is about a unit, not about a rule.
5. R6 is one character in one query.

### Level 2 — narrowing

6. R1: the code chooses a copy and then marks it as taken. Between those two things, what stops
   another request choosing the same copy? Write down the SQL for the choosing step and ask what
   lock it takes.
7. R2: the borrowing limit is checked against a number. Where does that number come from, and
   what writes it? Trace both.
8. R3: put a breakpoint where the return charge is priced and inspect the loan it loaded. Look at
   `returnedAt` on the row that came back from the database — not on the object the return handler
   is holding.
9. R3 again: the two pieces of work run in different transactions. Work out which one has
   committed by the time the other reads.
10. R4: find the single line that turns two timestamps into a number of days. Feed it
    `2026-09-18T18:00` and `2026-09-24T09:00` by hand and compare the answer to what the rule
    says.
11. R5: when a copy comes back and a hold is promoted, list everything that changes. Then list
    everything that would have to change for the copy to actually be reserved for that member.
12. R6: read the query behind `availableCopies` and compare the set of statuses it accepts with
    the set of statuses that can actually be lent.

### Level 3 — the mechanism

13. R1: a plain `SELECT` under REPEATABLE READ is a consistent read and takes no lock at all. Two
    transactions can both read the same row as available, and both will then happily write to it —
    the second write does not fail, it just overwrites.
14. R2: reading a value, adding one in application memory, and writing the result back is a lost
    update whenever two transactions interleave. The database never sees an increment; it sees two
    absolute assignments.
15. R3: `Propagation.REQUIRES_NEW` suspends the caller's transaction and opens a second one on a
    second connection. Uncommitted work from the first is invisible to the second, by definition.
16. R4: `ChronoUnit.DAYS.between` on two date-times counts *complete* 24-hour periods and rounds
    towards zero. The rule is about calendar days, which is a different question.
17. R5: a status on a reservation row is a note to a human. It is not a lock on the copy, and
    nothing in the borrow path consults it.
18. R6: `<>` excludes one value. Availability is a whitelist, not a blacklist.

### Level 4 — where to look

19. R1: `LoanService#borrow`, the call to `findFirstByBookIdAndStatusOrderByIdAsc`, and the
    absence of any lock mode on `BookCopyRepository`.
20. R2: `LoanService#borrow`, the limit check near the top against
    `MemberRepository#updateActiveLoanCount` near the middle.
21. R3: `FineService#assessOnReturn`, its `@Transactional` annotation, and the `returnedAt` check
    at the top of the method body.
22. R4: `FineService#daysOverdue`.
23. R5: `ReservationService#promoteNextInQueue`, and what it does *not* do to the copy.
24. R6: `BookCopyRepository#countAvailable`.

---

Do not tell me why. Reproduce it, trace it, then tell me what you found.

When you think you have one, give me:

1. the symptom, precisely,
2. the root cause,
3. the evidence — the query output, the statement order from the log, the lock InnoDB reports,
   the value at the breakpoint,
4. your proposed fix, and what it does *not* fix.

I will evaluate the reasoning.
