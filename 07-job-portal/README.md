# Hirestack — Job Portal Service

`job-portal-service` is the backend behind Hirestack, a job board with a built-in applicant
tracker. It serves three audiences from one API: the public job board, candidates managing
their applications, and recruiters working their company's hiring pipeline.

This is a **debugging lab project**. The application is feature-complete and starts
cleanly, but it does not behave correctly in every case. Work from `DEBUGGING_GUIDE.md`.
Do not open `SOLUTION.md` until you have finished.

---

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| Web | Spring MVC, REST/JSON |
| Security | Spring Security, HTTP Basic, BCrypt, `@EnableMethodSecurity` |
| Persistence | Spring Data JPA, Hibernate 6, MySQL 8 |
| Querying | `JpaSpecificationExecutor` and the Criteria API for the search |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.hirestack.portal
├── config/        SecurityConfig, PortalProperties
├── controller/    Job (public), Candidate, Recruiter
├── dto/           request and response payloads, JobSearchCriteria, PagedResponse, ApiError
├── entity/        Company, Candidate, AppUser, JobPosting, JobApplication + enums
├── exception/     domain exceptions + GlobalExceptionHandler
├── mapper/        entity to DTO translation
├── repository/    Spring Data JPA repositories
├── security/      PortalUser, PortalUserDetailsService
└── service/       JobService, JobSpecifications, ApplicationService, CandidateService
```

---

## The tenancy model

This is the part worth understanding before you touch anything else.

A recruiter belongs to a **company**, through `users.company_id`. A company has several
recruiters — Northwind has two — and they share the company's postings and pipelines. A
recruiter must be able to see and work on **everything their company owns**, including
postings a colleague created, and must never see anything belonging to another company.

So the ownership question for a recruiter is always *"does this row belong to my
company?"*, never *"did I create this row?"* and never *"am I a recruiter?"*. `PortalUser`
carries `companyId` for exactly this purpose.

Candidates are scoped to themselves: `users.candidate_id`, and an application belongs to
one candidate.

`ROLE_ADMIN` is a platform account and bypasses company scoping.

---

## Job posting lifecycle

```
DRAFT ──publish──► PUBLISHED ──close──► CLOSED
```

A `DRAFT` is a work in progress. It often contains the internal salary band and notes the
company has not agreed to publish, so it must never leave the recruiter's own screens.
Only `PUBLISHED` postings belong on the public board and only they accept applications.

---

## Running it

### 1. Backing services

```bash
docker compose -f infra/docker-compose.yml up -d mysql
```

MySQL on `localhost:3307` with a `jobportaldb` database.

### 2. Configuration

`application.yml` holds placeholders (`YOUR_DATABASE_*`) and reads every value from the
environment. The `local` profile — active by default — fills them in from
`infra/docker-compose.yml`.

### 3. Start

```bash
mvn spring-boot:run
```

Schema and seed are applied on every start; both are idempotent.

### 4. Sign in

| Account | Password | Role | Belongs to |
|---|---|---|---|
| `sana.qureshi@example.com` | `Password123!` | candidate | candidate 1, 6 years, applied to jobs 1 and 7 |
| `vikram.solanki@example.com` | `Password123!` | candidate | candidate 2, 3 years |
| `nadia.farouk@example.com` | `Password123!` | candidate | candidate 3, 8 years |
| `ethan.brandt@example.com` | `Password123!` | candidate | candidate 4, 1 year |
| `priya.nambiar@northwind.test` | `Password123!` | recruiter | Northwind (company 1) |
| `rahul.dev@northwind.test` | `Password123!` | recruiter | Northwind (company 1) |
| `fatima.ali@cobalt.test` | `Password123!` | recruiter | Cobalt Analytics (company 2) |
| `james.okoro@harbour.test` | `Password123!` | recruiter | Harbour Fintech (company 3) |
| `platform.admin@hirestack.test` | `Admin123!` | platform admin | — |

The seed has 9 postings: 6 `PUBLISHED`, 2 `DRAFT` and 1 `CLOSED`, across three companies,
three of them remote. Northwind's four postings are split between its two recruiters —
Priya created three and Rahul created one.

---

## Exercising it

```bash
BASE=http://localhost:8080
PRIYA='priya.nambiar@northwind.test:Password123!'
SANA='sana.qureshi@example.com:Password123!'

curl -s "$BASE/api/jobs"
curl -s "$BASE/api/jobs?location=Bengaluru&maxExperience=6"
curl -s "$BASE/api/jobs?remote=true"

curl -s -u "$SANA" -X POST "$BASE/api/jobs/8/applications" \
  -H 'Content-Type: application/json' -d '{"coverLetter":"Keen to talk about SRE."}'
curl -s -u "$SANA" "$BASE/api/applications"

curl -s -u "$PRIYA" "$BASE/api/recruiter/jobs"
curl -s -u "$PRIYA" "$BASE/api/recruiter/jobs/1/applications"
curl -s -u "$PRIYA" -X POST "$BASE/api/recruiter/applications/1/status" \
  -H 'Content-Type: application/json' -d '{"status":"INTERVIEW"}'
```

Full contracts are in [API.md](API.md).

---

## Inspecting state while you debug

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass jobportaldb \
  -e "select id, reference, company_id, posted_by_id, remote, status, title from job_postings order by id;"

docker exec -it lab-mysql mysql -ulabuser -plabpass jobportaldb \
  -e "select u.id, u.email, u.role_name, u.company_id, u.candidate_id from users u order by u.id;"
```

Any candidate with more than one live application to the same posting:

```sql
SELECT job_posting_id, candidate_id, COUNT(*) AS live
  FROM applications
 WHERE status IN ('SUBMITTED','SHORTLISTED','INTERVIEW','OFFERED')
 GROUP BY job_posting_id, candidate_id
HAVING COUNT(*) > 1;
```

**The generated SQL is the main tool in this project.** `org.hibernate.SQL` is already at
`DEBUG`, so every search shows you the `where` clause the Criteria API actually built. Add
bound parameters with:

```
logging.level.org.hibernate.orm.jdbc.bind=TRACE
```

Read the `where` clause against the filters you supplied. A predicate you did not ask for is
as interesting as one that is missing.

---

## Tests

```bash
mvn test
```

The suite covers job mapping and a repository slice against H2. It passes, and it never
builds a `Specification` or authenticates as anybody.
