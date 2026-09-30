# Debugging guide — Aegis Identity Platform

## The business

Aegis is the identity service every internal tool at the company delegates to. It owns accounts,
passwords, roles and permissions, and it issues the tokens that everything else trusts. The
document API bundled with it is the smallest real protected resource that could be built: it
exists so that "who is allowed to do what" has a concrete answer.

The platform has been live for about a year. It started as login plus a role check. Fine-grained
permissions were added later, when "editor" stopped being a precise enough description of what
somebody was allowed to do. Refresh token rotation and the logout denylist were added after a
security review, in a hurry, a week before an audit.

## What the system does

- **Two tokens.** The access token is short lived and self-contained: it carries `roles` and
  `permissions` claims, so a protected endpoint can decide without calling back to the database.
  The refresh token is long lived, carries no grants, and is recorded in `refresh_tokens` so a
  session can be listed and revoked.
- **Two layers of authorization.** URL rules in `SecurityConfig` say who may reach a path.
  `@PreAuthorize` on the service layer says who may perform an operation. Both are in play on
  every request.
- **A denylist.** Logging out must stop a token being accepted before it expires on its own, so
  revoked tokens are written to Redis with a time to live.

## Reports from the business

Six, collected over a sprint. Some came from users, some from the security group, one from an
auditor. They are recorded as reported.

---

### R1 — "Nobody can create a document"

> Every editor gets 403 when they try to save. Not a validation error, not a login problem — the
> save button just fails.
>
> The odd part is that it works perfectly for me, and I wrote the feature. It works for the whole
> platform team. It does not work for a single person in the content team.
>
> We checked their token on jwt.io. `"permissions": ["document:read","document:write"]`. It is
> right there in the token. We checked the database. `ROLE_EDITOR` has `document:write`. The
> permission exists, the grant exists, the token carries it, and the API says no.

---

### R2 — "An auditor can see the whole admin console"

> Security review finding. An account with only `ROLE_AUDITOR` can call `GET /api/admin/accounts`
> and `GET /api/admin/audit` and gets a full `200` with every account in the company, their
> email addresses, their roles and their lock status.
>
> They cannot lock or unlock anybody, and they cannot grant roles — those correctly come back 403.
> So it is not that admin is wide open. It is that some of it is.
>
> Nobody can explain why it is inconsistent. The routes are all under the same prefix.

---

### R3 — "Everyone has to sign in again every morning"

> We set the session length to two weeks. People are being signed out roughly once a day.
>
> It is not exactly once a day, which is what made this hard to pin down. Somebody who signs in at
> 09:00 is fine until the evening. Somebody who signs in at 16:00 gets kicked out during the
> following morning. The people who complain loudest are the ones who work irregular hours.
>
> Access tokens are fine. It is only the long session that is wrong.

---

### R4 — "Logging out does not log you out"

> Reported by the security group, and this is the one that worries them.
>
> A user signs out. The UI clears and looks correct. But if you keep the old access token — which
> anybody with the browser devtools open has — you can keep calling the API for another quarter of
> an hour as if nothing happened. `GET /api/auth/me` returns their profile. Documents are readable.
>
> The denylist definitely has something in it after a logout. They checked Redis. There is a key.

---

### R5 — "A signed-out session can mint brand new tokens"

> Same investigation as R4, and worse.
>
> After logging out, posting the old refresh token to `/api/auth/refresh` returns `200` and a
> complete new pair of tokens. So the fifteen-minute window in R4 is not a window at all — anyone
> holding an old refresh token can renew indefinitely.
>
> The database says `revoked = true` on that row. We are looking at it. And it still works.

---

### R6 — "I registered and then could not sign in"

> New joiner. Signed up with `Priya.Nair@aegis.test`, got a `201` and a token, closed the tab.
> Came back an hour later, typed the same address, `401`.
>
> Support told her to try it in lower case and that worked, which nobody can explain to her.
>
> Related, possibly: our on-call rota account appears twice in the account list. Same person, same
> display name, two rows. The email column has a unique constraint on it, so that should not be
> possible.

---

## How to work on this

Six reports. Two of them (R4 and R5) came from the same investigation and may or may not have the
same cause — decide that for yourself rather than assuming.

The tools that will actually help:

- **Read the token.** It is base64url, not encryption. `cut -d. -f2` and decode it. The payload
  tells you the `jti`, the expiry, and exactly what the bearer is claiming. Any argument about
  what a user "has" is settled by this.
