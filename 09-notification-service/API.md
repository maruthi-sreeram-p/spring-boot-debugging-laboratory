# API — Pulsesend Notification Service

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic**, stateless.

| Status | Meaning |
|---|---|
| 400 | payload malformed or fails validation |
| 401 | missing or invalid credentials |
| 403 | authenticated but lacks the role |
| 404 | no such notification, template or recipient |
| 422 | well formed but breaks a notification rule |

Roles: `ROLE_PRODUCT` and `ROLE_MSGOPS`.

---

## Notification lifecycle

| Status | Meaning |
|---|---|
| `PENDING` | accepted and queued, not yet handled by a consumer |
| `SENT` | **a provider accepted the message for delivery** |
| `FAILED` | delivery was attempted and the provider would not take it |
| `SUPPRESSED` | not queued, because the recipient has opted out of this channel |

`SENT` is what the delivery dashboard counts and what product teams report on. It means a
provider accepted the message, so a notification the provider rejected is `FAILED`, never
`SENT`.

A notification is delivered **once**. If the same notification is handled again — a
redelivery after a failure, a manual requeue, a consumer restart — the recipient must not
receive a second copy.

---

## Sending

### `POST /api/notifications`
Requests a notification. The channel comes from the template.

*Auth:* `ROLE_PRODUCT`.

```json
{
  "recipientRef": "CUST-1001",
  "destination": "asha.menon@example.com",
  "templateCode": "order.shipped",
  "payload": "{\"name\":\"Asha\",\"orderRef\":\"ORD-5001\"}"
}
```

`payload` is a JSON object as a string; its keys fill `{{placeholders}}` in the template.

**Preferences are binding.** If the recipient has switched off the template's channel, the
notification is recorded as `SUPPRESSED` and nothing is queued. This applies to every path
that can produce a notification — a recipient who has opted out of email does not receive
email, whichever part of the platform asked for it.

Response `202 Accepted`:
```json
{
  "id": 7,
  "notificationRef": "NTF-20250612-004471",
  "recipientRef": "CUST-1001",
  "destination": "asha.menon@example.com",
  "channel": "EMAIL",
  "templateCode": "order.shipped",
  "payload": "{\"name\":\"Asha\",\"orderRef\":\"ORD-5001\"}",
  "status": "PENDING",
  "attempts": 0,
  "lastError": null,
  "createdAt": "2025-06-12T09:14:02Z",
  "updatedAt": "2025-06-12T09:14:02Z"
}
```

The response is returned as soon as the notification is queued. A consumer picks it up
within a second or so and the status moves to `SENT` or `FAILED`. **Every template routes
to its channel's consumer** — the template code is part of the routing key, but it selects
a subscriber, it never prevents delivery.

| Status | When |
|---|---|
| 202 | queued, or recorded as `SUPPRESSED` |
| 400 | payload fails validation |
| 404 | no such template |
| 422 | the template is retired |

### `GET /api/notifications/{notificationRef}`
One notification. `200` / `404`.

### `GET /api/notifications?recipientRef=&status=&page=&size=`
Notifications for one recipient, or all notifications in one status. `200`.

### `GET /api/notifications/{notificationRef}/deliveries`
The delivery log for one notification — one row per handling attempt.

```json
[
  { "id": 9, "attemptNo": 1, "outcome": "DELIVERED",
    "detail": "Accepted by the email provider", "createdAt": "2025-06-12T09:14:03Z" }
]
```

Outcomes are `DELIVERED`, `REJECTED` and `ERROR`. The log and the notification status
describe the same events, so a notification whose only log row is `REJECTED` is not `SENT`.

`200` / `404`.

---

## Preferences

### `GET /api/preferences/{recipientRef}`
The recipient's per-channel settings. A channel with no row is enabled by default.
`200`.

### `PUT /api/preferences/{recipientRef}`
```json
{ "channel": "EMAIL", "enabled": false }
```
`200` / `400`.

---

## Messaging operations

*Auth for this whole section:* `ROLE_MSGOPS`.

### `POST /api/ops/broadcast`
Queues one template for a list of recipients — how the growth team runs lifecycle
campaigns.

```json
{
  "templateCode": "welcome",
  "targets": [
    { "recipientRef": "CUST-1001", "destination": "asha.menon@example.com" },
    { "recipientRef": "CUST-1003", "destination": "meera.iyer@example.com" }
  ],
  "payload": "{\"name\":\"there\",\"brand\":\"Northwind\"}"
}
```

A broadcast is an ordinary set of notifications and is subject to the same rules as a single
send, **including the recipient's opt-out**.

`200` / `400` / `404` / `422`.

### `POST /api/ops/notifications/{notificationRef}/requeue`
Puts an existing notification back on its queue. Used after a provider outage has been
resolved.

Requeueing a notification that was already delivered must not deliver it a second time.

`200` / `404`.

### `GET /api/ops/outbox?notificationRef=`
What the simulated providers actually sent. This is the ground truth the delivery dashboard
is supposed to match. `200`.

### `POST /api/ops/outbox/reset`
Clears the outbox, so an experiment starts from a clean slate. `200`.

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness, including the RabbitMQ connection |
| `GET /actuator/info` | none | build info |
