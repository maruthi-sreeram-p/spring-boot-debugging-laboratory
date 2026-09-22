# Debugging Guide — Northgate HR Directory Service

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

---

## 1. Business context

Northgate is a 60-person company. `hr-directory-service` is the system of record for who
works here, what they do, which department they sit in and who they report to.

Four groups depend on it:

- **Everyone** uses the staff directory: search by name, filter by department, click
  through to a profile.
- **Managers** use the org-chart view, which is built from the reporting lines.
- **People Operations** maintain the records through the HR console — they are the only
  people allowed to create, edit or terminate an employee.
- **Finance** pulls compensation data, and they are the reason `salary` exists on these
  records at all.

Two things about this system are sensitive rather than merely inconvenient if they go
wrong. Salary is one: an employee seeing a colleague's pay is a serious incident, not a
bug report. The org chart is the other: if a reporting line disappears, that person stops
appearing in their manager's team, drops out of approval routing, and is missed in the
performance-review cycle.

---

## 2. How the system is supposed to behave

**Search filters narrow.** `GET /api/employees` combines every supplied filter with AND.
Adding a filter can only ever return the same number of results or fewer. Filtering by
department and typing a name returns people who are in that department **and** match the
name.

**Sorting works on what the table shows.** The directory table has columns for Name,
Email, Job title, Department, Manager, Hire date and Status, and every one of them is
clickable. The frontend sends the column key as `sort`.

**Compensation is need-to-know.** HR administrators see every salary. Everyone else sees
their own and nobody else's. When the caller is not entitled to a salary, the field is
omitted from the JSON. This is the rule everywhere an employee is returned — one at a
time, in a list, or in an org-chart response.

**Only People Operations may write.** Creating, updating and terminating an employee
require `ROLE_HR_ADMIN`. An ordinary employee attempting any of them gets `403`. The URL
rules in the filter chain deliberately do not express this; the rules live on the service
methods, next to the logic they protect.

**Editing one field changes one field.** The HR console has several small edit forms —
contact details, job details, compensation. Each form posts the record it knows about. In
particular `managerId` is only sent by the "change manager" screen, and a payload without
it must leave the existing reporting line exactly as it was.

**The directory is a hot read path.** It is loaded on almost every internal page. The
database team watches its query volume.

---

## 3. Symptoms

These are the reports that came in. Nobody has told you how many distinct defects there
are, and at least one report describes a consequence rather than a cause.

---

### Ticket HR-104 — "Sorting the directory by Name breaks the page"

> Filed by: Frontend team
>
> Click the **Name** column header in the directory and the API returns a 500. Same for
> **Manager**. Sorting by **Department**, **Hire date** or **Status** is fine.
>
> We send the column key as `sort`, which is documented as supported. The 500 body is the
> generic "Unexpected error" so we cannot tell you anything more from our side. There is
> presumably something in your logs.

---

### Ticket HR-109 — "Filters make the list longer"

> Filed by: Internal support, on behalf of several people
>
> Filtering the directory by Engineering gives 13 people, which looks right. Typing "nair"
> in the search box on its own gives 1 person, also right.
>
> Doing both at once gives 13 again. It should give at most 1.
>
> It gets stranger. If I then also set the status filter to Active, I get 58 results —
> almost the whole company. Every filter I add makes the list bigger.

---

### Ticket HR-115 — "I can see everyone's salary"

> Filed by: An employee, escalated to People Operations
>
> I am not in HR. If I open a colleague's profile page, their salary is not shown, which
> is what I would expect.
>
> But the directory list endpoint returns a `salary` field for every single person, mine
> and everyone else's. I noticed because I was looking at the network tab for something
> unrelated.
>
> Please treat this as urgent.

---

### Ticket HR-118 — "Someone who is not in HR terminated a record"

> Filed by: People Operations
>
> An employee record was marked TERMINATED yesterday. We did not do it. The person who did
> it has `ROLE_EMPLOYEE` and nothing else — they were poking at the API after reading
> HR-115 and told us straight away, which is the only reason we know.
>
> I checked and they can create records too. They created one called "Mallory Intruder" to
> demonstrate the point.
>
> The code clearly says these operations are HR-only. I have read the annotations myself.

---

### Ticket HR-122 — "People keep falling off the org chart"

> Filed by: Engineering manager, and separately by two others
>
> Three people have disappeared from my team view this month. They are still employed,
> still in the right department, and their profile pages look normal. They are just not
> under me any more, and they are not under anyone else either.
>
> The only thing I can correlate is that HR edited each of them shortly before — one was a
> phone number change, one was a job-title change. Nobody touched their manager.

---

### Ticket HR-127 — "Directory endpoint flagged in the slow query report"

> Filed by: Database team
>
> `/api/employees` is at the top of our statement-count report. A single request for one
> page of twenty employees issues **more than twenty** separate SELECT statements against
> `employees` and `departments`.
>
> It is fast enough today because the company is small and the box is idle. It will not
> stay that way, and this is the kind of thing that falls over the week after a headcount
> jump. Please look at it before it becomes an incident.

---

