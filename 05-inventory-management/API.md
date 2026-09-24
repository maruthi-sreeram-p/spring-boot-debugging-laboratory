# API — Vantage Supply Inventory Service

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic**, stateless. Every endpoint requires authentication.

| Status | Meaning |
|---|---|
| 400 | payload or parameter malformed or fails validation |
| 401 | missing or invalid credentials |
| 403 | authenticated but lacks the role |
| 404 | no such product, warehouse, stock level or purchase order |
| 409 | the operation would leave stock in an impossible state |
| 422 | well formed but breaks a receiving rule |

Roles: `ROLE_WAREHOUSE` (floor staff — read stock, post goods receipts) and
`ROLE_CONTROLLER` (inventory control — everything, including manual adjustments).

---

## Stock

Every stock figure in this API carries three quantities:

| Field | Meaning |
|---|---|
| `quantityOnHand` | physically in the building |
| `quantityReserved` | on hand but already committed to open sales orders |
| `quantityAvailable` | what may still be promised to a new order — **on hand minus reserved** |

`quantityAvailable` is what the storefront and the order-capture system use to decide
whether a customer can buy. It is never larger than `quantityOnHand`.

### `GET /api/stock/products/{productId}`
Network-wide view of one product: a row per warehouse plus the totals.

*Auth:* any authenticated user.

```json
{
  "productId": 1,
  "sku": "VS-CBL-0101",
  "productName": "USB-C to USB-C cable 2m",
  "totalOnHand": 2490,
  "totalAvailable": 2270,
  "warehouses": [
    {
      "productId": 1, "sku": "VS-CBL-0101", "productName": "USB-C to USB-C cable 2m",
      "warehouseId": 1, "warehouseCode": "WH-BLR",
      "quantityOnHand": 1420, "quantityReserved": 180, "quantityAvailable": 1240,
      "belowReorderLevel": false, "updatedAt": "2025-01-14T08:00:00Z"
    }
  ]
}
```

`200` / `404`.

### `GET /api/stock/products/{productId}/warehouses/{warehouseId}`
One product in one warehouse. This is the hot path and is served from Redis when an entry
exists.

The cached figure is a copy of the database figure, so this endpoint and
`GET /api/stock/products/{productId}` always report the same numbers for the same warehouse.
A cache entry only ever reflects committed data.

`200` / `404`.

### `GET /api/stock/warehouses/{warehouseId}`
Every stock level in one warehouse. `200` / `404`.

### `GET /api/stock/movements?productId=&warehouseId=&page=&size=`
The movement ledger for a product, newest first. `warehouseId` is optional; omit it for
every warehouse.

Movements are signed: receipts and positive adjustments are positive, issues and negative
adjustments are negative. **Every change to `quantity_on_hand` has a corresponding movement
row**, so the sum of a product's movements in a warehouse always equals its current
`quantityOnHand`. Month-end reconciliation depends on it.

`200` / `400`.

### `POST /api/stock/adjustments`
Manual stock correction — cycle counts, damage, goods found or lost.

*Auth:* **`ROLE_CONTROLLER` only.**

```json
{
  "productId": 3,
  "warehouseId": 1,
  "quantityDelta": -4,
  "reason": "Damaged in handling"
}
```

`quantityDelta` may be positive or negative but must not take the level below zero. The
adjustment updates the stock level, writes an `ADJUSTMENT` movement carrying the same signed
quantity and the reason, and invalidates any cached figure.

Response `200 OK`: the updated stock level.

| Status | When |
|---|---|
| 200 | adjusted |
| 400 | payload fails validation |
| 403 | authenticated but not inventory control |
| 404 | no stock level for that product and warehouse |
| 409 | the adjustment would take the level below zero |

---

## Purchase orders and receiving

### `GET /api/purchase-orders`
Every purchase order still `OPEN`, by expected date. `200`.

### `GET /api/purchase-orders/{purchaseOrderId}`
One purchase order with its lines.

```json
{
  "id": 1,
  "poNumber": "PO-2025-0021",
  "supplierName": "Acton Components Ltd",
  "warehouseId": 1,
  "warehouseCode": "WH-BLR",
  "status": "OPEN",
  "expectedDate": "2025-06-18",
  "lines": [
    { "lineId": 1, "productId": 1, "sku": "VS-CBL-0101", "quantityOrdered": 1000, "quantityReceived": 0, "unitCost": 2.40 }
  ]
}
```

`200` / `404`.

### `POST /api/purchase-orders/{purchaseOrderId}/receipts`
Posts a delivery against a purchase order. Deliveries arrive in parts, so an order may be
received several times.

*Auth:* `ROLE_WAREHOUSE` or `ROLE_CONTROLLER`.

```json
{
  "lines": [
    { "productId": 1, "quantity": 400 },
    { "productId": 5, "quantity": 150 }
  ],
  "note": "Pallet 1 of 3"
}
```

Rules:

- Every product must be on the order.
- **The running total received for a line may never exceed the quantity ordered.** Two
  receipts of 600 against a line ordered for 1000 is an over-receipt and must be rejected,
  exactly as a single receipt of 1200 would be.
- An order that has already reached `RECEIVED` is closed; further deliveries against it are
  rejected.
- The whole receipt is one unit of work. If any line is rejected, **nothing** changes — no
  stock, no movements, no line quantities, and no cached figure.

Each accepted line raises `quantity_on_hand`, writes a `RECEIPT` movement and refreshes the
cached figure for that product and warehouse. The order then becomes `PARTIALLY_RECEIVED`
or `RECEIVED`.

Response `200 OK`: the updated purchase order.

| Status | When |
|---|---|
| 200 | receipt posted |
| 400 | payload fails validation (empty lines, quantity below 1) |
| 404 | no such purchase order |
| 422 | product not on the order, over-receipt, or the order is already closed |

---

## Reference data

| Endpoint | Returns |
|---|---|
| `GET /api/products` | active products |
| `GET /api/warehouses` | active warehouses |
| `GET /api/suppliers` | all suppliers |

All `200`, any authenticated user.

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness |
| `GET /actuator/info` | none | build info |
