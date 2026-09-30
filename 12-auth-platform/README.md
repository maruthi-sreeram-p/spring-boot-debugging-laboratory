# 12 — Aegis Identity Platform

The identity service behind a company's internal tools. It registers accounts, signs people in,
issues JSON Web Tokens, rotates refresh tokens, expands roles into fine-grained permissions, and
protects a small document API that exists so there is something real to be denied access to.

**Difficulty:** 8 / 10 · **Theme:** filter chain ordering, authority naming, claim validation,
token expiry, denylist correctness, matcher precedence

---

## Stack

| Concern      | Choice |
| ------------ | ------ |
| Runtime      | Java 21, Spring Boot 3.3.5 |
| Persistence  | Spring Data JPA / Hibernate 6 on PostgreSQL 16 |
| Tokens       | JJWT 0.12.6, HS384, stateless resource protection |
| Token state  | Redis 7 — the denylist of tokens that must no longer be accepted |
| Security     | Spring Security 6, BCrypt, `@EnableMethodSecurity` |
| Build / test | Maven, JUnit 5, AssertJ |

---

## How authorization is meant to work

1. `POST /api/auth/login` verifies the password and issues **two** tokens.
   - an **access token**, short lived, carrying `roles` and `permissions` claims
   - a **refresh token**, long lived, carrying nothing but an identity, recorded in
     `refresh_tokens` so a session can be listed and revoked
2. `JwtAuthenticationFilter` turns a bearer access token into an `Authentication`. Roles and
   permissions from the token become granted authorities.
3. URL rules in `SecurityConfig` decide who may reach which path.
4. `@PreAuthorize` on the service layer decides who may perform which operation.
5. `POST /api/auth/refresh` rotates the refresh token: the presented one is retired and a new
   pair is issued.
6. `POST /api/auth/logout` ends a session.

Roles carry permissions:

| Role | Permissions |
| ---- | ----------- |
| `ROLE_ADMIN` | `document:read`, `document:write`, `document:delete`, `audit:read`, `account:manage` |
| `ROLE_EDITOR` | `document:read`, `document:write` |
| `ROLE_VIEWER` | `document:read` |
| `ROLE_AUDITOR` | `document:read`, `audit:read` |

---

## Running it

```bash
docker compose -f ../infra/docker-compose.yml up -d lab-postgres lab-redis
```

Then, from this directory:

```bash
mvn -B -DskipTests package && java -jar target/identity-platform-12.4.7.jar --spring.profiles.active=local
```

The `local` profile points at `localhost:5432` (PostgreSQL, database `authdb`) and
`localhost:6380` (Redis). `application.yml` carries placeholders — `YOUR_DATABASE_HOST`,
`YOUR_DATABASE_USER`, `YOUR_DATABASE_PASSWORD`, `YOUR_REDIS_HOST`,
`YOUR_JWT_SIGNING_SECRET_AT_LEAST_32_CHARACTERS` — so set those, or the matching environment
variables, if you run it anywhere else.

`schema.sql` and `data.sql` are applied on every boot and are idempotent.

### Seeded accounts

| Email | Password | Role | Status |
| ----- | -------- | ---- | ------ |
| `admin@aegis.test` | `Admin123!` | `ROLE_ADMIN` | ACTIVE |
| `editor@aegis.test` | `Password123!` | `ROLE_EDITOR` | ACTIVE |
| `viewer@aegis.test` | `Password123!` | `ROLE_VIEWER` | ACTIVE |
| `auditor@aegis.test` | `Password123!` | `ROLE_AUDITOR` | ACTIVE |
| `Dev.Ops@aegis.test` | `Password123!` | `ROLE_EDITOR` | ACTIVE |
| `former@aegis.test` | `Password123!` | `ROLE_VIEWER` | LOCKED |

---

## Inspecting state

**The token itself.** A JWT is three base64url segments. You do not need a website to read one:

```bash
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"editor@aegis.test","password":"Password123!"}'
```

```bash
echo "<token>" | cut -d. -f2 | base64 -d 2>/dev/null
```

The payload tells you the subject, the `jti`, the expiry, and exactly which roles and permissions
the bearer is claiming. When an authorization decision surprises you, the token is the first place
to look — and the second place is the authorities the filter actually built from it.

**The sessions.** `GET /api/auth/sessions` lists your refresh tokens with their issue time,
expiry, revocation flag and what replaced them. The same rows are in `refresh_tokens`:

```bash
docker exec lab-postgres psql -U labuser -d authdb -c "SELECT token_id, issued_at, expires_at, revoked, replaced_by FROM refresh_tokens ORDER BY id DESC LIMIT 5;"
```

**The denylist.**

```bash
docker exec lab-redis redis-cli KEYS 'aegis:denylist:*'
```

or `GET /api/admin/denylist`, with `DELETE /api/admin/denylist` to clear it.

**The audit trail.** `GET /api/admin/audit`, or the `audit_events` table. Every sign-in,
lockout, rotation and document change is recorded there.

**The filter chain.** Setting `logging.level.org.springframework.security: DEBUG` makes Spring
print the filter chain at startup and log each authorization decision. It is verbose, and it is
worth it exactly once per investigation.

---

## Where to start

Read [`API.md`](API.md), then [`DEBUGGING_GUIDE.md`](DEBUGGING_GUIDE.md).

`SOLUTION.md` is the answer key. Leave it shut.

---

## Tests

```bash
mvn -B test
```

They pass.
