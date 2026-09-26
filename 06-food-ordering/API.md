# API — Spicebox Food Ordering Service

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic**, stateless.

| Status | Meaning |
|---|---|
| 400 | payload malformed or fails validation, or an unknown status value |
| 401 | missing or invalid credentials |
| 403 | authenticated but lacks the role |
| 404 | no such resource, or not visible to the caller |
| 409 | the order is not in a state that allows the operation |
| 422 | well formed but breaks an ordering rule |

Roles: `ROLE_CUSTOMER` and `ROLE_OPS`.

---

## Accounts

### `POST /api/auth/register`
Creates a customer account. *Auth:* none.

```json
{
  "email": "nisha.rao@example.com",
  "password": "Password123!",
  "fullName": "Nisha Rao",
  "phone": "+91-9845099887",
  "defaultAddress": "22 Ulsoor Road, Bengaluru 560042"
}
```

`201` / `400` / `409`.

### `GET /api/auth/me`
The caller's profile. `200` / `401`.

---

## Catalogue

### `GET /api/restaurants`
Restaurants currently accepting orders. *Auth:* none. `200`.

### `GET /api/restaurants/{restaurantId}/menu`
Available menu items for one restaurant, grouped by category then name. *Auth:* none.
`200` / `404`.

---

## Orders

### `POST /api/orders`
Places an order.

*Auth:* any authenticated customer.

```json
{
  "items": [
    { "menuItemId": 1, "quantity": 2 },
    { "menuItemId": 2, "quantity": 1 }
  ],
  "deliveryAddress": "Flat 402, Palm Grove, Indiranagar, Bengaluru 560038"
}
```

`deliveryAddress` is optional; omitting it uses the account's default address.

Rules:

- **Every item must belong to the same restaurant.** An order is fulfilled by one kitchen,
  so a basket that mixes two restaurants is rejected — the customer places two orders.
- The restaurant must be active and every item must be available.
- The subtotal must reach the minimum order value (99.00).
- Delivery is 39.00, or free at or above a 499.00 subtotal.

Response `201 Created`:
```json
{
  "id": 4,
  "orderCode": "SPX-20250612-6645",
  "restaurantId": 1,
  "restaurantName": "Curry Leaf Kitchen",
  "status": "PLACED",
  "subtotal": 340.00,
  "deliveryFee": 39.00,
  "totalAmount": 379.00,
  "deliveryAddress": "Flat 402, Palm Grove, Indiranagar, Bengaluru 560038",
  "placedAt": "2025-06-12T13:12:00Z",
  "updatedAt": "2025-06-12T13:12:00Z",
  "items": [
    { "menuItemId": 1, "itemName": "Masala Dosa", "quantity": 2, "unitPrice": 140.00, "lineTotal": 280.00 }
  ]
}
```

The response is returned as soon as the order is written. Three things then happen in the
background, and all three are expected to complete within a second or two:

1. The kitchen intake step moves the order from `PLACED` to `ACCEPTED`.
2. An `order.placed` event reaches the analytics queue.
3. A confirmation is recorded in `order_notifications`, addressed to the customer who
   placed the order.

| Status | When |
|---|---|
| 201 | order placed |
| 400 | payload fails validation (no items, quantity outside 1–20) |
| 401 | not authenticated |
| 404 | a menu item does not exist |
| 422 | mixed restaurants, inactive restaurant, unavailable item, or below the minimum |

### `GET /api/orders`
The caller's own orders, newest first. `page` and `size` supported. `200` / `401`.

### `GET /api/orders/{orderId}`
One order belonging to the caller. Operations may read any order. `200` / `401` / `404`.

### `GET /api/orders/{orderId}/notifications`
The confirmations recorded for one order.

```json
[
  {
    "id": 3,
    "orderId": 4,
    "channel": "EMAIL",
    "recipient": "ayesha.khan@example.com",
    "subject": "Your Spicebox order SPX-20250612-6645 is confirmed",
    "sentAt": "2025-06-12T13:12:04Z"
  }
]
```

Every placed order has at least one confirmation, addressed to the customer who placed it.

`200` / `401` / `404`.

---

## Operations

*Auth for this whole section:* `ROLE_OPS`.

### `GET /api/ops/intake-queue`
Orders still sitting in `PLACED` — that is, orders the kitchen intake step has not picked
up. In healthy operation this is empty or near empty, because intake runs within seconds of
checkout.

`200` / `403`.

### `GET /api/ops/restaurants/{restaurantId}/queue`
Orders a restaurant is currently working on (`PLACED`, `ACCEPTED`, `PREPARING`). `200`.

### `POST /api/ops/orders/{orderId}/status`
Moves an order along the track.

```json
{ "status": "PREPARING" }
```

The track is:

```
PLACED → ACCEPTED → PREPARING → READY_FOR_PICKUP → OUT_FOR_DELIVERY → DELIVERED
```

**Only a forward step to the next status is allowed.** An order cannot skip a stage, and it
cannot move backwards — a delivered order is finished. `CANCELLED` and `REJECTED` are
permitted from any stage before `OUT_FOR_DELIVERY` and are terminal.

Moving an order to `READY_FOR_PICKUP` publishes it to the dispatch queue. The dispatch
service picks it up and moves it to `OUT_FOR_DELIVERY` on its own, so operations never set
that status by hand.

| Status | When |
|---|---|
| 200 | status changed |
| 400 | the value is not a known status |
| 403 | authenticated but not operations |
| 404 | no such order |
| 409 | the requested move is not legal from the current status |

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness, including the RabbitMQ connection |
| `GET /actuator/info` | none | build info |
