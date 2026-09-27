# SOLUTION — Hirestack Job Portal Service

> **Answer key. Do not read this until you have finished investigating.**

Five defects. Two are query construction, two are tenancy scoping, one is a business rule
half-implemented. All are deterministic.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| J1 | HS-628 | The applicant pipeline endpoint never checks the posting's company | `service/ApplicationService.java` |
| J2 | HS-616 | `remote` is a primitive `boolean`, so the filter is always applied | `dto/JobSearchCriteria.java` + `service/JobSpecifications.java` |
| J3 | HS-610 | The public search never restricts to `PUBLISHED` | `service/JobSpecifications.java` |
| J4 | HS-634 | The duplicate check only looks for `SUBMITTED` applications | `service/ApplicationService.java` |
| J5 | HS-623 | Recruiter postings are scoped by user id instead of company id | `service/JobService.java` |

---

## J1 — Any recruiter can read any company's pipeline

### Symptom
A recruiter at Cobalt Analytics requests `GET /api/recruiter/jobs/1/applications`, where
posting 1 belongs to Northwind, and receives Northwind's full pipeline: candidate names,
email addresses, cover letters and pipeline stages.

```
Sana Qureshi   | sana.qureshi@example.com   | SHORTLISTED | JOB-2025-0101 at Northwind
Vikram Solanki | vikram.solanki@example.com | REJECTED    | JOB-2025-0101 at Northwind
HTTP 200
```

### Root cause
```java
public List<ApplicationResponse> applicationsForPosting(Long jobPostingId, PortalUser principal) {
    JobPosting job = jobPostingRepository.findById(jobPostingId)
            .orElseThrow(() -> new ResourceNotFoundException("Job posting", jobPostingId));

    List<JobApplication> applications =
            jobApplicationRepository.findByJobPostingIdOrderByAppliedAtAsc(job.getId());
    return portalMapper.toApplicationResponses(applications);
}
```

`principal` is accepted and never read. The posting is loaded by primary key and its company
is never compared with the caller's.

The URL rule `.requestMatchers("/api/recruiter/**").hasAnyRole("RECRUITER","ADMIN")` is
doing its job — it confirms the caller is *a* recruiter. It cannot express *whose*
recruiter, because that is a property of the row, not of the caller.

Two siblings in the same codebase get this right:

```java
// ApplicationService.updateStatus
if (!principal.isAdmin()
        && !application.getJobPosting().getCompany().getId().equals(principal.getCompanyId())) {
    throw new ResourceNotFoundException("Application", applicationId);
}

// JobService.loadOwnedPosting
if (!principal.isAdmin() && !job.getCompany().getId().equals(principal.getCompanyId())) {
    throw new ResourceNotFoundException("Job posting", jobId);
}
```

So the codebase clearly knows the rule. One read endpoint does not apply it — and it happens
to be the one that returns the most sensitive data in the system.

### Exact location
`src/main/java/com/hirestack/portal/service/ApplicationService.java`,
`applicationsForPosting`.

### Correct fix
Reuse the existing ownership helper rather than writing a third copy of the check. Extract
`JobService.loadOwnedPosting` into something both services can call, or add the same guard:

```java
JobPosting job = jobPostingRepository.findById(jobPostingId)
        .orElseThrow(() -> new ResourceNotFoundException("Job posting", jobPostingId));
if (!principal.isAdmin() && !job.getCompany().getId().equals(principal.getCompanyId())) {
    throw new ResourceNotFoundException("Job posting", jobPostingId);
}
```

Structurally better is to scope the query itself, so an unscoped read is not expressible:

```java
Optional<JobPosting> findByIdAndCompanyId(Long id, Long companyId);
```

`404` rather than `403` is deliberate. A `403` confirms the posting exists, which lets a
competitor enumerate ids and learn how many requisitions you are running.

### Affected components
`ApplicationService`, `RecruiterController`, `JobApplicationRepository`, `PortalUser`, and
the confidentiality of every pipeline on the platform.

### Underlying concept
**Role-based rules cannot express tenancy.** `hasRole("RECRUITER")` answers "what kind of
user is this?". Multi-tenant data needs "does this row belong to this user's tenant?", which
depends on the row and can only be answered where the row is loaded.

This is OWASP's Broken Access Control in its most common form, and the most reliable defence
is structural: make the tenant a mandatory parameter of the query, so that writing an
unscoped read requires deliberately calling a different method.

