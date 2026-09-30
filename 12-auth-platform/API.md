# API — Aegis Identity Platform

Base URL `http://localhost:8080`. Authentication is a bearer access token:

```
Authorization: Bearer <accessToken>
```

Errors come back as:

```json
{
  "timestamp": "2026-03-04T11:20:31.114Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account not found: 999",
  "path": "/api/admin/accounts/999",
  "details": []
}
```

`details` is populated for validation failures. Rejections produced by the security filter chain
itself (`401`, `403` before a controller is reached) carry a shorter body with the same `status`,
`error`, `message` and `path` fields.

---

## Authentication — `/api/auth`

### `POST /api/auth/register`

Public. Creates an account with `ROLE_VIEWER` and signs it in immediately.

```json
{
  "email": "priya.nair@aegis.test",
  "password": "Password123!",
  "displayName": "Priya Nair"
}
```

`email` must be a valid address of at most 190 characters, `password` between 10 and 72
characters, `displayName` at most 120.

`201 Created` with a token pair. `400 Bad Request` on validation failure, `409 Conflict` if the
account already exists.

---

### `POST /api/auth/login`

Public.

```json
{ "email": "editor@aegis.test", "password": "Password123!" }
```

`200 OK` with a token pair:

```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "accessTokenExpiresAt": "2026-03-04T11:35:31Z",
  "refreshTokenExpiresAt": "2026-03-18T11:20:31Z",
  "roles": ["ROLE_EDITOR"],
  "permissions": ["document:read", "document:write"]
}
```

`401 Unauthorized` when the email or password is wrong. `403 Forbidden` when the account exists
but is not `ACTIVE`.

Five consecutive failures lock the account.

---

### `POST /api/auth/refresh`

Public — the refresh token is the credential.

```json
{ "refreshToken": "eyJhbGciOiJIUzM4NCJ9..." }
```

Rotates the session: the presented token is retired, marked with what replaced it, and a fresh
pair is returned.

`200 OK` with a token pair. `401 Unauthorized` when the token is unrecognised, expired, not a
refresh token, or does not match the stored session. `403 Forbidden` when the account is no longer
active.

---

### `POST /api/auth/logout`

Requires a valid access token. The refresh token to end goes in the body.

```json
{ "refreshToken": "eyJhbGciOiJIUzM4NCJ9..." }
```

`204 No Content`. `401 Unauthorized` when the refresh token is unrecognised.

---

### `GET /api/auth/me`

Requires a valid access token.

```json
{
  "id": 2,
  "email": "editor@aegis.test",
  "displayName": "Ananya Sharma",
  "status": "ACTIVE",
  "failedAttempts": 0,
  "roles": ["ROLE_EDITOR"],
  "permissions": ["document:read", "document:write"],
  "createdAt": "2024-07-04T09:30:00Z",
  "lastLoginAt": "2026-03-04T11:20:31Z"
}
```

`200 OK`.

---

### `GET /api/auth/sessions`

Requires a valid access token. Every refresh token ever issued to the caller, newest first.

```json
[
  {
    "id": 8,
    "tokenId": "60018cf4-6615-49ba-ac04-211bdde728a1",
    "issuedAt": "2026-03-04T11:20:31Z",
    "expiresAt": "2026-03-18T11:20:31Z",
    "revoked": false,
    "replacedBy": null,
    "userAgent": "curl/8.4.0"
  }
]
```

`200 OK`.

---

## Documents — `/api/documents`

The protected resource. Reads need a valid token. Writes need the matching permission.

| Method | Path | Required |
| ------ | ---- | -------- |
| `GET` | `/api/documents` | any valid token |
| `GET` | `/api/documents/{id}` | any valid token |
| `POST` | `/api/documents` | `ROLE_ADMIN` or the `document:write` permission |
| `PUT` | `/api/documents/{id}` | `ROLE_ADMIN` or the `document:write` permission |
| `DELETE` | `/api/documents/{id}` | `ROLE_ADMIN` or the `document:delete` permission |

### `POST /api/documents`

```json
{
  "title": "Sprint notes",
  "body": "Notes from the sprint review.",
  "sensitivity": "INTERNAL"
}
```

`sensitivity` is `INTERNAL` or `CONFIDENTIAL` and defaults to `INTERNAL`. The reference is
allocated by the service.

`201 Created`:

```json
{
  "id": 6,
  "reference": "DOC-2026-0006",
  "title": "Sprint notes",
  "body": "Notes from the sprint review.",
  "ownerId": 2,
  "ownerEmail": "editor@aegis.test",
  "sensitivity": "INTERNAL",
  "updatedAt": "2026-03-04T11:22:04Z"
}
```

`400` on validation failure or an unknown sensitivity, `403` without the permission,
`404` for an unknown document on update or delete. `DELETE` returns `204 No Content`.

---

## Administration — `/api/admin`

Reserved for administrators.

| Method | Path | Purpose |
| ------ | ---- | ------- |
| `GET` | `/api/admin/accounts` | every account with its roles and permissions |
| `GET` | `/api/admin/accounts/{id}` | one account |
| `POST` | `/api/admin/accounts/{id}/lock` | prevent sign in |
| `POST` | `/api/admin/accounts/{id}/unlock` | allow sign in and clear the failure counter |
| `POST` | `/api/admin/accounts/{id}/roles/{roleName}` | grant a role |
| `DELETE` | `/api/admin/accounts/{id}/roles/{roleName}` | revoke a role |
| `GET` | `/api/admin/audit?page=0&size=25` | the audit trail, newest first |
| `GET` | `/api/admin/denylist` | the Redis denylist keys |
| `DELETE` | `/api/admin/denylist` | clear the denylist |

Role names are the full `ROLE_` form, for example `ROLE_EDITOR`.

`200 OK` on success, `404 Not Found` for an unknown account or role.

---

## Status codes in use

| Code | When |
| ---- | ---- |
| `200` | successful read or update |
| `201` | account registered, document created |
| `204` | logout, document deleted |
| `400` | request body failed validation |
| `401` | no token, a token that is not valid, or bad credentials |
| `403` | authenticated but not permitted, or the account is not active |
| `404` | account, role or document not found |
| `409` | the account already exists |
| `500` | unhandled server error |
