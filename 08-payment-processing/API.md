# API — Ledgerline Payment Processing Service

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic**, stateless.

| Status | Meaning |
|---|---|
| 400 | payload or header malformed or fails validation |
| 401 | missing or invalid credentials |
| 403 | authenticated but lacks the role |
| 404 | no such order, payment or merchant |
| 409 | the payment is not in a state that allows the operation |
| 422 | well formed but breaks a payment rule |
| 504 | the acquirer did not respond; the outcome is unknown |

Roles: `ROLE_MERCHANT` and `ROLE_PAYOPS`.

---

## Orders

An order is the thing being paid for. It carries the authoritative amount.

| Order status | Meaning |
|---|---|
| `AWAITING_PAYMENT` | no money has been taken |
| `PAID` | **the full amount has been successfully taken** |
| `PAYMENT_FAILED` | a payment attempt completed and failed |
| `CANCELLED` | the order was abandoned |

`PAID` is the status the merchant's fulfilment system watches. It means goods can ship, so
it must never be set unless a payment has actually succeeded for the full order amount.

### `POST /api/orders`
```json
{ "merchantCode": "MER-NORTHWIND", "customerRef": "CUST-8841", "amount": 1299.00, "currency": "INR" }
```
`201` / `400` / `404` / `422` (merchant inactive).

### `GET /api/orders/{orderRef}`
`200` / `404`.

### `GET /api/orders/{orderRef}/payments`
Every payment attempted against the order, oldest first. `200` / `404`.

---

## Payments

| Payment status | Meaning |
|---|---|
| `PENDING` | created, not yet sent |
| `PROCESSING` | sent to the acquirer, outcome not yet known |
| `SUCCEEDED` | the acquirer captured the money |
| `FAILED` | the acquirer refused, or reconciliation confirmed no capture |
| `ABANDONED` | closed administratively |

### `POST /api/payments`
Takes a payment for an order.

*Auth:* `ROLE_MERCHANT`.
*Required header:* `Idempotency-Key` — a value unique to this attempt, supplied by the
caller.

```json
{ "orderRef": "ORD-5008", "amount": 3300.00, "instrument": "CARD_VISA" }
```

**Idempotency.** A key identifies one payment attempt. Replaying a request with a key that
has already been used returns the payment that key created, in whatever state it reached,
and does **not** contact the acquirer again. This is what makes it safe for a client to
retry after a dropped connection. Retrying legitimately — after a genuine decline, say —
means issuing a **new** key.

**Amount.** The amount charged is the amount owed on the order. The request body is a
statement of intent from a client that may be out of date or hostile; the order row is the
authority. A request that disagrees with the order is rejected rather than honoured.

**Outcome.** On approval the payment becomes `SUCCEEDED` and the order becomes `PAID`. On
decline the payment becomes `FAILED` and the order becomes `PAYMENT_FAILED`. If the
acquirer does not answer, the payment is left `PROCESSING`, the order is **not** advanced,
and the caller gets `504` — the outcome is genuinely unknown and reconciliation will settle
it.

Response `201 Created`:
```json
{
  "id": 3,
  "paymentRef": "PAY-732500",
  "orderRef": "ORD-5008",
  "idempotencyKey": "chk-001",
  "amount": 3300.00,
  "currency": "INR",
  "instrument": "CARD_VISA",
  "status": "SUCCEEDED",
  "attempts": 1,
  "gatewayRef": "SIMGW-4471007",
  "failureReason": null,
  "createdAt": "2025-05-10T08:47:12Z",
  "updatedAt": "2025-05-10T08:47:12Z"
}
```

| Status | When |
|---|---|
| 201 | the payment reached a definite outcome (`SUCCEEDED` or `FAILED`) |
| 400 | payload invalid, or `Idempotency-Key` missing |
| 404 | no such order |
| 422 | order cancelled, merchant inactive, or the amount does not match the order |
| 504 | the acquirer did not respond; the payment is left for reconciliation |

**Retries against the acquirer.** A lost response is retried a few times before giving up.
Because the acquirer may have taken the money on an attempt whose response was lost, a
retry must never be able to capture twice for one payment — the number of captures the
acquirer records for a payment reference is at most one.

### `GET /api/payments/{paymentRef}`
`200` / `404`.

### `GET /api/payments/{paymentRef}/attempts`
The attempt history for one payment — one row per call to the acquirer, with the outcome
and the acquirer's reference. This is the audit trail a dispute is argued from, so an
attempt that reached the acquirer is expected to appear here whatever happened afterwards.

`200` / `404`.

---

## Payment operations

*Auth for this whole section:* `ROLE_PAYOPS`.

### `POST /api/payops/jobs/reconcile`
Runs reconciliation immediately instead of waiting for its cron (every two minutes).

Reconciliation exists for payments stuck in `PROCESSING` — ones where we never learned the
outcome. For each of them it **asks the acquirer what actually happened** and records that:
a capture the acquirer accepted becomes `SUCCEEDED` with its gateway reference and the order
becomes `PAID`; only a payment the acquirer has no record of becomes `FAILED`.

```json
{ "closed": 2 }
```

`200` / `403`.

### `GET /api/payops/acquirer/calls?paymentRef=`
The settlement view: every call the acquirer accepted, including captures whose response was
lost on the way back to us. Optionally filtered to one payment reference.

```json
[
  { "paymentRef": "PAY-406185", "amount": 1120.22, "instrument": "CARD_VISA",
    "outcome": "CAPTURED", "gatewayRef": "SIMGW-4471003", "receivedAt": "2025-05-10T09:02:11Z" }
]
```

`200` / `403`.

### `POST /api/payops/acquirer/reset`
Clears the simulator's record. Useful before an experiment so the call list contains only
what you just did. `200` / `403`.

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness |
| `GET /actuator/info` | none | build info |