### Why this is realistic
The endpoint was written to display a pipeline the recruiter had just navigated to from
their own postings list — where every id genuinely was theirs. The assumption was true at the
time and became false the moment somebody typed a different number. `principal` is even in
the signature, which makes the method look security-aware.

The other endpoints being correct is what makes this hard: a reviewer who spot-checks
`updateStatus` and `publish` finds careful ownership checks and concludes the file is safe.

### Detecting it faster next time
- **Two accounts, one id.** For every endpoint that takes an id belonging to a tenant, sign
  in as the wrong tenant and call it. It takes minutes and finds the whole class.
- An unused `principal` parameter is a red flag your IDE will already be greying out.
- Grep for the tenant field — `getCompanyId()` — and check every endpoint that loads a
  tenant-owned row appears in the results.

### Prevention
- Scope in the repository: `findByIdAndCompanyId`, not `findById` plus a check.
- One ownership helper, used everywhere, rather than a copy per service.
- A negative test per tenant-owned endpoint asserting `404` for a foreign id.

---

## J2 — Remote postings never appear unless you ask for them

### Symptom
The board with no filters returns 6 postings, all of them on-site. `remote=true` returns 3,
all remote. `remote=false` returns the same 6 as no filter at all. There is no request that
returns all 9.

### Root cause
```java
public class JobSearchCriteria {
    ...
    private boolean remote;
}
```

and

```java
predicates.add(builder.equal(root.get("remote"), criteria.isRemote()));
```

Every other filter is guarded by a null check, because every other field is a wrapper type
or a `String`:

```java
if (criteria.getEmploymentType() != null) { ... }
if (criteria.getMinSalary() != null) { ... }
```

`remote` is a **primitive `boolean`**. It cannot be null, so "the caller did not supply it"
is not representable — the field defaults to `false` and the predicate is added
unconditionally. Omitting the parameter therefore means `remote = false`, which is why
absent and `false` return identical results.

The controller compounds it with `@RequestParam(defaultValue = "false") boolean remote`,
which manufactures a value where there was none.

### Exact location
`src/main/java/com/hirestack/portal/dto/JobSearchCriteria.java` (the field type),
`src/main/java/com/hirestack/portal/service/JobSpecifications.java` (the unguarded
predicate), and `JobController.search` (the defaulted request parameter).

### Correct fix
Make absence representable, end to end:

```java
// JobSearchCriteria
private Boolean remote;

// JobSpecifications
if (criteria.getRemote() != null) {
    predicates.add(builder.equal(root.get("remote"), criteria.getRemote()));
}

// JobController
@RequestParam(required = false) Boolean remote
```

All three have to change together. Fixing only the specification leaves the controller
supplying `false`; fixing only the controller leaves the predicate unguarded.

### Affected components
`JobSearchCriteria`, `JobSpecifications`, `JobController`, the public board, and every
candidate who never saw a remote role.

### Underlying concept
**Primitives cannot express absence.** In a filter object, every field needs three states:
"not supplied", "supplied as true", "supplied as false". A `boolean` has two, and the
missing one silently collapses into `false`.

The rule generalises: **search and patch DTOs should use wrapper types**, because both are
about what the caller *said*, not about what the value *is*. The same mistake with `int`
produces filters pinned to `0`; with `long`, filters pinned to id `0`.

And note the business cost. This is not a crash — it is a silent reduction of the product's
inventory by a third, visible only to the users who never found what they were looking for
and therefore never filed a ticket. It took a persistent job seeker three weeks to report
it.

### Why this is realistic
Lombok generates `isRemote()` for a primitive and the call site reads perfectly. The
Criteria line is shorter than its neighbours precisely because it has no null guard, which
reads as clean rather than as suspicious. And `@RequestParam(defaultValue = "false")` looks
like good defensive practice.

It also passes every demo, because a demo with one filter ticked works exactly as intended.

### Detecting it faster next time
- **Compare "absent" against "explicitly false".** If they behave identically, absence is not
  representable. One extra curl call.
- Read the generated `where` clause for an unfiltered search. A predicate you did not ask for
  is the answer:

```
logging.level.org.hibernate.SQL=DEBUG
```

- Scan filter DTOs for primitive fields. Each one is this defect waiting for a user.

### Prevention
- Wrapper types in every criteria and patch DTO, as a convention.
- A test asserting `search(noFilters)` returns the union of `remote=true` and
  `remote=false`.
