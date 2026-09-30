# Solution — Aegis Identity Platform

Six defects. Do not read this until you have finished with the project.

---

## A1 — permissions are turned into authorities with the role prefix

**Report:** R1.

**Symptom.** An editor holding a token that plainly contains `document:write` gets `403` on
`POST /api/documents`. An administrator succeeds. Reads work for everyone.

```
editor POST /api/documents  403
admin  POST /api/documents  201
editor GET  /api/documents  200
```

The token is not in doubt:

```json
{ "sub": "2", "typ": "access", "email": "editor@aegis.test",
  "roles": ["EDITOR"], "permissions": ["document:read", "document:write"] }
```

**Root cause.** `JwtAuthenticationFilter` builds the granted authorities from both claims in one
pass:

```java
List<GrantedAuthority> authorities = Stream.concat(roles.stream(), permissions.stream())
        .map(name -> "ROLE_" + name)
        .map(SimpleGrantedAuthority::new)
        ...
```

Roles come out of the token stripped of their prefix (`AuthService#issue` removes `ROLE_` before
putting them in the claim), so prefixing them back on is correct. Permissions are not roles, and
prefixing them produces `ROLE_document:write`.

`DocumentService#create` is guarded by

```java
@PreAuthorize("hasRole('ADMIN') or hasAuthority('document:write')")
```

`hasRole('ADMIN')` expands to a test for the authority `ROLE_ADMIN`, which the administrator has,
so the first branch passes and the administrator is let through. `hasAuthority('document:write')`
tests for that exact string, and nobody has it — every holder of the permission has
`ROLE_document:write` instead. The permission system is inert.

