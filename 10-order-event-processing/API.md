# API — Riverstone Order Event Processing

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic**, stateless.

| Status | Meaning |
|---|---|
| 400 | payload malformed or fails validation |
| 401 | missing or invalid credentials |
| 403 | authenticated but lacks the role |
| 404 | no such order or SKU |
| 422 | well formed but breaks an ordering rule |

Roles: `ROLE_ORDERS` and `ROLE_PLATFORM`.

---

## Order lifecycle

| Status | Meaning |
|---|---|
| `CREATED` | accepted and published to the pipeline; inventory has not decided yet |
| `CONFIRMED` | **inventory reserved the stock** |
| `REJECTED` | inventory could not reserve the stock |
| `CANCELLED` | withdrawn before fulfilment |

`CREATED` is a transient state. Every order leaves it within a second or two of being
placed, because the pipeline runs continuously — an order still `CREATED` minutes later
means something upstream of the status consumer did not finish.

`CONFIRMED` means a reservation exists and the stock counts moved. The three records agree:
a confirmed order has a row in `reservations`, a matching decrease in
`stock_items.available`, and a notification.

---

## Orders

### `POST /api/orders`
Accepts an order and publishes it to the pipeline.

*Auth:* `ROLE_ORDERS`.

```json
{ "customerRef": "CUST-9001", "sku": "RS-WIDGET-01", "quantity": 4 }
```

Response `202 Accepted` — the order has been recorded and published, not yet processed:

```json
{
  "id": 12,
  "orderRef": "RS-441023",
  "customerRef": "CUST-9001",
  "sku": "RS-WIDGET-01",
  "quantity": 4,
  "status": "CREATED",
  "createdAt": "2025-06-01T10:00:00Z",
  "updatedAt": "2025-06-01T10:00:00Z"
}
```

| Status | When |
|---|---|
| 202 | accepted and published |
| 400 | payload fails validation (quantity outside 1–500) |
| 404 | no such SKU |
| 422 | breaks an ordering rule |

**Every accepted order produces the same three effects**, in order, within a couple of
seconds: a reservation decision from inventory, a notification to the customer, and a final
order status. All three happen for every order — the pipeline has no sampling and no
filtering.

### `GET /api/orders/{orderRef}`
One order. `200` / `404`.

### `GET /api/orders?status=&page=&size=`
Orders in one status, newest first. `200`.

### `GET /api/orders/{orderRef}/trace`
Everything that happened to one order.

```json
{
  "order": { "orderRef": "RS-441023", "status": "CONFIRMED", "...": "..." },
  "events": [
    { "topic": "orders.created", "partition": 1, "offset": 12, "messageKey": "RS-441023",
      "eventType": "OrderCreated", "consumerGroup": "inventory-service",
      "handledBy": "InventoryConsumer", "createdAt": "2025-06-01T10:00:01Z" },
    { "topic": "inventory.events", "partition": 0, "offset": 7, "messageKey": "RS-441023",
      "eventType": "InventoryRESERVED", "consumerGroup": "notification-service",
      "handledBy": "NotificationConsumer", "createdAt": "2025-06-01T10:00:01Z" },
    { "topic": "inventory.events", "partition": 0, "offset": 7, "messageKey": "RS-441023",
      "eventType": "InventoryRESERVED", "consumerGroup": "order-status",
      "handledBy": "OrderStatusConsumer", "createdAt": "2025-06-01T10:00:01Z" }
  ],
  "reservations": ["4 x RS-WIDGET-01"],
  "notifications": ["Your order RS-441023 is confirmed"]
}
```

A healthy confirmed order has **three** event rows: the inventory consumer taking the
`OrderCreated`, and both downstream consumers independently taking the `InventoryEvent`.
Each `InventoryEvent` is handled once by each downstream service, because they are separate
services with separate concerns.

`200` / `404`.

---

## Platform

*Auth for this whole section:* `ROLE_PLATFORM`.

### `GET /api/platform/events?topic=`
Every event any consumer has taken off a topic, oldest first, with its partition, offset,
key and the consumer that handled it. Optionally filtered to one topic.

**Message keys.** Events are keyed by order reference so that everything about one order
lands on one partition and is therefore processed in order. A key of `null` in this log
means the producer left the partition choice to Kafka.

`200` / `403`.

### `GET /api/platform/stock`
Current stock levels.

```json
[ { "sku": "RS-WIDGET-01", "name": "Standard widget", "available": 4979, "reserved": 21 } ]
```

`available` plus `reserved` is constant for a SKU unless stock is added: reserving moves
units from one column to the other. The sum of `reservations.quantity` for a SKU equals its
`reserved`.

`200` / `403`.

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness, including the Kafka connection |
| `GET /actuator/info` | none | build info |
