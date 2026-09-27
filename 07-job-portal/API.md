# API — Hirestack Job Portal Service

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic**, stateless. The job board is public; everything else needs
credentials.

| Status | Meaning |
|---|---|
| 400 | payload or parameter malformed or fails validation |
| 401 | missing or invalid credentials |
| 403 | authenticated but the wrong kind of account for this area |
| 404 | no such resource, **or it belongs to another company or candidate** |
| 409 | duplicate email, or a duplicate application |
| 422 | well formed but breaks a hiring rule |

Anything a caller is not entitled to returns `404`, never `403`. A recruiter probing for
another company's posting ids must not be able to tell "exists but not yours" from "does not
exist".

---

## Public job board

### `GET /api/jobs`
Searches **published** postings. Draft postings are internal to the company that owns them
and closed postings are historical; neither appears here, whatever filters are supplied.

*Auth:* none.

| Query parameter | Type | Default | Meaning |
|---|---|---|---|
| `q` | string | – | case-insensitive substring of the title or description |
| `location` | string | – | exact location, case-insensitive |
| `employmentType` | `FULL_TIME` \| `PART_TIME` \| `CONTRACT` \| `INTERNSHIP` | – | |
| `companyId` | long | – | restrict to one company |
| `minSalary` | decimal | – | only postings that can pay at least this much |
| `maxExperience` | int | – | only postings asking for no more than this many years |
| `remote` | boolean | – | **omit to see every posting**; `true` for remote only; `false` for on-site only |
| `page` | int | `0` | |
| `size` | int | `20` | capped at 50 |

Filters combine with AND. A filter that is not supplied does not constrain the results — in
particular, **a search with no `remote` parameter returns remote and on-site postings
together**, which is what the board shows by default.

Response `200 OK`:
```json
{
  "content": [
    {
      "id": 2,
      "reference": "JOB-2025-0102",
      "companyId": 1,
      "companyName": "Northwind Retail Technologies",
      "title": "Platform Engineer (Remote)",
      "description": "Build internal tooling for a distributed team.",
      "location": "Remote",
      "employmentType": "FULL_TIME",
      "remote": true,
      "minExperience": 3,
      "minSalary": 2200000.00,
      "maxSalary": 3400000.00,
      "status": "PUBLISHED",
      "createdAt": "2025-04-05T11:30:00Z",
      "closesOn": "2025-12-31"
    }
  ],
  "page": 0, "size": 20, "totalElements": 6, "totalPages": 1, "last": true
}
```

### `GET /api/jobs/{jobId}`
One posting. *Auth:* none. `200` / `404`.

---

## Candidates

### `POST /api/auth/register`
Creates a candidate profile and its sign-in account. *Auth:* none. `201` / `400` / `409`.

### `GET /api/auth/me`
The caller's candidate profile. `200` / `401`.

### `POST /api/jobs/{jobId}/applications`
Applies to a posting.

*Auth:* a candidate account.

```json
{ "coverLetter": "Six years on JVM order systems, keen to talk." }
```

Rules:

- The posting must be `PUBLISHED`.
- **A candidate may hold only one live application per posting.** An application is live
  while it is `SUBMITTED`, `SHORTLISTED`, `INTERVIEW` or `OFFERED`. Re-applying while any of
  those is in flight is a duplicate and is rejected. A candidate whose application was
  `REJECTED` or who `WITHDREW` may apply again.
- A candidate may hold at most 25 live applications in total.

`201` / `400` / `401` / `404` / `409` / `422`.

### `GET /api/applications`
The caller's own applications, newest first. `page` and `size` supported. `200` / `401`.

### `POST /api/applications/{applicationId}/withdraw`
Withdraws one of the caller's own applications. `200` / `404`.

### `GET /api/companies`
All companies. *Auth:* none. `200`.

---

## Recruiters

*Auth for this whole section:* `ROLE_RECRUITER` or `ROLE_ADMIN`.

Everything here is scoped to **the caller's company**, not to the caller. Two recruiters at
the same company see exactly the same postings and the same pipelines; a recruiter at
another company sees `404`.

### `GET /api/recruiter/jobs`
Every posting belonging to the caller's company, newest first — including drafts, closed
postings, and postings a colleague created.

`200` / `401` / `403`.

### `POST /api/recruiter/jobs`
Creates a posting for the caller's company. It starts as `DRAFT`.

```json
{
  "title": "Senior Backend Engineer",
  "description": "Own and scale the order platform.",
  "location": "Bengaluru",
  "employmentType": "FULL_TIME",
  "remote": false,
  "minExperience": 5,
  "minSalary": 2800000.00,
  "maxSalary": 4200000.00,
  "closesOn": "2025-12-31"
}
```

`201` / `400` / `403` / `422` (max salary below min, or the account has no company).

### `POST /api/recruiter/jobs/{jobId}/publish`
Moves a posting to `PUBLISHED`. `200` / `404` / `422` (already closed).

### `POST /api/recruiter/jobs/{jobId}/close`
Moves a posting to `CLOSED`. `200` / `404`.

### `GET /api/recruiter/jobs/{jobId}/applications`
The applicant pipeline for one of the caller's company's postings, oldest first.

The response carries candidate contact details, so a posting belonging to another company
must be indistinguishable from one that does not exist.

```json
[
  {
    "id": 1,
    "jobPostingId": 1,
    "jobReference": "JOB-2025-0101",
    "jobTitle": "Senior Backend Engineer",
    "companyName": "Northwind Retail Technologies",
    "candidateId": 1,
    "candidateName": "Sana Qureshi",
    "candidateEmail": "sana.qureshi@example.com",
    "candidateHeadline": "Backend engineer, JVM and distributed systems",
    "status": "SHORTLISTED",
    "coverLetter": "Six years on JVM order systems, keen to talk.",
    "appliedAt": "2025-04-08T10:22:00Z",
    "updatedAt": "2025-04-15T09:00:00Z"
  }
]
```

`200` / `401` / `403` / `404`.

### `POST /api/recruiter/applications/{applicationId}/status`
Moves an applicant along the pipeline.

```json
{ "status": "INTERVIEW" }
```

`200` / `400` / `404`.

### `GET /api/recruiter/candidates/{candidateId}`
A candidate profile. `200` / `404`.

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness |
| `GET /actuator/info` | none | build info |
