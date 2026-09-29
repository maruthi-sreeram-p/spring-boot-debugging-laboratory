# API — Lumen Retail Product Catalogue

Base URL `http://localhost:8080`. Authentication is HTTP Basic.

| Prefix | Access |
| ------ | ------ |
| `GET /api/catalog/**` | public |
| `/api/merch/**` | `ROLE_MERCH` |
| `/api/ops/**` | `ROLE_MERCH` |
| `/actuator/health`, `/actuator/info` | public |

Errors come back as:

```json
{
  "timestamp": "2026-03-04T11:20:31.114Z",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found: 999",
  "path": "/api/catalog/products/999"
}
```

---

## Storefront

### `GET /api/catalog/categories`

Active categories, ordered by their display position.

```json
[
  { "id": 1, "slug": "laptops", "name": "Laptops", "position": 1 },
  { "id": 2, "slug": "audio", "name": "Audio", "position": 2 }
]
```

`200 OK`.

---

### `GET /api/catalog/products/{productId}`

One active product.

```json
{
  "id": 1,
  "sku": "LM-LAP-1401",
  "name": "Meridian 14 Ultrabook",
  "description": "14 inch magnesium chassis, 16 GB RAM, 512 GB NVMe",
  "brand": "Meridian",
  "categoryId": 1,
  "categoryName": "Laptops",
  "price": 124900.00,
  "currency": "INR",
  "active": true,
  "updatedAt": "2024-11-02T09:00:00Z"
}
```

`200 OK`, or `404 Not Found` when the id is unknown or the product has been deactivated.

---

### `GET /api/catalog/products/sku/{sku}`

The same payload, addressed by SKU. This is what the storefront deep links and the print
catalogue QR codes use.

`200 OK`, or `404 Not Found`.

---

### `GET /api/catalog/products/{productId}/variants`

The variant picker on a product page. `effectivePrice` is the product price plus the variant's
delta.

```json
[
  {
    "id": 1,
    "variantSku": "LM-LAP-1401-SLV",
    "label": "Silver",
    "priceDelta": 0.00,
    "effectivePrice": 124900.00
  },
  {
    "id": 2,
    "variantSku": "LM-LAP-1401-GRP",
    "label": "Graphite",
    "priceDelta": 2000.00,
    "effectivePrice": 126900.00
  }
]
```

`200 OK`, or `404 Not Found` if the product does not exist or is inactive.

---

### `GET /api/catalog/categories/{categoryId}/products`

Every active product in a category, ordered by name. Backs the category pages.

`200 OK`, or `404 Not Found` when the category does not exist.

---

### `GET /api/catalog/search`

| Parameter | Type | Default |
| --------- | ---- | ------- |
| `term` | string, matched against the product name, case-insensitive | — |
| `brand` | string, exact match, case-insensitive | — |
| `categoryId` | long | — |
| `minPrice` | decimal | — |
| `maxPrice` | decimal | — |
| `page` | int | `0` |
| `size` | int, capped at 100 | `20` |

All filters are optional and combine with AND. Inactive products are never returned.

```json
{
  "content": [ { "id": 9, "sku": "LM-ACC-3101", "...": "..." } ],
  "totalElements": 4,
  "totalPages": 1,
  "number": 0,
  "size": 20,
  "first": true,
  "last": true
}
```

`200 OK`.

---

### `GET /api/catalog/browse`

The home page rails. A rail is a saved filter — a brand, a category, or both.

| Parameter | Type |
| --------- | ---- |
| `brand` | string |
| `categoryId` | long |

Returns a plain array of products, ordered by name. `200 OK`.

---

## Merchandising — `ROLE_MERCH`

### `POST /api/merch/products`

```json
{
  "sku": "LM-ACC-3199",
  "name": "Braided USB-C Cable 2m",
  "description": "240 W rated",
  "brand": "Lumen",
  "categoryId": 4,
  "price": 1490.00
}
```

`sku`, `name`, `brand` and `categoryId` are required; `price` must be positive. Products are
created active.

`201 Created` with the product body. `400 Bad Request` on a validation failure,
`409 Conflict` when the SKU already exists, `404 Not Found` when the category does not.

---

### `PUT /api/merch/products/{productId}`

```json
{
  "name": "Meridian 14 Ultrabook (2026 refresh)",
  "description": "Refreshed chassis and panel",
  "brand": "Meridian",
  "categoryId": 1,
  "price": 119900.00,
  "active": true
}
```

Replaces the editable fields. A price change writes a row to `price_history` attributed to the
authenticated user.

`200 OK` with the updated product. `400`, `404` as above.

---

### `GET /api/merch/products/{productId}/price-history`

Newest first.

```json
[
  {
    "id": 1,
    "oldPrice": 29900.00,
    "newPrice": 27900.00,
    "changedBy": "merch@lumen.test",
    "changedAt": "2025-03-14T10:02:00Z"
  }
]
```

`200 OK`, or `404 Not Found`.

---

## Cache operations — `ROLE_MERCH`

### `GET /api/ops/cache/keys?pattern=catalog:*`

Every matching Redis key, sorted. `200 OK`.

### `GET /api/ops/cache/summary`

Key count per cache name.

```json
{ "catalog:products": 3, "catalog:categoryListing": 1 }
```

`200 OK`.

### `DELETE /api/ops/cache`

Drops every key. `200 OK` with `{ "removed": 12 }`.

---

## Status codes in use

| Code | When |
| ---- | ---- |
| `200` | successful read or update |
| `201` | product created |
| `400` | request body failed validation |
| `401` | no credentials on a protected route |
| `403` | authenticated, but not `ROLE_MERCH` |
| `404` | product, category or variant not found |
| `409` | duplicate SKU |
| `500` | unhandled server error |
