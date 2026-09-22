# API — Northwind Shop Order Service

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic** (email as username). The service is stateless — send the
`Authorization` header on every request.

Error responses share one shape:

```json
{
  "timestamp": "2025-03-04T09:12:44.201Z",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found: 941",
  "path": "/api/products/941",
  "details": ["quantity: must be greater than or equal to 1"]
}
```

`details` is present only for validation failures.

---

## Authentication

### `POST /api/auth/register`
Creates a customer account with the `ROLE_CUSTOMER` role.

*Auth:* none.

Request:
```json
{
  "email": "nadia.khan@example.com",
  "password": "Password123!",
  "fullName": "Nadia Khan",
  "phone": "+91-98111-22334"
}
```

Response `201 Created`:
```json
{
  "id": 5,
  "email": "nadia.khan@example.com",
  "fullName": "Nadia Khan",
  "phone": "+91-98111-22334",
  "status": "ACTIVE",
  "roles": ["ROLE_CUSTOMER"]
}
```

| Status | When |
|---|---|
| 201 | account created |
| 400 | payload fails validation |
| 409 | the email is already registered |

---

### `POST /api/auth/login`
Verifies credentials and returns the profile. There is no token — subsequent calls use
Basic auth with the same credentials.

*Auth:* none.

Request:
```json
{ "email": "priya.sharma@example.com", "password": "Password123!" }
```

Response `200 OK`: same body as register.

| Status | When |
|---|---|
| 200 | credentials accepted |
| 401 | bad credentials, or the account is not `ACTIVE` |

---

### `GET /api/auth/me`
Returns the profile of the caller.

*Auth:* any authenticated customer.

| Status | When |
|---|---|
| 200 | profile returned |
| 401 | missing or invalid credentials |

---

## Catalogue

### `GET /api/products`
Lists active products.

*Auth:* none (public catalogue).

| Query parameter | Type | Default | Meaning |
|---|---|---|---|
| `categoryId` | long | – | restrict to one category |
| `q` | string | – | case-insensitive substring match on the product name |
| `page` | int | `0` | zero-based page index |
| `size` | int | `20` | page size, capped at 100 |
| `sort` | string | `name` | entity property to sort ascending by |

Response `200 OK`:
```json
{
  "content": [
    {
      "id": 4,
      "sku": "NW-AUD-2201",
      "name": "Auralis Over-Ear ANC",
      "description": "Active noise cancelling headphones, 38 hour battery",
      "price": 279.00,
      "stockQuantity": 52,
      "categoryName": "Audio",
      "active": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 12,
  "totalPages": 1,
  "last": true
}
```

`totalElements` is the number of products matching the filter across all pages;
`totalPages` is derived from it and `size`. The storefront paginator relies on both.

---

### `GET /api/products/{productId}`
Returns one active product.

*Auth:* none.

| Status | When |
|---|---|
| 200 | product returned |
| 404 | no such product, or the product is inactive |

Product detail reads are cached in Redis for 30 minutes under the `products` cache. A
back-office update to a product is expected to be visible on the very next read.

---

### `PUT /api/admin/products/{productId}`
Back-office product edit.

*Auth:* `ROLE_ADMIN`.

Request:
```json
{
  "sku": "NW-AUD-2201",
  "name": "Auralis Over-Ear ANC",
  "description": "Active noise cancelling headphones, 38 hour battery",
  "price": 249.00,
  "stockQuantity": 52,
  "active": true
}
```

Response `200 OK`: the updated product.

| Status | When |
|---|---|
| 200 | product updated |
| 400 | payload fails validation |
| 401 | not authenticated |
| 403 | authenticated but not an administrator |
| 404 | no such product |

---

## Cart

The cart belongs to the authenticated customer; there is no cart id in the URL. Every cart
endpoint returns the whole cart after applying the change.

Cart body:
```json
{
  "lines": [
    {
      "productId": 4,
      "sku": "NW-AUD-2201",
      "name": "Auralis Over-Ear ANC",
      "unitPrice": 279.00,
      "quantity": 2,
      "lineTotal": 558.00,
      "available": true
    }
  ],
  "distinctItems": 1,
  "subtotal": 558.00
}
```

`available` is `false` when the requested quantity exceeds current stock.

### `GET /api/cart`
*Auth:* authenticated. `200`.

### `POST /api/cart/items`
Adds to the quantity already in the cart for that product.

*Auth:* authenticated.

```json
{ "productId": 4, "quantity": 2 }
```

`quantity` must be between 1 and 20.

| Status | When |
|---|---|
| 200 | cart returned |
| 400 | payload fails validation |
| 404 | no such product, or the product is inactive |

### `PUT /api/cart/items/{productId}`
Replaces the quantity for one product.

```json
{ "quantity": 3 }
```

`quantity` must be between 1 and 20. Statuses as above.

### `DELETE /api/cart/items/{productId}`
Removes one line. `200` with the remaining cart.

### `DELETE /api/cart`
Empties the cart. `204 No Content`.

---

## Orders

### `POST /api/orders`
Converts the current cart into an order, reserves the stock, and empties the cart.

*Auth:* authenticated.

Request:
```json
{ "shippingAddress": "12 Carter Road, Bandra West, Mumbai 400050, IN" }
```

Response `201 Created`:
```json
{
  "id": 7,
  "orderNumber": "ORD-20250304-418823",
  "status": "PENDING",
  "subtotal": 558.00,
  "shippingFee": 0.00,
  "totalAmount": 558.00,
  "shippingAddress": "12 Carter Road, Bandra West, Mumbai 400050, IN",
  "createdAt": "2025-03-04T09:12:44.201Z",
  "items": [
    {
      "productId": 4,
      "productName": "Auralis Over-Ear ANC",
      "quantity": 2,
      "unitPrice": 279.00,
      "lineTotal": 558.00
    }
  ]
}
```

Shipping is 49.00 below a 500.00 subtotal and free at or above it.

| Status | When |
|---|---|
| 201 | order created, stock reserved, cart emptied |
| 400 | the cart is empty, or the payload fails validation |
| 401 | not authenticated |
| 409 | a line requests more units than are in stock |

### `GET /api/orders`
Order history of the caller, newest first.

*Auth:* authenticated.

| Query parameter | Type | Default |
|---|---|---|
| `page` | int | `0` |
| `size` | int | `10` |

Response `200 OK`: a paged envelope of order objects, same shape as `/api/products`.

### `GET /api/orders/{orderId}`
One order belonging to the caller.

*Auth:* authenticated.

| Status | When |
|---|---|
| 200 | order returned |
| 401 | not authenticated |
| 404 | the caller has no order with that id |

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness |
| `GET /actuator/info` | none | build info |
| `GET /actuator/caches` | authenticated | which caches exist and which manager owns them |