- A static check or review rule against primitives in DTOs bound to optional parameters.

---

## J3 — Draft and closed postings are on the public board

### Symptom
`GET /api/jobs`, with no credentials, returns `JOB-2025-0103` (a Northwind `DRAFT`) and
`JOB-2025-0099` (a `CLOSED` posting). With `remote=true` it also returns `JOB-2025-0106`, a
Cobalt `DRAFT` whose description reads *"Not yet approved by the hiring committee. Internal
band L7."*

### Root cause
`JobSpecifications.matching` builds predicates for every filter the caller supplied and
nothing else. There is no predicate on `status`, anywhere.

The board is public — `.requestMatchers(HttpMethod.GET, "/api/jobs", "/api/jobs/**").permitAll()` —
so the entire `job_postings` table is exposed to anonymous callers, including salary bands
and internal notes on unapproved requisitions.

`GET /api/jobs/{jobId}` has the same hole: `JobService.getJob` loads by id with no status
check, so a draft is readable directly by id.

This is an *absence*, not a mistake, which is why reading the existing predicates carefully
finds nothing. The question to ask of a specification is always "what is missing?" as well
as "what is wrong?".

### Exact location
`src/main/java/com/hirestack/portal/service/JobSpecifications.java` (no status predicate),
and `JobService.getJob` (no status check).

### Correct fix
Make the public path structurally public. The cleanest shape separates the audiences rather
than adding a flag:

```java
public static Specification<JobPosting> publiclyVisible(JobSearchCriteria criteria) {
    return matching(criteria).and((root, query, builder) ->
            builder.equal(root.get("status"), JobStatus.PUBLISHED));
}
```

`JobService.search` — which backs the public board — uses `publiclyVisible`. If a recruiter
search is added later it composes `matching(...)` with its own company predicate, and the
public constraint cannot be forgotten because the public entry point does not expose the
unconstrained version.

`getJob` needs the same treatment:

```java
JobPosting job = jobPostingRepository.findById(jobId)
        .filter(posting -> posting.getStatus() == JobStatus.PUBLISHED)
        .orElseThrow(() -> new ResourceNotFoundException("Job posting", jobId));
```

Whether `CLOSED` postings should remain readable by direct link is a product decision —
many boards keep them for SEO and existing links — but they must not appear in search
results, and the decision should be written down either way.

### Affected components
`JobSpecifications`, `JobService`, `JobController`, `SecurityConfig` (which makes the
endpoint anonymous), and the confidentiality of every unpublished requisition.

### Underlying concept
**A search that is reachable anonymously must constrain visibility inside the query, not in
the caller.** Anything the specification does not exclude is public. There is no second line
of defence: the filter chain has already said `permitAll`, and the mapper will faithfully
serialise whatever the query returns.

More generally, **the default for a query must be the most restrictive audience**, with
wider access added explicitly. Building "everything" and expecting each caller to narrow it
inverts the safety property — a new caller that forgets is a leak, rather than an empty
result.

### Why this is realistic
The status column exists and is used correctly elsewhere: `apply` refuses non-`PUBLISHED`
postings, and the lifecycle is properly implemented. Only the read path forgot. Drafts are
rare in a fresh dev database, so the board looks right locally, and the leak only becomes
visible once someone actually saves a draft and leaves it there.

The `permitAll` on the URL is also the kind of line that gets written once, early, when the
endpoint returned only published postings by construction.

### Detecting it faster next time
- **Summarise the field you care about across a response**, rather than eyeballing rows:

```bash
curl -s http://localhost:8080/api/jobs | grep -o '"status":"[A-Z_]*"' | sort | uniq -c
```

One line tells you drafts are present.

- For every anonymous endpoint, ask: what is the full set of rows this can return, and is
  every one of them safe to hand to a stranger?
- Compare the row count the API returns against `SELECT COUNT(*)` on the table. If they
  match, nothing is being filtered.

### Prevention
- Separate the public query from the internal one at the method level, so the public entry
  point cannot express "all statuses".
- A test that saves a draft and asserts it is absent from the public board and from the
  public detail endpoint.
- Treat every `permitAll` as requiring a written note about exactly what it exposes.

---

## J4 — A candidate can hold two live applications to one posting

### Symptom
Sana Qureshi appears twice on the Senior Backend Engineer pipeline: one `SHORTLISTED` from
last week, one `SUBMITTED` from today. Applying again returned `201`.