## 4. Investigation hints

Hints are graded. Read **one** at a time and go back to the code before reading the next.

---

### HR-104 — 500 when sorting by certain columns

**Hint 1.** The generic 500 body is the exception handler doing its job. The real exception
type and message are in the application log. Read them before anything else — this one
names the problem almost literally.

**Hint 2.** Note which columns work and which do not: Department works, Manager and Name do
not. Work out what Department has in common with the entity that Manager and Name lack.
The answer explains both the failures and the one that unexpectedly succeeds.

**Hint 3.** The value the client sends goes into sorting untouched. Trace it from the
request parameter to the object handed to the repository, and ask who is responsible for
deciding that a client-supplied string names something real.

**Hint 4.** Once you know the cause, note the second defect hiding behind it: whatever you
decide to do about an unsortable field, the caller should not be told it was a server
fault. Decide what status this deserves.

---

### HR-109 — filters widen instead of narrowing

**Hint 1.** Each filter is correct on its own and only the combination is wrong. That rules
out the individual predicates and points at the thing that joins them.

**Hint 2.** The search predicate is assembled in one small class. Read the last line of the
method that builds it.

**Hint 3.** The name search legitimately needs OR — first name or last name or email. Look
at whether that OR stayed where it belongs.

**Hint 4.** If you want to see it directly, log the generated SQL for a request with two
filters and read the `where` clause:

```
logging.level.org.hibernate.SQL=DEBUG
```

---

### HR-115 — salaries exposed in the list

**Hint 1.** The single-employee endpoint gets it right and the list endpoint does not.
Compare the two service methods side by side — the difference is the whole finding.

**Hint 2.** The rule is implemented once, in one place, and applied in some paths but not
all of them. Find every method that turns an `Employee` into an `EmployeeResponse` and
check which of them apply it.

**Hint 3.** Think about where a rule like this *should* live so that it cannot be forgotten
by the next endpoint somebody adds. The fix that stops this recurring is not the same as
the fix that closes the ticket.

---

### HR-118 — write operations are not restricted

**Hint 1.** People Operations are right: the annotations are there and they are correct.
Stop reading them and start asking whether anything is evaluating them.

**Hint 2.** Turn on security logging and call the endpoint as an ordinary employee:

```
logging.level.org.springframework.security=DEBUG
```

Look for evidence of an authorization decision being made at the service layer. Absence of
evidence is the finding here.

**Hint 3.** Method-level rules are not on by default; something has to switch them on. Find
the switch and read its arguments carefully — not just its name.

**Hint 4.** This one interacts with HR-115. Think about what an ordinary employee can do
once they have both, and fix them in the order that closes the hole fastest.

---

### HR-122 — reporting lines disappearing after unrelated edits

**Hint 1.** The manager correlates each disappearance with an HR edit that had nothing to
do with managers. Take that literally and reproduce it: read an employee's `manager_id`,
send an update that changes only the phone number, and read `manager_id` again.

**Hint 2.** Compare what the API contract says about a missing `managerId` with what the
update method does when it receives one.

**Hint 3.** Look at how the update method distinguishes "the client did not mention this
field" from "the client wants this field cleared". Then look at whether the request object
even allows those two cases to be told apart.

---

### HR-127 — too many queries per directory page

**Hint 1.** Reproduce the measurement before you theorise. Count the statements one request
produces:

```bash
BEFORE=$(wc -l < app.log)
curl -s -o /dev/null -u "$AUTH" "http://localhost:8080/api/employees?size=20"
tail -n +$BEFORE app.log | grep -c select
```

**Hint 2.** Group the statements you see by the table they hit. The shape of the repetition
tells you which associations are responsible.

**Hint 3.** One query fetches the page. Everything after it is triggered by something that
happens later in the request, in a different class. Find what reads those associations.

**Hint 4.** There is more than one way to fix this — a fetch join, an entity graph, a
projection that never loads the associations at all. They are not equivalent once paging is
involved. Work out which of them stay correct with `Pageable`, and why one of them would
quietly change your page size.

---

## 5. Before you call it fixed

- Reproduce every symptom once more with the same commands and confirm each is gone.
- For HR-109, check the whole matrix, not one case: no filter, each filter alone, every
  pair, and all three together. The result count must never increase when you add a filter.
- For HR-115, check **every** endpoint that returns an employee, as an ordinary employee,
  as a manager and as HR. It is easy to fix the list and miss another path.
- For HR-118, verify both directions: an ordinary employee must get `403`, and HR must
  still get `201` / `200` / `204`. A fix that locks everyone out is not a fix.
- For HR-122, confirm the org chart is intact afterwards:

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass hrdb \
  -e "select id, employee_code, first_name, last_name from employees where manager_id is null;"
```

Exactly one person should have no manager.

- For HR-127, re-measure the statement count and compare it against what you started with.
  Then confirm the page still contains the right number of people and `totalElements` is
  still right — the usual fixes for this problem are exactly the ones that break paging.
- Re-run `mvn test`. It passed before you started. Think about why.

When you are done, ask for **verification mode** and I will check your work.
