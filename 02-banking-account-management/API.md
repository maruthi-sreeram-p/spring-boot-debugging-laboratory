# API — Meridian Bank Core Banking Service

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic** (email as username). The service is stateless — send the
`Authorization` header on every request.

Amounts are decimal strings with two fraction digits. Timestamps are UTC ISO-8601.

Error shape:

```json
{
  "timestamp": "2025-03-04T09:12:44.201Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Account MB-0000-1001 has insufficient funds: requested 900000.00, available 84250.00",
  "path": "/api/accounts/1/withdrawals",
  "details": ["amount: must be at least 0.01"]
}
```

Status conventions used throughout:

| Status | Meaning |
|---|---|
| 400 | the payload is malformed or fails validation |
| 401 | missing or invalid credentials |
| 403 | authenticated but not permitted |
| 404 | the resource does not exist, or does not belong to the caller |
| 409 | the resource exists but is in a state that forbids the operation |
| 422 | the request is well formed but breaks a business rule (funds, currency, limits) |

---

## Authentication

### `POST /api/auth/register`
Onboards a retail customer with `ROLE_CUSTOMER`. It does not open an account.

*Auth:* none.

```json
{
  "email": "ana.silva@example.com",
  "password": "Password123!",
  "fullName": "Ana Silva",
  "nationalId": "NID-5566-7788"
}
```

`nationalId` must match `NID-0000-0000`.

Response `201 Created`:
```json
{
  "id": 10,
  "email": "ana.silva@example.com",
  "fullName": "Ana Silva",
  "nationalId": "NID-5566-7788",
  "status": "ACTIVE",
  "roles": ["ROLE_CUSTOMER"]
}
```

| Status | When |
|---|---|
| 201 | customer onboarded |
| 400 | payload fails validation |
| 409 | the email or the national id is already registered |

### `POST /api/auth/login`
Verifies credentials and returns the profile. No token is issued; later calls use Basic
auth with the same credentials.

*Auth:* none. `200` on success, `401` otherwise.

### `GET /api/auth/me`
Profile of the caller. *Auth:* authenticated. `200` / `401`.

---

## Accounts

### `POST /api/accounts`
Opens an account for the caller.

*Auth:* authenticated.

```json
{ "accountType": "SAVINGS", "currency": "EUR", "openingBalance": 500.00 }
```

`accountType` is `CHECKING` or `SAVINGS`. `currency` is a three-letter uppercase code.
`openingBalance` must be at least `banking.account.minimum-opening-balance`.

Response `201 Created`:
```json
{
  "id": 7,
  "accountNumber": "MB-4417-2290",
  "accountType": "SAVINGS",
  "currency": "EUR",
  "balance": 500.00,
  "status": "ACTIVE",
  "openedAt": "2025-03-04T09:12:44.201Z"
}
```

| Status | When |
|---|---|
| 201 | account opened |
| 400 | payload fails validation, or the opening balance is below the minimum |
| 401 | not authenticated |

### `GET /api/accounts`
All accounts of the caller, oldest first. *Auth:* authenticated. `200` with an array.

### `GET /api/accounts/{accountId}`
One account belonging to the caller.

| Status | When |
|---|---|
| 200 | account returned |
| 401 | not authenticated |
| 404 | no such account, or it does not belong to the caller |

### `GET /api/accounts/{accountId}/transactions`
Statement for one account, newest first.

*Auth:* authenticated (the account must belong to the caller).

| Query parameter | Type | Default | Meaning |
|---|---|---|---|
| `from` | `yyyy-MM-dd` | – | start of the reporting period, **inclusive** |
| `to` | `yyyy-MM-dd` | – | end of the reporting period, **inclusive** |
| `page` | int | `0` | zero-based page index |
| `size` | int | `20` | page size, capped at 100 |

`from` and `to` are interpreted in UTC and are both inclusive: a statement for
`from=2024-11-01&to=2024-11-30` contains every transaction that happened in November,
including any booked on the 30th. If either bound is omitted the whole history is
returned.

Response `200 OK`:
```json
{
  "content": [
    {
      "id": 7,
      "reference": "WDR-20241102-000007",
      "type": "WITHDRAWAL",
      "amount": 2750.00,
      "balanceAfter": 84250.00,
      "counterpartyAccountNumber": null,
      "description": "Card payment - utilities",
      "createdAt": "2024-11-02T15:20:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1,
  "last": true
}
```

`counterpartyAccountNumber` is populated only for transfer legs.

---

## Money movement

### `POST /api/accounts/{accountId}/deposits`
Credits the account.

*Auth:* authenticated (account must belong to the caller).

```json
{ "amount": 2500.00, "description": "Cash deposit" }
```

`amount` must be at least `0.01` with at most two fraction digits.

Response `201 Created`: the resulting transaction, including `balanceAfter`.

| Status | When |
|---|---|
| 201 | deposit booked |
| 400 | payload fails validation |
| 404 | no such account, or it does not belong to the caller |
| 409 | the account is not `ACTIVE` |

Repeated deposits are independent: after *n* accepted deposits of *x*, the balance has
increased by exactly *n × x*, whether they arrived one at a time or simultaneously.

### `POST /api/accounts/{accountId}/withdrawals`
Debits the account. Same request shape as deposits.

| Status | When |
|---|---|
| 201 | withdrawal booked |
| 400 | payload fails validation |
| 404 | no such account, or it does not belong to the caller |
| 409 | the account is not `ACTIVE` |
| 422 | insufficient funds |

### `POST /api/transfers`
Moves money between two accounts. The source must belong to the caller; the destination
may belong to anyone.

*Auth:* authenticated.

```json
{
  "fromAccountNumber": "MB-0000-1001",
  "toAccountNumber": "MB-0000-1003",
  "amount": 1200.00,
  "description": "Rent share"
}
```

`amount` must be a positive amount with at most two fraction digits. Both accounts must be
`ACTIVE` and share a currency. The total moved out of the source account in one UTC day
may not exceed `banking.transfer.daily-limit` (50000.00).

Response `201 Created`:
```json
{
  "reference": "TRF-20250304-004182",
  "fromAccountNumber": "MB-0000-1001",
  "toAccountNumber": "MB-0000-1003",
  "amount": 1200.00,
  "sourceBalanceAfter": 83050.00,
  "description": "Rent share",
  "executedAt": "2025-03-04T09:12:44.201Z"
}
```

| Status | When |
|---|---|
| 201 | both legs booked and both balances updated |
| 400 | payload fails validation, or both account numbers are the same |
| 404 | the source account does not belong to the caller, or the destination does not exist |
| 409 | either account is not `ACTIVE` |
| 422 | insufficient funds, currency mismatch, or the daily limit would be exceeded |

A transfer is **atomic**. On any non-2xx response neither balance has moved and no ledger
rows exist. On `201` both balances have moved and exactly two ledger rows exist, sharing a
reference.

---

## Back office

### `POST /api/back-office/accounts/{accountNumber}/freeze`
Freezes an account so that no further money can move on it. Used by the fraud desk.

*Auth:* `ROLE_TELLER`.

Response `200 OK`: the account, with `status` now `FROZEN`. Once frozen, deposits,
withdrawals and transfers on that account are rejected with `409`.

| Status | When |
|---|---|
| 200 | account frozen |
| 401 | not authenticated |
| 403 | authenticated but not a teller |
| 404 | no such account number |

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness |
| `GET /actuator/info` | none | build info |
