# API — Athenaeum Circulation Service

Base URL `http://localhost:8080`. Authentication is HTTP Basic.

| Prefix | Access |
| ------ | ------ |
| `GET /api/catalogue/**` | public |
| `/api/circulation/**` | `ROLE_LIBRARIAN` or `ROLE_MEMBER` |
| `/api/admin/**` | `ROLE_LIBRARIAN` |
| `/actuator/health`, `/actuator/info` | public |

Errors:

```json
{
  "timestamp": "2026-03-04T11:20:31.114Z",
  "status": 404,
  "error": "Not Found",
  "message": "Member not found: 999",
  "path": "/api/circulation/loans",
  "details": []
}
```

---

## Catalogue

### `GET /api/catalogue/books?term=`

`term` matches the title or the author, case-insensitive. Omit it for everything.

```json
[
  {
    "id": 1,
    "isbn": "9780132350884",
    "title": "Clean Code",
    "author": "Robert C. Martin",
    "publisher": "Prentice Hall",
    "publishedYear": 2008,
    "shelfMark": "QA76.76.C55",
    "totalCopies": 5,
    "availableCopies": 5,
    "holdsWaiting": 0
  }
]
```

`availableCopies` is what the branch terminals show as borrowable. `200 OK`.

### `GET /api/catalogue/books/{bookId}`

The same object for one title. `200 OK`, `404 Not Found`.

### `GET /api/catalogue/books/{bookId}/copies`

Every physical copy and its status.

```json
[
  { "id": 1, "barcode": "ATH-000101", "branch": "Central", "status": "AVAILABLE", "acquiredOn": "2021-03-11" }
]
```

`status` is one of `AVAILABLE`, `ON_LOAN`, `RESERVED`, `LOST`, `REPAIR`. `200 OK`, `404 Not Found`.

### `GET /api/catalogue/books/{bookId}/holds`

The waiting queue, in position order. `200 OK`.

---

## Circulation

### `POST /api/circulation/loans`

Issues a copy. The service picks the lowest-numbered copy of the title that is on the shelf.

```json
{ "bookId": 1, "memberId": 4 }
```

`201 Created`:

```json
{
  "id": 9,
  "copyId": 1,
  "barcode": "ATH-000101",
  "bookId": 1,
  "title": "Clean Code",
  "memberId": 4,
  "membershipNumber": "LIB-0004",
  "borrowedAt": "2026-03-04T11:22:04",
  "dueAt": "2026-03-18T11:22:04",
  "returnedAt": null,
  "status": "ACTIVE",
  "renewalCount": 0,
  "overdue": false
}
```

| Code | When |
| ---- | ---- |
| `400` | missing `bookId` or `memberId` |
| `404` | no such member |
| `409` | no copy of that title is on the shelf |
| `422` | membership suspended, borrowing limit reached, or fines above 100.00 |

### `POST /api/circulation/returns`

Takes a copy back, by barcode.

```json
{ "barcode": "ATH-000502" }
```

The loan is closed, the copy goes back on the shelf, a late return is charged, and anybody
waiting for the title is told their hold is ready.

`200 OK` with the closed loan. `404 Not Found` for an unknown barcode, `422 Unprocessable Entity`
if that copy is not currently out.

### `POST /api/circulation/loans/{loanId}/renew`

Extends a loan by a full loan period from today.

`200 OK`. `404` if there is no such loan. `422` if the loan is closed, the renewal limit has been
reached, or another member is waiting for the title.

### `GET /api/circulation/loans/{loanId}`

`200 OK`, `404 Not Found`.

### `POST /api/circulation/holds`

```json
{ "bookId": 5, "memberId": 4 }
```

`201 Created`:

```json
{
  "id": 3,
  "bookId": 5,
  "title": "Introduction to Algorithms",
  "memberId": 4,
  "membershipNumber": "LIB-0004",
  "placedAt": "2026-03-04T11:24:10",
  "status": "WAITING",
  "queuePosition": 3,
  "heldCopyId": null,
  "readyUntil": null
}
```

`status` moves `WAITING` → `READY` when a copy comes back, then `FULFILLED` when collected.
`heldCopyId` and `readyUntil` are filled in when the hold becomes ready.

`404` for an unknown book or member, `422` if the member is suspended or already holds this title.

### `DELETE /api/circulation/holds/{reservationId}`

Cancels a hold and renumbers the queue behind it. `200 OK`, `404`, `422` if already collected.

### `GET /api/circulation/members/{memberId}`

```json
{
  "id": 2,
  "membershipNumber": "LIB-0002",
  "fullName": "Rahul Verma",
  "email": "rahul.verma@athenaeum.test",
  "tier": "PREMIUM",
  "status": "ACTIVE",
  "activeLoanCount": 2,
  "loanLimit": 8,
  "outstandingFines": 0.00,
  "joinedOn": "2020-06-30"
}
```

`200 OK`, `404 Not Found`.

### `GET /api/circulation/members/{memberId}/loans`

Every loan, newest first. `200 OK`, `404`.

### `GET /api/circulation/members/{memberId}/holds`

`200 OK`.

### `GET /api/circulation/members/{memberId}/fines`

```json
[
  {
    "id": 1,
    "loanId": 8,
    "memberId": 7,
    "amount": 15.00,
    "daysOverdue": 3,
    "assessedAt": "2026-02-04T10:15:00",
    "paidAt": null,
    "status": "OUTSTANDING"
  }
]
```

`200 OK`.

---

## Desk supervision — `ROLE_LIBRARIAN`

| Method | Path | Purpose |
| ------ | ---- | ------- |
| `GET` | `/api/admin/loans` | every active loan, soonest due first |
| `GET` | `/api/admin/loans/overdue` | active loans past their due date |
| `GET` | `/api/admin/members` | every member with their limit and outstanding charges |
| `POST` | `/api/admin/fines/accrual` | run the overdue accrual now |
| `POST` | `/api/admin/fines/{fineId}/pay` | mark a charge paid |
| `POST` | `/api/admin/fines/{fineId}/waive` | write a charge off |

### `POST /api/admin/fines/accrual`

Optional `asOf` query parameter, ISO-8601, to price the run as of a given moment rather than now:

```
POST /api/admin/fines/accrual?asOf=2026-09-19T05:56:37
```

```json
{
  "ranAt": "2026-09-19T05:56:37",
  "loansExamined": 2,
  "finesCreated": 1,
  "finesUpdated": 0,
  "totalAssessed": 25.00
}
```

`200 OK`. The same run happens automatically at 02:00 every night.

---

## Status codes in use

| Code | When |
| ---- | ---- |
| `200` | successful read or update |
| `201` | loan issued, hold placed |
| `400` | request body failed validation |
| `401` | no credentials on a protected route |
| `403` | authenticated, but not permitted |
| `404` | book, copy, member, loan, hold or fine not found |
| `409` | no copy of the title is on the shelf |
| `422` | a circulation rule refused the request |
| `500` | unhandled server error |