- **Read the authorities.** What the token claims and what Spring Security ends up holding are
  two different things, and the gap between them is where authorization bugs live. A breakpoint
  in `JwtAuthenticationFilter`, or the `DEBUG` line it already logs, shows you the granted
  authorities that were actually built.
- **Turn on the security log.** `logging.level.org.springframework.security: DEBUG` prints the
  whole filter chain at startup and narrates each authorization decision, including which
  `RequestMatcher` matched. When a `403` makes no sense, this says which rule produced it.
- **Read the database.** `refresh_tokens` has `issued_at`, `expires_at`, `revoked` and
  `replaced_by`. `accounts` has `email` and `status`. `audit_events` has the whole story in
  order.
- **Read Redis.** `docker exec lab-redis redis-cli KEYS 'aegis:denylist:*'` and compare what is
  in there against the `jti` of the token you are actually presenting. Compare the strings
  character by character, not by eye.
- **Postman or curl, twice.** Several of these only appear on the second request, or on a request
  made by somebody other than you.

One discipline worth adopting for this project in particular: when an authorization decision
surprises you, write down the three things separately — what the database grants, what the token
claims, and what the authority strings look like at the moment the decision is made. The bug is
always in a mismatch between two of the three, and you cannot see the mismatch until you have
written all three down.

---

## Hints

One at a time.

### Level 1 — the shape of the problem

1. Two of these are authorization decisions that are wrong in opposite directions: one denies
   somebody who should be allowed, one allows somebody who should be denied. Neither is a bug in
   the roles or the permissions data.
2. Two of them are about revocation not taking effect. They have different causes.
3. One is arithmetic. One is string handling. Neither involves Spring Security at all.
4. Nothing here is a signature problem, an issuer problem or a clock skew problem. Tokens verify
   correctly throughout.

### Level 2 — narrowing

5. R1: decode the token, then find the exact string the authorization rule is testing for.
   Compare it against the exact string the filter put into the `Authentication`. Not the claim —
   the authority.
6. R1 again: ask why it works for an administrator. The rule that guards the write has two
   branches.
7. R2: the routes that leak are all reads. The routes that do not leak are all writes. That is
   the whole clue.
8. R3: the number in the configuration is right. Look at `refresh_tokens.expires_at` minus
   `issued_at` and say out loud what unit that interval is in.
9. R4: after a logout, list the denylist keys, then decode the access token you are still using
   and read its `jti`. Put the two strings next to each other.
10. R5: the row says `revoked = true`, so the write side is fine. Read the read side —
    specifically, read the list of checks `refresh` performs before it accepts a token, and ask
    which field is never mentioned.
11. R6: log the exact string that `register` stores and the exact string that `login` searches
    for. They pass through different amounts of processing.

### Level 3 — the mechanism

12. R1: Spring Security has a convention about a prefix. `hasRole('X')` and `hasAuthority('X')`
    are not the same test, and one of them silently adds something to the string.
13. R2: `authorizeHttpRequests` evaluates its matchers in the order they are written and stops at
    the first one that matches. A broad rule written above a narrow rule means the narrow rule is
    never consulted.
14. R3: `Duration` has a factory method per unit, and the property name says which one was
    intended.
15. R4: a logout request carries one token in the body and another in the header. Only one of them
    ends up on the denylist, and the filter only ever checks the other.
16. R5: an expired token is rejected, a token whose hash does not match is rejected, an inactive
    account is rejected. Revocation is stored and never read.
17. R6: `toLowerCase` appears exactly once in the service, and the lookup on the other side does
    not have it.

### Level 4 — where to look

18. R1: `JwtAuthenticationFilter`, the stream that builds `authorities`, against the
    `@PreAuthorize` expressions in `DocumentService`.
19. R2: `SecurityConfig#securityFilterChain`, the order of the lines inside
    `authorizeHttpRequests`.
20. R3: `JwtService#issueRefreshToken`, the line that computes `expiresAt`.
21. R4: `AuthService#logout`, the argument passed to `denylist.add`, against the argument passed
    to `denylist.contains` in `JwtAuthenticationFilter`.
22. R5: `AuthService#refresh`, the block of guard clauses after the repository lookup.
23. R6: `AuthService#register` and `AuthService#login`, the first line of each.

---

Do not tell me why. Reproduce it, trace it, then tell me what you found.

When you think you have one, give me:

1. the symptom, precisely,
2. the root cause,
3. the evidence — the decoded claim, the authority string, the table row, the Redis key, the log
   line,
4. your proposed fix.

I will evaluate the reasoning, not the conclusion.
