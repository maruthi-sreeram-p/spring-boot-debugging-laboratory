# Debugging Guide — Hirestack Job Portal Service

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

---

## 1. Business context

Hirestack is a job board with an applicant tracker attached. One API serves three
audiences, and most of the difficulty in this system comes from that fact.

- **Job seekers** browse the public board. No account needed to look.
- **Candidates** apply and track their applications.
- **Recruiters** work for a company. They write postings, publish them, and move applicants
  through a pipeline. A company usually has several recruiters and they work the same
  requisitions — Priya writes a posting, Rahul screens the applicants, and both need the
  full picture.

Two things are commercially serious here. A **draft posting** carries the internal salary
band and notes the company has not agreed to publish; it is not for the public. And an
**applicant pipeline** — who applied where, and how far they got — is the single most
valuable thing a competitor could take.

---

## 2. How the system is supposed to behave

**The public board shows published postings only.** Drafts belong to the company that wrote
them. Closed postings are history. Neither appears on the board, under any combination of
filters, to anybody.

**A filter that is not supplied does not filter.** The board with no parameters shows
everything published — remote and on-site together. `remote=true` narrows to remote,
`remote=false` narrows to on-site, and omitting it narrows nothing.

**Recruiter scoping is by company, not by person.** Everything under `/api/recruiter` is
scoped to the caller's company. Two recruiters at the same company see the same postings
and the same pipelines. A recruiter at a different company sees `404` — indistinguishable
from a posting that does not exist.

**One live application per candidate per posting.** An application is live while it is
`SUBMITTED`, `SHORTLISTED`, `INTERVIEW` or `OFFERED`. A candidate cannot apply again while
one of those is in flight. A candidate who was `REJECTED` or who `WITHDREW` may apply
again — people do reapply, and that is allowed.

---

## 3. Symptoms

Nobody has told you how many distinct defects there are.

---

### Ticket HS-610 — "Our unpublished role is on the public site"

> Filed by: Cobalt Analytics, via their account manager
>
> We have a Staff Data Scientist requisition in draft. It has not been approved by the
> hiring committee and it has our internal band in the description.
>
> A candidate emailed us this morning asking about it. It is on your public job board. I
> opened it in a private window with no login and there it is.
>
> There is also a closed Northwind role showing on the board, which is a smaller problem but
> presumably the same one.

---

### Ticket HS-616 — "Remote jobs do not exist"

> Filed by: Job seeker, forwarded by support
>
> I have been checking your board for three weeks. You have never once shown me a remote
> role, and I know you have them because I found one through a search engine.
>
> If I tick the Remote box I get three results and they all look right. If I untick it they
> all disappear and I get the office-based ones instead.
>
> There does not seem to be any way to see all the jobs at once. Is that deliberate?

---

### Ticket HS-623 — "I cannot see my colleague's requisitions"

> Filed by: Priya, recruiter at Northwind
>
> Rahul and I both work Northwind hiring. We have four requisitions open between us.
>
> My screen shows three. Rahul's screen shows one. Neither of us can see all four.
>
> When Rahul is on leave I am supposed to cover his pipeline and I currently cannot even see
> his posting exists. I have been asking him to screenshot things for me.

---

### Ticket HS-628 — "A competitor has our applicant list"

> Filed by: Northwind, escalated by the account manager
>
> One of our candidates told us a recruiter from another company contacted them referencing
> our role by name and the fact that they were shortlisted for it.
>
> We only ever entered that in Hirestack.
>
> Please tell us who can see our pipelines.

---

### Ticket HS-634 — "The same person is in the pipeline twice"

> Filed by: Rahul, recruiter at Northwind
>
> Sana Qureshi appears twice on the Senior Backend Engineer pipeline. One entry says
> SHORTLISTED, which is where I put her last week. The other says SUBMITTED and is dated
> today.
>
> She says she applied once, did not hear back quickly, and applied again.
>
> Our screening counts are now wrong and I nearly interviewed her twice.

---

## 4. Investigation hints

Read **one** hint at a time and go back to the code before reading the next.

---

### HS-610 — drafts and closed postings on the public board

**Hint 1.** Reproduce it with no credentials at all, and look at the `status` field of every
result:

```bash
curl -s http://localhost:8080/api/jobs | grep -o '"status":"[A-Z_]*"' | sort | uniq -c
```