### Root cause
```java
if (jobApplicationRepository.existsByJobPostingIdAndCandidateIdAndStatus(
        job.getId(), candidate.getId(), ApplicationStatus.SUBMITTED)) {
    throw new DuplicateApplicationException(job.getReference());
}
```

The check asks a narrower question than the rule. It blocks a second application only while
the first is still `SUBMITTED`. As soon as a recruiter moves it to `SHORTLISTED`,
`INTERVIEW` or `OFFERED`, the candidate can apply again — and the further a candidate gets,
the more likely they are to follow up.

The rule in `API.md` is that all four *live* statuses block, while `REJECTED` and
`WITHDRAWN` deliberately do not. The constant expressing that set already exists a few lines
above, and is used for the separate "maximum open applications" check:

```java
private static final List<ApplicationStatus> OPEN_STATUSES = List.of(
        ApplicationStatus.SUBMITTED, ApplicationStatus.SHORTLISTED,
        ApplicationStatus.INTERVIEW, ApplicationStatus.OFFERED);
```

So the right vocabulary is in the file; the duplicate check just does not use it.

### Exact location
`src/main/java/com/hirestack/portal/service/ApplicationService.java`, the duplicate check in
`apply`.

### Correct fix
Add a repository method that asks the real question and use the existing constant:

```java
// JobApplicationRepository
boolean existsByJobPostingIdAndCandidateIdAndStatusIn(
        Long jobPostingId, Long candidateId, Collection<ApplicationStatus> statuses);
```

```java
if (jobApplicationRepository.existsByJobPostingIdAndCandidateIdAndStatusIn(
        job.getId(), candidate.getId(), OPEN_STATUSES)) {
    throw new DuplicateApplicationException(job.getReference());
}
```

A database constraint would be stronger, and is worth thinking about even though the rule is
status-dependent: a partial unique index on `(job_posting_id, candidate_id)` where the status
is live expresses it exactly in PostgreSQL. MySQL has no partial indexes, so the usual
technique is a generated column that is `NULL` for terminal statuses and the candidate id
otherwise, with a unique index over it. Either way, a check-then-insert in application code
is also racy — two simultaneous applications can both pass the check.

### Affected components
`ApplicationService`, `JobApplicationRepository`, the `applications` table, the recruiter
pipeline and every metric derived from it.

### Underlying concept
**A guard must ask the same question as the rule.** The rule names a *set* of statuses; the
guard named one member of it. The narrowed version is not obviously wrong when read on its
own — it only becomes wrong next to the specification.

There is a second, quieter lesson: **the constant naming the right set already existed in
the file.** When a codebase contains a well-named collection of the values a rule cares
about, a check that hard-codes a single value is almost always a defect.

### Why this is realistic
When the endpoint was written, `SUBMITTED` was the only status a new application could have,
and "has this candidate already applied?" and "does a SUBMITTED application exist?" were the
same question. The pipeline statuses were added later. The check kept working for the case
everybody tests — apply twice in a row — and stopped working for the case that only occurs
after a recruiter has done something, days later.

### Detecting it faster next time
- **Test the rule across the state space, not just the happy path.** Apply, advance the
  status, apply again. The bug is in the second column of that table.
- A `...AndStatus(SOMETHING)` in a uniqueness or guard query deserves suspicion: uniqueness
  rules are rarely about one status.
- Reconcile with a query. The duplicate-detection SQL in the README finds every instance in
  one statement, including any already in production.

### Prevention
- Express status sets as named constants and use them in every check.
- A database constraint where the dialect allows one; otherwise a scheduled reconciliation.
- Tests for the transitions, not only the initial state.

---

## J5 — Recruiters cannot see their colleagues' postings

### Symptom
Northwind has four postings. Priya's screen shows three, Rahul's shows one, and neither sees
all four. Cover during leave is impossible.

### Root cause
```java
public PagedResponse<JobPostingResponse> postingsForRecruiter(PortalUser principal, Pageable pageable) {
    Page<JobPosting> page = jobPostingRepository
            .findByPostedByIdOrderByCreatedAtDesc(principal.getUserId(), pageable);
    ...
}
```

The list is scoped by `posted_by_id` — the individual who created the row — rather than by
`company_id`. The tenancy model says a recruiter works their *company's* requisitions.

`JobPostingRepository` already declares both methods:

```java
Page<JobPosting> findByPostedByIdOrderByCreatedAtDesc(Long postedById, Pageable pageable);
Page<JobPosting> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
```

Only the wrong one is called, and the right one is dead code — which is itself the clue.

### Exact location
`src/main/java/com/hirestack/portal/service/JobService.java`, `postingsForRecruiter`.

### Correct fix
```java
Page<JobPosting> page = jobPostingRepository
        .findByCompanyIdOrderByCreatedAtDesc(principal.getCompanyId(), pageable);
```

Handle the admin case deliberately: a `ROLE_ADMIN` has no `companyId`, so this returns
nothing for them. Either give admins a separate endpoint or branch explicitly — do not let
`null` fall through and quietly return an empty page, because "no results" is exactly what
this defect already looked like.

`posted_by_id` is still worth keeping. It is useful for attribution and audit; it is just
not the tenancy boundary.

### Affected components
`JobService`, `JobPostingRepository`, `RecruiterController`, and every multi-recruiter
company on the platform.

### Underlying concept
**Ownership and authorship are different relations, and CRUD code confuses them constantly.**
"Who created this row" is metadata; "who is allowed to work on it" is policy. They coincide
in a single-user tenant and diverge the moment a second colleague joins — which is to say,
for every real customer.

It is worth noticing the symmetry with J1. Both are the same rule, wrong in opposite
directions: this endpoint scopes too narrowly, that one does not scope at all. Both would be
fixed by a single, shared definition of "rows this principal may work with" — which is what
makes the shared-helper fix better than patching two methods independently.

### Why this is realistic
The first customer had one recruiter, for whom `postedById` and `companyId` selected exactly
the same rows. The repository method was added later by someone who did understand the model
and then went unused, most likely because whoever wired the screen picked the first method
whose name looked plausible.

It also presents as a feature request rather than a bug — "I would like to see my
colleague's postings" — which is why it sat unfixed while Rahul screenshotted things.

### Detecting it faster next time
- **Seed data with two users in one tenant.** A tenancy defect is invisible with one user per
  tenant, in exactly the same way a concurrency defect is invisible with one request.
- An unused repository method is a strong signal. Check what calls the sibling.
- Compare what two users of the same tenant see. If the sets differ, the scoping is by user.

### Prevention
- Name repository methods after the policy — `findAllForCompany` — rather than the column.
- One place that answers "which rows may this principal work with", used by every endpoint.
- Multi-user-per-tenant fixtures in tests and in seed data.

---

## How these interact

**J1 and J5 are one rule broken twice, in opposite directions.** The pipeline endpoint
does not scope at all; the postings list scopes to the wrong column. Fixing them separately
produces two more ad-hoc checks; fixing them together produces the shared ownership helper
the codebase is clearly missing. A developer who fixes only the ticket in front of them will
write the third copy of a check that already exists twice.

**J2 hides J3, partially.** Because the default board only ever showed on-site postings, the
remote draft (`JOB-2025-0106`, with the internal band in its description) was invisible on
the default view and only appeared once someone ticked Remote. Fix J2 first and the leak gets
*larger*, which is the correct behaviour of a correct fix and will look alarming in a demo.
That ordering is worth understanding before you deploy.

**J3 and J1 are the same class of problem at different scopes** — one leaks company data to
the entire internet, the other to one competitor. The first is worse and is also the easier
fix.

**J4 is independent.** It is a business-rule defect with no security dimension, and it is the
only one here a normal user could trigger by accident.

**Suggested fix order:** J3 (public leak, fix now), J1 (cross-tenant leak), J5 (same rule,
same helper, finish the job), J4 (business rule), J2 (last, because it widens the board and
you want J3 closed before that happens).

---

## What the test suite tells you

`mvn test` passes with all five defects live.

`PortalMapperTest` maps a posting built by hand — it never runs a query, so it cannot see J2
or J3. `JobApplicationRepositoryTest` verifies that applications can be listed per posting
and that open applications can be counted per candidate; both queries are correct, and the
defects are in the *callers* that decide which query to run and what to compare.

Nothing in the suite authenticates as anybody, so no test can observe a tenancy defect at
all. That is the structural gap: **authorization is a property of a request, and a test that
never makes a request cannot test it.** The tests that would have caught J1 and J5 are
`@SpringBootTest` with `@WithUserDetails`, making the same call as two different users and
comparing what comes back.