**Location.**
[`JwtAuthenticationFilter.java:78-83`](src/main/java/com/aegis/identity/security/JwtAuthenticationFilter.java#L78),
against the `@PreAuthorize` expressions in
[`DocumentService.java`](src/main/java/com/aegis/identity/service/DocumentService.java).

**Why it happens in real systems.** Spring Security's `ROLE_` prefix is a convention enforced in
two places that never talk to each other: `hasRole(x)` silently prepends it, and
`SimpleGrantedAuthority` silently does not. A developer who has internalised "authorities need the
`ROLE_` prefix" — which is true for roles — applies it to everything in the list, and the code
reads as a tidy one-line normalisation. Nothing fails at compile time, nothing fails at startup,
and the only visible consequence is a `403` that looks like a data problem.

It survives testing because whoever is testing is an administrator. The `hasRole('ADMIN') or ...`
shape is extremely common — it is how you give admins a blanket override — and it masks the defect
completely for the one person most likely to notice it. This is the purest form of "it works on my
machine": it works on their *account*.

**The correct fix.** Roles and permissions are different namespaces and must be mapped
differently:

```java
List<GrantedAuthority> authorities = Stream.concat(
                roles.stream().map(name -> "ROLE_" + name),
                permissions.stream())
        .map(SimpleGrantedAuthority::new)
        .map(GrantedAuthority.class::cast)
        .toList();
```

Then `hasRole('ADMIN')` and `hasAuthority('document:write')` both work, and — importantly —
`hasAuthority('ROLE_ADMIN')` also works, because `hasRole` is nothing more than sugar for it.

If you would rather remove the ambiguity entirely, stop stripping the prefix when the token is
issued and stop adding it when the token is read: put `ROLE_ADMIN` and `document:write` in the
claims exactly as they appear in the database, and map both with `SimpleGrantedAuthority::new` and
no rewriting at all. Fewer transformations means fewer places for a convention to be applied to
the wrong half of a list.

**Underlying concept.** `hasRole('X')` is `hasAuthority('ROLE_' + X)`. That prefix is the only
difference between the two, and Spring will not tell you when you have added it twice, added it to
something that is not a role, or forgotten it. Authority strings are compared exactly.

**Detecting it faster.** Log or breakpoint the `Authentication.getAuthorities()` at the moment of
the decision. One look at `[ROLE_EDITOR, ROLE_document:read, ROLE_document:write]` ends the
investigation. `org.springframework.security` at `DEBUG` prints the same thing.

**Prevention.** Never build authorities for two namespaces in one `map`. A test that asserts the
exact authority set produced from a token — not that a request succeeds, but the literal strings —
costs four lines and catches the entire class.

---

## A2 — a broad matcher above a narrow one

**Report:** R2.

**Symptom.** Any authenticated account can read the administrative routes. Writing to them is
correctly refused.

```
viewer GET  /api/admin/accounts  200
viewer GET  /api/admin/audit     200
viewer POST /api/admin/.../lock  403
anon   GET  /api/admin/accounts  401
```

**Root cause.** In `SecurityConfig`:

```java
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
.requestMatchers(HttpMethod.GET, "/api/**").authenticated()
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

`authorizeHttpRequests` evaluates matchers in declaration order and stops at the first match. Any
`GET` under `/api` matches line three, so line four is never reached for a `GET`. Non-`GET` methods
fall through to it and are protected correctly, which is exactly why the leak looks arbitrary: the
prefix is the same, the outcome depends on the verb.

**Location.** [`SecurityConfig.java:38-46`](src/main/java/com/aegis/identity/config/SecurityConfig.java#L38).

**Why it happens in real systems.** The `GET /api/**` line is a hardening change: "make sure no
read endpoint is ever anonymous". It is a sensible instinct and it was pasted at the top of the
list, because that is where a catch-all feels like it belongs. Nothing warns you. Spring Security
will refuse to start if you put a matcher *after* `anyRequest()`, which trains people to believe
the framework validates ordering — it does not, it validates only that one case. Every other
shadowed rule is silently dead.

It also survives review because the diff is one line and the line is obviously an improvement in
isolation. You have to hold the whole list in your head to see what it broke.

**The correct fix.** Order matchers from most specific to least specific:

```java
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/documents/**").authenticated()
.requestMatchers("/api/auth/**").authenticated()
.anyRequest().authenticated()
```

The `GET /api/**` line then adds nothing that `anyRequest().authenticated()` does not already
guarantee, so delete it rather than moving it. A catch-all that duplicates the default is a
liability, not a safety net.

Defence in depth is the better answer, though. URL rules are positional and fragile; method
security is attached to the thing being protected and cannot be shadowed by an unrelated edit. Put
`@PreAuthorize("hasRole('ADMIN')")` on the administrative service methods as well, and the
ordering mistake becomes a non-event.

**Underlying concept.** `authorizeHttpRequests` is an ordered chain of `RequestMatcher`s, first
match wins, and a match is final — it does not "and" with later rules. A rule that can never be
reached is not an error, it is just dead.

**Detecting it faster.** `logging.level.org.springframework.security: DEBUG` names the matcher that
decided each request. Failing that, read the list top to bottom and, for each rule, ask what is
left for the rules below it.

**Prevention.** Write the matcher list most-specific first, as a rule, and review it as a whole
whenever a line is added. An integration test per role that asserts both an allowed and a denied
route — including the verb — is the cheapest possible guard.

---

## A3 — days configured, hours applied

**Report:** R3.

**Symptom.** `aegis.jwt.refresh-token-days: 14`, and sessions last fourteen hours.

```
 issued 2026-09-24T06:27:32Z  expires 2026-09-24T20:27:32Z  -> window 13:59:59.99
```

```
      token_id       |        issued_at        |       expires_at        |     window
---------------------+-------------------------+-------------------------+-----------------
 e3628b03-5dfd-...   | 2026-09-24 06:27:34+00  | 2026-09-24 20:27:34+00  | 13:59:59.998227
```

**Root cause.** `JwtService#issueRefreshToken`:

```java
Instant expiresAt = now.plus(Duration.ofHours(properties.getJwt().getRefreshTokenDays()));
```

`Duration.ofHours` applied to a value that means days. Fourteen days becomes fourteen hours — 1/24
of the intended session, which is why it correlates with time of day rather than with a day
boundary, and why people on irregular hours notice it first.

The access token, two methods above, is correct: `Duration.ofMinutes(getAccessTokenMinutes())`.

**Location.** [`JwtService.java:74`](src/main/java/com/aegis/identity/security/JwtService.java#L74).

**Why it happens in real systems.** The property almost certainly used to be `refresh-token-hours`.
Someone renamed it when the session length was extended and updated the YAML, the documentation
and the property name — everything except the one call site, where `ofHours` still compiles
perfectly against an `int`. Type systems do not carry units, so nothing complains.

The symptom is delayed by fourteen hours from the change that caused it, so it never appears in
the same session as the deploy, and it presents as an intermittent user complaint rather than a
failure.

**The correct fix.**

```java
Instant expiresAt = now.plus(Duration.ofDays(properties.getJwt().getRefreshTokenDays()));
```

Better, stop passing bare numbers around. Spring Boot binds `14d` and `15m` straight into a
`Duration`:

```java
private Duration accessTokenTtl = Duration.ofMinutes(15);
private Duration refreshTokenTtl = Duration.ofDays(14);
```

```yaml
aegis:
  jwt:
    access-token-ttl: 15m
    refresh-token-ttl: 14d
```

Now the unit lives with the value and the call site cannot choose a different one.

**Underlying concept.** A number is not a quantity. The moment a unit lives in a variable *name*
rather than in a *type*, it is only a convention, and conventions do not survive refactoring. This
is the same defect as a `timeoutMillis` field being passed to a method expecting seconds.

**Detecting it faster.** Subtract two columns. `SELECT expires_at - issued_at FROM refresh_tokens`
answers "how long is a session, really" in one query, and it answers it about the running system
rather than about the configuration.

**Prevention.** Bind durations as `Duration`, never as `int`. Where that is not possible, assert
the window in a test: issue a token and check the expiry is within a few seconds of the intended
one.

---

## A4 — logout denylists the refresh token and the filter checks the access token

**Report:** R4.

**Symptom.** After `POST /api/auth/logout` returns `204`, the access token from that session keeps
working until it expires on its own.

```
before logout   GET /api/auth/me    200
logout                              204
after  logout   GET /api/auth/me    200
after  logout   GET /api/documents  200
```

The denylist is not empty, which is what made this confusing:

```
redis        : aegis:denylist:60018cf4-6615-49ba-ac04-211bdde728a1
access  jti  : 1fbad78e-06af-4c30-8f75-9a3227cd8b52
refresh jti  : 60018cf4-6615-49ba-ac04-211bdde728a1
```

The key that is there is the refresh token's `jti`. The filter looks up the access token's `jti`.
They never coincide.

**Root cause.** `AuthService#logout` receives the refresh token in the body, parses it, and
denylists what it has:

```java
Claims claims = parseRefreshToken(request.getRefreshToken());
...
denylist.add(claims.getId(), claims.getExpiration().toInstant());
```

`JwtAuthenticationFilter` checks `denylist.contains(claims.getId())` where `claims` came from the
access token. The denylist is therefore write-only: nothing is ever written that anything ever
reads.

Note that the refresh token *is* genuinely revoked in the database, so the denylist entry is
redundant as well as wrong. The whole mechanism does nothing.

**Location.** [`AuthService.java`](src/main/java/com/aegis/identity/service/AuthService.java),
the `logout` method, against
[`JwtAuthenticationFilter.java:66`](src/main/java/com/aegis/identity/security/JwtAuthenticationFilter.java#L66).

**Why it happens in real systems.** The logout endpoint takes a refresh token because that is what
the OAuth revocation convention does, and it is the right choice. The developer then had exactly
one set of claims in scope and denylisted it. The code is self-consistent, the endpoint returns
`204`, Redis visibly gains a key, and a manual test that checks "did logout work" by calling login
again passes.

Verifying it properly requires deliberately *re-using a credential you were told to discard* —
which is an attacker's instinct, not a developer's. That is why it took a security review to find.

**The correct fix.** Denylist the token that is actually presented on subsequent requests. Logout
is authenticated, so the access token's `jti` is already in the security context:

```java
@Transactional
public void logout(RefreshRequest request, AegisPrincipal principal, Instant accessTokenExpiry) {
    Claims claims = parseRefreshToken(request.getRefreshToken());

    RefreshToken stored = refreshTokenRepository.findByTokenId(claims.getId())
            .orElseThrow(() -> new TokenRejectedException("Refresh token is not recognised"));
    if (!stored.getAccountId().equals(principal.accountId())) {
        throw new TokenRejectedException("Refresh token belongs to another account");
    }

    stored.setRevoked(true);
    denylist.add(principal.accessTokenId(), accessTokenExpiry);
    ...
}
```

`AegisPrincipal` already carries `accessTokenId` for exactly this. Carry the access token's
expiry through as well (add it to the principal) so the denylist entry expires with the token and
the list stays bounded.

While you are here: the ownership check above is missing from the current code. Any authenticated
user can log out anybody else's session by presenting their refresh token. Add it.

**Underlying concept.** A denylist only works if the identifier written is the identifier read.
Two token types means two `jti` namespaces, and a revocation mechanism has to name which one it
operates on. Stateless tokens cannot be un-issued; a denylist is the compensating control, and a
compensating control that is never consulted is worse than none, because it creates the belief
that revocation works.

**Detecting it faster.** After any revocation, compare the stored key against the credential you
are still able to use. Two UUIDs side by side settle it immediately — which is why it is worth
printing both rather than eyeballing Redis alone.

**Prevention.** Test revocation by replay: perform the revoking action, then deliberately re-send
the old credential and assert `401`. Anything less tests that the endpoint returns a status code,
not that it did anything.

---

## A5 — the revoked flag is written and never read

**Report:** R5.

**Symptom.** A refresh token that the database says is revoked still mints a full new token pair.

```
POST /api/auth/refresh with the logged-out token   ->  HTTP 200 + new accessToken
```

```
      token_id       | revoked |          replaced_by
---------------------+---------+--------------------------------
 60018cf4-6615-...   | t       | e3858070-4796-4ebf-9455-...
```

**Root cause.** `AuthService#refresh` validates four things and not the fifth:

```java
RefreshToken stored = refreshTokenRepository.findByTokenId(claims.getId())
        .orElseThrow(() -> new TokenRejectedException("Refresh token is not recognised"));

if (stored.getExpiresAt().isBefore(Instant.now())) { ... }        // expiry: checked
if (!hash(request.getRefreshToken()).equals(stored.getTokenHash())) { ... }  // hash: checked
if (account.getStatus() != AccountStatus.ACTIVE) { ... }          // status: checked
```

`stored.isRevoked()` is never consulted. Rotation sets it, logout sets it, the session listing
displays it, and no code path acts on it.

Because rotation also leaves the old row's hash intact, an old token continues to match its own
row forever. Every rotation therefore leaves behind a permanently valid credential.

**Location.** [`AuthService.java`](src/main/java/com/aegis/identity/service/AuthService.java),
the guard block in `refresh`.

**Why it happens in real systems.** The `revoked` column was added with the rotation feature. The
write side and the read side of a new field are two separate edits, and the write side is the one
that produces visible output — the session list in the UI immediately shows "revoked", so the
feature looks finished. Nothing reads the flag, and nothing fails.

This is also the defect that unit tests are least likely to catch, because a test for rotation
asserts that the new token works. Asserting that the *old* one has stopped working is a different
test, and it is only obvious to write if you are thinking about theft.

**The correct fix.**

```java
if (stored.isRevoked()) {
    auditService.record(stored.getAccountId(), "REFRESH_REUSE_DETECTED",
            "Revoked session " + stored.getTokenId() + " was presented again");
    revokeEntireFamily(stored);
    throw new TokenRejectedException("Refresh token has been revoked");
}
```

Rejecting is the minimum. The industry-standard behaviour is stronger: presenting an
already-rotated refresh token is *evidence of theft* — either the legitimate client or an attacker
is replaying, and you cannot tell which — so the correct response is to revoke the whole token
family for that account and force a fresh sign-in. `replaced_by` already gives you the chain to
walk.

Also make the lookup itself unambiguous — `findByTokenIdAndRevokedFalse` — so that a future caller
cannot forget the same check.

**Underlying concept.** A state flag has two halves and the compiler only enforces one of them.
Adding a column and writing to it feels like shipping the feature; nothing is enforced until
something branches on it. Whenever you add a boolean to a table, the immediate next question is
"which query filters on this", and if the answer is "none", the feature does not exist.

**Detecting it faster.** For any revocation feature, the test is always the same shape: revoke,
then replay, then assert refusal. R4 and R5 are the same test applied to two different
credentials, and both fail.

**Prevention.** Encode the invariant in the query, not in the caller. A repository method that
cannot return a revoked token is a stronger guarantee than a guard clause that a future edit can
skip.

---

## A6 — one side of the comparison is normalised

**Report:** R6.

**Symptom.** Registering with any capital letter in the address makes that address unusable for
sign-in, and allows a duplicate account despite a unique constraint.

```
POST /api/auth/register  Priya.Nair@aegis.test   ->  201
POST /api/auth/login     Priya.Nair@aegis.test   ->  401
POST /api/auth/login     priya.nair@aegis.test   ->  200
```

```
 id |         email         |   display_name
----+-----------------------+------------------
  5 | Dev.Ops@aegis.test    | Platform on-call
  8 | dev.ops@aegis.test    | Platform on-call
```

**Root cause.** `AuthService#register` normalises:

```java
String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
if (accountRepository.existsByEmail(email)) { throw new DuplicateAccountException(email); }
```

`AuthService#login` does not:

```java
String email = request.getEmail().trim();
Account account = accountRepository.findByEmail(email)...
```

Two consequences, and they are different bugs wearing the same cause:

1. The address the user typed at registration is not the address that was stored, so their own
   sign-in misses.
2. The uniqueness check runs on the normalised form while older rows hold un-normalised values, so
   `Dev.Ops@aegis.test` and `dev.ops@aegis.test` are two distinct strings and the unique index is
   satisfied by both. The constraint is doing its job — it is comparing exactly what it was asked
   to compare.

**Location.** [`AuthService.java`](src/main/java/com/aegis/identity/service/AuthService.java),
the first line of `register` against the first line of `login`.

**Why it happens in real systems.** Normalisation is added where the data enters, and "where the
data enters" feels like registration. The lookup is written separately, often earlier, and looks
complete on its own. Neither function is wrong in isolation; they are wrong relative to each other,
and nothing in the code expresses the relationship.

It also almost never reproduces for developers, who type lower-case addresses out of habit and use
seeded accounts that are already lower case.

**The correct fix.** Normalise in exactly one place and make every path go through it. A small
helper is enough:

```java
private static String normaliseEmail(String raw) {
    return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
}
```

used by `register`, `login` and any future lookup. For the existing bad rows, back-fill and then
enforce it in the database so the application cannot regress:

```sql
UPDATE accounts SET email = lower(email);
CREATE UNIQUE INDEX idx_accounts_email_lower ON accounts (lower(email));
```

A functional unique index is the real fix: it makes the invariant true regardless of what any
future code path forgets to do.

Note that the local part of an email address is case-sensitive per RFC 5321 and the domain is not.
Every mail provider in practice treats the local part case-insensitively, and treating addresses
as case-insensitive identifiers is the correct product decision — but it is a decision, and it
belongs in one documented place rather than being half-applied.

**Underlying concept.** If you normalise a value before storing it, every lookup of that value
must apply the identical normalisation. A comparison is only as good as the weaker of the two
sides, and database uniqueness constraints compare bytes, not intent.

**Detecting it faster.** When a credential "works sometimes", log the exact string on both the
write and the read path and compare them character by character. `SELECT email FROM accounts` next
to the string the user typed answers it in seconds.

**Prevention.** Normalise at the boundary — a custom deserialiser, a setter, or a single helper —
never at the call site, and back the invariant with a database constraint so the code is not the
only thing holding it up.

---

## Why the test suite is green

`mvn -B test` passes with all six defects live.

- `JwtServiceTest` constructs the service directly and asserts that tokens round-trip: subject,
  `jti`, type, claims, and that a foreign signature is rejected. All of that is true. It does not
  assert the *length* of the refresh window, which is A3, and it cannot see A1, which happens in
  the filter, one layer above.
- `AccountRepositoryTest` is a `@DataJpaTest` slice. The mapping and the queries are correct;
  every defect here is in the service or the security configuration.

Four of the six defects live in the interaction between two files — a filter and an annotation, a
configuration line and the line above it, a writer and a reader, a normaliser and a lookup. Unit
tests are, by construction, tests of one file. The only tests that would have caught these are
integration tests that sign in as each role, and replay tests that re-use a credential after it was
supposed to stop working.