**Hint 2.** The public board and the search behind it are one method. Find where the
predicate list is built and read every predicate it can add.

**Hint 3.** The rule is not implemented anywhere, rather than implemented wrongly. That is
why there is nothing to find by reading the existing predicates carefully — ask instead what
is *absent*.

**Hint 4.** When you add it, think about where it belongs. The same search method may one day
serve a recruiter who legitimately needs to see drafts. Decide whether the constraint lives
in the specification, in the caller, or in a separate method, and why.

---

### HS-616 — remote postings never appear by default

**Hint 1.** Compare the three cases: no `remote` parameter, `remote=true`, `remote=false`.
Write down the result counts. Two of those three are identical, and that is the finding.

**Hint 2.** Every other filter in the search is skipped when the caller did not supply it.
Look at how the code decides "the caller did not supply this", and then look at the type of
the `remote` field.

**Hint 3.** What is the value of a `boolean` field on a freshly constructed Java object, and
how would any code ever distinguish that from a caller who genuinely asked for `false`?

**Hint 4.** The fix changes a type. Check the controller as well as the criteria object —
both sides have to agree that "absent" is representable.

---

### HS-623 — recruiters cannot see each other's postings

**Hint 1.** Read the tenancy section of the README, then find the repository method the
recruiter postings screen uses. Compare the column it filters on with the one the model
says it should.

**Hint 2.** The repository already has both methods. Only one of them is called.

**Hint 3.** This ticket and HS-628 are about the same rule, applied in two places. One place
scopes by the wrong thing; the other does not scope at all. Find both before you fix either,
because the fix is the same idea in both.

---

### HS-628 — another company read a pipeline

**Hint 1.** Two recruiters, two companies, one posting id. Reproduce it in two curl calls
before you read anything.

**Hint 2.** Several endpoints under `/api/recruiter` take an id that belongs to a company.
List them, and check each one for the company comparison. Not all of them have it.

**Hint 3.** One method in `ApplicationService` does this correctly and one does not. Put them
side by side — the correct one is your specification for the broken one.

**Hint 4.** Note the URL rules only say `hasAnyRole("RECRUITER","ADMIN")`. Ask yourself what
that can and cannot express about company ownership, and why no amount of role configuration
would have prevented this.

---

### HS-634 — duplicate live applications

**Hint 1.** The duplicate check exists and does fire in some cases. Find the exact
circumstances in which it fires, and the ones in which it does not.

**Hint 2.** Look at the repository method it calls and count the arguments. Then read the
rule in `API.md` and count the conditions it describes.

**Hint 3.** The rule deliberately permits reapplying after a rejection or a withdrawal, so
the fix is not "one application per candidate per posting". Work out precisely which
statuses must block a new application, then find or write a repository method that asks that
question.

**Hint 4.** The database has no constraint preventing this either. Think about whether one
could be written, given that the rule depends on status.

---

## 5. Before you call it fixed

- With no credentials, fetch the board and confirm **every** result is `PUBLISHED`. Then
  fetch it with each filter in turn and confirm drafts never leak through any of them.
- Check the three `remote` cases: absent must return the union of `true` and `false`.
- Sign in as Priya and as Rahul and confirm both see all four Northwind postings, including
  the draft and the closed one. Then confirm Fatima sees only Cobalt's.
- As Fatima, request every Northwind posting id and every Northwind application id across
  every recruiter endpoint. All must be `404`. Then repeat as Priya and confirm they all
  still work — a fix that locks the owner out is not a fix.
- Apply twice to the same posting as the same candidate and confirm the second attempt is
  `409`. Then withdraw and reapply, and confirm that is allowed. Then get rejected and
  reapply, and confirm that is allowed too.
- Check the database has no live duplicates:

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass jobportaldb -e "
SELECT job_posting_id, candidate_id, COUNT(*) FROM applications
 WHERE status IN ('SUBMITTED','SHORTLISTED','INTERVIEW','OFFERED')
 GROUP BY job_posting_id, candidate_id HAVING COUNT(*) > 1;"
```

- Read the generated SQL for a search with no filters and confirm the `where` clause contains
  only what you asked for.
- Re-run `mvn test`.

When you are done, ask for **verification mode** and I will check your work.
