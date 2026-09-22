# SOLUTION — Northwind Shop Order Service

> **Answer key. Do not read this until you have finished investigating.**

Six defects. They are largely independent, but two of them mask each other (D1 and D2), and
one of them (D3) becomes strictly more dangerous once D2 is fixed.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| D1 | SHOP-248 | Checkout deletes a Redis key that no cart ever lived under | `service/OrderService.java` |
| D2 | SHOP-241 | `@Transactional` bypassed by self-invocation, so stock updates are never flushed | `service/OrderService.java` |
| D3 | SHOP-259 | `@Valid` missing on the add-to-cart handler | `controller/CartController.java` |
| D4 | SHOP-252 | `totalElements` computed from the current page, not the query | `dto/PagedResponse.java` |
| D5 | SHOP-255 | Cache written under the id, evicted under the SKU | `service/ProductService.java` |
| D6 | SHOP-263 | No ownership check on single-order read | `service/OrderService.java` + `controller/OrderController.java` |

---

## D1 — The cart survives checkout

### Symptom
`POST /api/orders` returns `201`, but `GET /api/cart` afterwards still returns the same
lines. Placing a second order immediately creates a duplicate order rather than failing
with "cart is empty".

### Root cause
Every other cart operation goes through `CartRedisRepository`, which builds its key with
`CartKeys.cartOf(customerId)` — that is `shop.cart.key-prefix` (`shop:cart:`) plus the
customer id, giving `shop:cart:1`.

`OrderService.placeOrder` reads the cart through `CartRedisRepository` (correct key) but
empties it with an inlined string:

```java
redisTemplate.delete("cart:" + customerId);
```

`DEL cart:1` on a key that does not exist returns `0` and throws nothing. Redis has no
concept of "that key was supposed to be there", so the failure is completely silent.

### Exact location
`src/main/java/com/northwind/shop/service/OrderService.java`, in `placeOrder`, the
`redisTemplate.delete(...)` call after `buildAndStoreOrder(...)`.

### Correct fix
Delete through the same abstraction that created the key:

```java
cartRedisRepository.clear(customerId);
```

and drop the now-unused `StringRedisTemplate` field and constructor parameter. The point of
the fix is not the one-line change — it is that `OrderService` should not know the Redis
key layout at all.

### Affected components
`OrderService`, `CartRedisRepository`, `CartKeys`, `ShopProperties.cart.keyPrefix`, Redis.

### Underlying concept
**Key ownership.** A key-value store has no schema and no referential integrity. If two
places construct the same key independently, nothing will ever tell you they disagree —
not the compiler, not the driver, not the server. The only defence is that exactly one
component is allowed to produce the key, and everyone else calls it. This is the same
reason you do not hand-write SQL column names in two different DAOs.

Note also that changing `shop.cart.key-prefix` in configuration would have silently broken
checkout, because only half the code respects it.

### Why this is realistic
`CartRedisRepository` has no `clear` semantics that fit "and also do this at the end of
checkout", so somebody in a hurry injected `StringRedisTemplate` — which was already on the
classpath — and wrote the key by hand. It works on the first read of the code because the
string *looks* right. The prefix was almost certainly added to `CartKeys` later, in a
separate change that never touched `OrderService`.

### Detecting it faster next time
When an API reports success but state did not change, stop reading the response and look at
the store. `KEYS '*'` before and after would have shown `shop:cart:1` sitting untouched in
under a minute. More generally: **verify writes at the storage layer, not at the HTTP
layer.** A `DELETE` that matches nothing, an `UPDATE` that matches zero rows, and a
successful no-op all look identical from the outside.

### Prevention
- One key-builder component; ban raw template access outside the repository package (an
  ArchUnit rule can enforce this).
- Make `clear` return the number of keys removed and log a warning at zero.
- An integration test with a real Redis (Testcontainers) that asserts the cart is empty
  after checkout — not that checkout returned `201`.

---

## D2 — Stock is never decremented

### Symptom
Orders are created successfully, but `products.stock_quantity` never changes. The
insufficient-stock guard appears to work when you test it once with a large quantity, which
makes the code look correct.

### Root cause
`OrderService.placeOrder` is **not** transactional. It calls `buildAndStoreOrder(...)`,
which **is** annotated `@Transactional` — but it calls it as `this.buildAndStoreOrder(...)`.

Spring implements `@Transactional` with a proxy. Calls that arrive from outside the bean go
through the proxy and get a transaction; calls made on `this` from inside the bean go
straight to the target object and get nothing. So `buildAndStoreOrder` runs with no
transaction at all, despite the annotation sitting directly above it.

Without an ambient transaction:

- `productRepository.findById(...)` starts and commits its *own* read-only transaction
  (`SimpleJpaRepository` is annotated `@Transactional(readOnly = true)`). The `Product` it
  returns is **detached** the moment that call returns.
- `product.setStockQuantity(...)` therefore mutates a plain Java object. There is no
  persistence context watching it, so dirty checking never runs and no `UPDATE products` is
  ever emitted.
- `orderRepository.save(order)` still works, because `save` supplies its own transaction.
  That is why the order lands in the database and the stock does not.

`spring.jpa.open-in-view: false` in `application.yml` removes the request-scoped
`EntityManager` that would otherwise blur this, which is what makes the failure clean and
100% reproducible rather than intermittent.

Because there is no single transaction, this defect also destroys atomicity: if line 3 of a
five-line cart throws `InsufficientStockException`, any work already done for lines 1 and 2
is not rolled back — there is nothing to roll back *from*, and nothing to roll back *to*.

### Exact location
`src/main/java/com/northwind/shop/service/OrderService.java` — `placeOrder` calling
`buildAndStoreOrder` directly, and the `@Transactional` on `buildAndStoreOrder` that
therefore does nothing.

### Correct fix
Put the transaction boundary on the method that is actually invoked from outside:

```java
@Transactional
public OrderResponse placeOrder(Long customerId, PlaceOrderRequest request) { ... }
```

`buildAndStoreOrder` can then be `private` (an annotation on a private method is dead
weight and worth removing, so the next reader is not misled).

Two details worth getting right:

1. The cart clear from D1 is a Redis side effect inside a database transaction. If the
   transaction rolls back after the cart was cleared, the customer loses their cart for an
   order that does not exist. The robust shape is to clear the cart *after* commit —
   `TransactionSynchronizationManager.registerSynchronization(...)` with an
   `afterCommit` hook, or simply performing the clear in `placeOrder` after the
   transactional call returns, with `placeOrder` itself no longer transactional and a
   separate transactional method called through a proxy (self-injection or a collaborator
   bean).
2. Reading and then writing `stock_quantity` without a lock is a lost-update race under
   concurrency. It is not what this ticket is about, and project 13 is where you will meet
   it properly — but if you noticed it, you were right to.

### Affected components
`OrderService`, `ProductRepository`, Hibernate's persistence context, Spring's transaction
proxy, `spring.jpa.open-in-view`.

### Underlying concept
**Proxy-based AOP only sees calls that cross the proxy boundary**, and **JPA only persists
changes to entities that are attached to an open persistence context that gets flushed.**
These two facts combine here: the annotation is inert, so the context is never open, so the
mutation is invisible. Either fact alone is survivable; together they produce a write that
vanishes without a single log line.

### Why this is realistic
The refactor that produced this is extremely common: a long `placeOrder` got split into
"validate and orchestrate" plus "do the work", and the `@Transactional` moved with the
body of the code rather than staying on the entry point. The result reviews well — the
annotation is *right there*, on the method that does the writing. Self-invocation is
invisible in a diff, and no test that mocks the repository will catch it, because mocks do
not care about persistence contexts.

### Detecting it faster next time
- Count SQL statements per request. `org.hibernate.SQL` at `DEBUG` shows every statement.
  A missing `update products set stock_quantity=?` is a two-second observation.
- Look for transaction begin/commit boundaries: `logging.level.org.springframework.
  orm.jpa.JpaTransactionManager=DEBUG` prints "Creating new transaction" / "Initiating
  transaction commit". If you see several tiny transactions where you expected one, you
  have found the shape of the bug before you have found the line.
- Rule of thumb: **an entity mutation with no surrounding transaction and no explicit
  `save()` is a no-op.** Whenever you see a setter on a loaded entity with neither, check
  the transaction boundary first.

### Prevention
- Annotate the public entry point, not the helper.
- Enable `spring.jpa.open-in-view: false` (this project does) so detachment is loud and
  early rather than accidental and late.
- Integration tests that assert on the *database* after the call, not on the response body.
- Static analysis: SpotBugs/Sonar and IntelliJ all flag self-invocation of
  `@Transactional` methods. Turn the inspection on.

---

## D3 — Add-to-cart accepts any quantity

### Symptom
`POST /api/cart/items` with `{"quantity": -5}` returns `200` and stores `-5`. The line
total goes negative, the cart subtotal drops, and eventually an order is persisted with a
negative `total_amount`. `PUT /api/cart/items/{productId}` with the same value correctly
returns `400`.

### Root cause
`AddCartItemRequest` declares `@NotNull @Min(1) @Max(20)` on `quantity`. Bean Validation on
a request body runs only if the parameter is annotated `@Valid` (or `@Validated`). The
handler is:

```java
public CartResponse addItem(@AuthenticationPrincipal AuthenticatedCustomer principal,
                            @RequestBody AddCartItemRequest request) {
```

No `@Valid`, so the constraints are decoration. `CartRedisRepository.increaseQuantity`
issues `HINCRBY`, which happily accepts negative deltas, and the value lands in Redis.

The damage then surfaces two layers away: `CartService.getCart` multiplies a negative
quantity by a positive price, and `OrderService` copies that into `order_items.line_total`
and `orders.total_amount`. Finance sees the symptom; the controller caused it.

Note that the stock guard does not catch it either: `product.getStockQuantity() < -5` is
false, so the negative line sails through.

### Exact location
`src/main/java/com/northwind/shop/controller/CartController.java`, the `addItem` handler
signature.

### Correct fix
```java
public CartResponse addItem(@AuthenticationPrincipal AuthenticatedCustomer principal,
                            @Valid @RequestBody AddCartItemRequest request) {
```

A defence-in-depth fix additionally rejects non-positive quantities in `CartService` (and
in `OrderService` when reading the cart), because Redis is shared mutable state that a
future producer could also write to.

### Affected components
`CartController`, `AddCartItemRequest`, `CartService`, `CartRedisRepository`,
`OrderService`, the `orders` and `order_items` tables.

### Underlying concept
**Constraints are inert until something evaluates them.** In Spring MVC that trigger is
`@Valid`/`@Validated` on the parameter. Having the annotations on the DTO makes the code
*look* validated in review, which is precisely why this survives.

Secondarily: **validate at the boundary, because bad data outlives the request that
created it.** A negative quantity in Redis persists for 72 hours and corrupts every later
computation that touches it, long after the offending HTTP call has been forgotten.

### Why this is realistic
The two handlers were written at different times by different people. The one with `@Valid`
proves the team knows the pattern; the one without proves nobody diffed them. Client-side
UI validation (a stepper that cannot go below 1) hides it in normal use, so it only appears
via a script, a mobile client, a retry with a mangled payload, or a curious user.

### Detecting it faster next time
When a value is illegal, ask **where it entered the system**, not where it hurt. Walk it
backwards: negative `total_amount` → negative `line_total` → negative quantity in Redis →
whatever wrote to Redis. Each step is one grep. Guessing at the arithmetic wastes hours.

A useful habit: for every write endpoint, send one deliberately illegal payload and confirm
you get `400`. It takes minutes and finds this class of defect immediately.

### Prevention
- A `@ParameterizedTest` over every write endpoint asserting `400` for out-of-range input.
- ArchUnit: every `@RequestBody` parameter in a `@RestController` must also carry `@Valid`.
- Enforce invariants in the domain type (a `Quantity` value object that cannot hold a
  non-positive number) so no controller can produce an illegal state at all.

---

## D4 — Paging metadata reports the page size as the total

### Symptom
`GET /api/products?size=5` returns `totalElements: 5` alongside `totalPages: 3`. The two
contradict each other. Front-end paginators that derive the last page from `totalElements`
show only one page.

### Root cause
`PagedResponse.of` builds the envelope from two sources — the Spring Data `Page` for
metadata, and a separately mapped DTO list for content — and takes the total from the wrong
one:

```java
response.setTotalElements(content.size());
```

`content` is the current page, so `totalElements` is always `min(size, remaining)`.
`totalPages` comes from `source.getTotalPages()`, which is derived from the real total, so
the two disagree — and the disagreement is what makes the bug visible at all.

### Exact location
`src/main/java/com/northwind/shop/dto/PagedResponse.java`, the static `of` factory.

### Correct fix
```java
response.setTotalElements(source.getTotalElements());
```

### Affected components
`PagedResponse`, and therefore every paged endpoint: `GET /api/products` and
`GET /api/orders`.

### Underlying concept
**A paging envelope has two independent facts in it — what is on this page, and how much
exists in total — and only the second requires a `count` query.** Mixing them up is easy
precisely because on the last page (or on a single-page result) `content.size()` and
`getTotalElements()` are equal. The bug is invisible in exactly the situation most
developers test in: a small seed dataset that fits on one page.

### Why this is realistic
`content.size()` is right there, already in scope, and is a `Collection` API everyone
reaches for by reflex. The mistake reads naturally. And because the seed data in most dev
environments is smaller than the default page size, the values agree locally and diverge in
production.

### Detecting it faster next time
When two fields in one response contradict each other, the bug is almost always in the code
that *assembles* the response, not in the query. Find the single place both fields are set
and read those two lines. This one is a 30-second fix once you stop looking at the
repository.

### Prevention
- A test that asserts `totalElements > content.size()` for a dataset larger than one page.
  Seed data must exceed the default page size, or paging is effectively untested.
- Do not hand-roll the envelope: `page.map(mapper::toResponse)` keeps the metadata attached
  to the `Page` and makes the mistake unrepresentable.

---

## D5 — Product detail cache is never invalidated

### Symptom
After a back-office price change, `GET /api/products/{id}` keeps returning the old price
for up to 30 minutes. `GET /api/products` (the list) shows the new price immediately. The
`PUT` response itself shows the new price. The database is correct. It fixes itself
eventually. QA cannot reproduce it.

### Root cause
The read path caches under the product id:

```java
@Cacheable(cacheNames = "products", key = "#productId")
public ProductResponse getProduct(Long productId)
```

which produces the Redis key `products::4`. The write path evicts under the SKU:

```java
@CacheEvict(cacheNames = "products", key = "#request.sku")
public ProductResponse updateProduct(Long productId, UpdateProductRequest request)
```

which deletes `products::NW-AUD-2201` — a key that never existed. The eviction succeeds
(deleting a missing key is not an error), the log line prints the new price, and
`products::4` stays exactly as it was until its 30-minute TTL in `RedisCacheConfig`
expires.

The list endpoint is unaffected because `listProducts` is not cached at all, which is what
produces the confusing split: two endpoints, same database row, two different answers.

### Exact location
`src/main/java/com/northwind/shop/service/ProductService.java` — the `key` expression on
`@CacheEvict` over `updateProduct`.

### Correct fix
```java
@CacheEvict(cacheNames = "products", key = "#productId")
```

Consider also whether the entry should be *replaced* rather than removed (`@CachePut` with
the same key) so the next reader does not pay for a cache miss, and whether deactivating a
product should evict too.

### Affected components
`ProductService`, `RedisCacheConfig` (TTL), the `products` Redis cache, `ProductController`,
`AdminProductController`.

### Underlying concept
**A cache entry is identified by its key, and invalidation is only correct when the evict
key is provably the same expression as the cacheable key.** Spring will not warn you: the
two SpEL expressions are evaluated independently, in different methods, against different
argument lists. There is no type checking and no runtime check that the key exists.

The second concept is the TTL. A TTL turns a permanent correctness bug into an intermittent
one. That is worse, not better — it guarantees the report will be "sometimes stale" and
guarantees it will be closed as unreproducible at least once.

### Why this is realistic
The author of `updateProduct` thought in domain terms ("the thing I am updating is the
product with this SKU") while the author of `getProduct` thought in URL terms ("the thing
being fetched is id 4"). Both expressions are individually sensible. The bug lives in the
gap between them, and a reviewer looking at either method in isolation sees nothing wrong.

QA cannot reproduce it because his Redis is empty when he starts: his first read populates
the cache *after* the update, so he sees the new price. The bug requires a warm cache — a
condition that is normal in production and rare on a laptop. That is the entire "works on
my machine" mechanism, and it is worth internalising: **cache bugs are invisible on a cold
cache.**

### Detecting it faster next time
- Reproduce with the cache warm, deliberately: read, write, read.
- `redis-cli --scan --pattern 'products*'` and `TTL` on the key. If the key survives the
  write, the eviction did not target it. That is the whole diagnosis.
- `MONITOR` on a scratch Redis shows the exact `DEL` the application issues, which makes
  the wrong key name unmissable.
- When one endpoint is stale and another is fresh for the same row, the difference between
  them *is* the bug. Diff them first.

### Prevention
- Extract key construction into a named method or a `KeyGenerator` used by both annotations
  so they cannot drift.
- Prefer `@CacheEvict(key = "#id")` where `id` is the same parameter used by `@Cacheable`,
  and keep both annotations adjacent in the file so drift is visible in review.
- Integration test: read (populate), update, read again, assert the new value. This test is
  four lines and would have caught it permanently.
- Alarm on cache-hit ratio and on staleness for critical fields such as price.

---

## D6 — Any authenticated customer can read any order

### Symptom
`GET /api/orders/3` returns customer 2's order — including the shipping address — to
customer 1. An insecure direct object reference (IDOR).

### Root cause
`GET /api/orders` is scoped correctly, via
`orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)`.

`GET /api/orders/{orderId}` is not. The controller does not pass the principal down, and
the service loads by primary key alone:

```java
public OrderResponse getOrder(Long orderId) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    return orderMapper.toResponse(order);
}
```

Spring Security answers "is this caller logged in?" — which it is — and nothing answers "is
this row theirs?".

### Exact location
`src/main/java/com/northwind/shop/service/OrderService.java` (`getOrder`) and
`src/main/java/com/northwind/shop/controller/OrderController.java` (`get`, which already
receives the principal and does not use it).

### Correct fix
Scope the query itself rather than loading and then checking:

```java
// OrderRepository
Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

// OrderService
@Transactional(readOnly = true)
public OrderResponse getOrder(Long orderId, Long customerId) {
    Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    return orderMapper.toResponse(order);
}
```

and pass `principal.getCustomerId()` from the controller.

Return **404**, not 403. A `403` confirms the order exists, which lets an attacker
enumerate order ids and learn your order volume. Make "not yours" and "not there"
indistinguishable.

Loading the row and then comparing `order.getCustomer().getId()` also works, but is weaker:
it is a check somebody can delete later without any query failing, and it briefly
materialises data the caller is not entitled to. Scoping the query makes the rule
structural.

### Affected components
`OrderController`, `OrderService`, `OrderRepository`, `AuthenticatedCustomer`, the whole
security posture of the order endpoints.

### Underlying concept
**Authentication is not authorization, and role checks are not ownership checks.** Every
customer here legitimately holds `ROLE_CUSTOMER`; the question this endpoint must ask is
about the *relationship between this principal and this row*, which no role can express.
Any endpoint that accepts a client-supplied identifier for a row belonging to somebody
needs an ownership predicate, and the safest place for that predicate is inside the query.

### Why this is realistic
The list endpoint was written first and is correct, so the file *looks* like a file that
thinks about ownership. The detail endpoint was added later — probably to support a
"view order" link where the id came from the customer's own history, so at the time every
id really did belong to the caller. The assumption was true when written and became false
the moment somebody typed a different number in the address bar. This is the single most
common web vulnerability in the OWASP Top 10 (Broken Access Control) and it looks exactly
like this in real code.

### Detecting it faster next time
Make it a habit: **for every endpoint with an id in the path, log in as a second user and
request the first user's id.** Two accounts and two curl calls. Automate it — one
parameterised test per owned resource is cheap and catches the whole class.

### Prevention
- Ownership belongs in the repository method signature, so a query that is not scoped is
  visibly not scoped.
- `@PreAuthorize("@orderGuard.isOwner(#orderId, principal)")` if you prefer it declarative
  and want it visible at the controller.
- A security test suite that, for every owned resource, asserts `404` for a foreign id.
- Code review checklist item: "does every path variable that names a row get an ownership
  predicate?"

---

## How these interact

Worth understanding, because it is the point of the exercise.

**D1 and D2 mask each other.** With stock never decrementing (D2), the duplicate order that
D1 enables is merely embarrassing — the numbers are wrong but no inventory is lost. Fix D2
alone and D1 becomes expensive: every double-submit now really removes stock twice. A
developer who fixes D2, deploys, and then gets a *worse* incident report has met "fixing one
thing exposes another" in its natural habitat.

**D3 becomes destructive once D2 is fixed.** With a negative quantity in the cart,
`product.setStockQuantity(stock - (-5))` *increases* stock. While D2 is present that
mutation is discarded, so nothing happens. Fix D2 first and D3 turns into a stock-inflation
vector: an attacker adds a negative quantity, checks out, and manufactures inventory.

**D2 also hides the concurrency problem underneath it.** Once stock updates actually
persist, the read-modify-write in `buildAndStoreOrder` is a textbook lost update under
concurrent checkout. It is out of scope for this project — project 13 is built around it —
but if you spotted it while fixing D2, that is the correct instinct, and adding
`@Version` to `Product` or a `SELECT ... FOR UPDATE` is a legitimate improvement here.

**D4 and D5 are genuinely independent** of everything else, and of each other. Not every
defect in a real system is connected; part of the skill is noticing when a symptom is
isolated so you stop looking for a grand unified explanation.

**Suggested fix order:** D4 (trivial, unblocks reading the data), D6 (security, isolated),
D3 (stops new bad data entering), D2 (the big one), D1 (now safe to fix), D5 (independent,
needs a warm cache to verify).

---

## What the test suite tells you

`mvn test` passed the entire time all six defects were live. That is not an accident and it
is not unusual:

- `PricingCalculatorTest` tests arithmetic that was never wrong.
- `ProductMapperTest` tests a mapper that was never wrong.
- `CustomerRepositoryTest` tests a repository method that was never wrong.

Every defect in this project lives in a **seam** — between a controller and its validation,
between a service and its proxy, between two Redis key builders, between two SpEL
expressions, between an id in a URL and a row in a table. Unit tests with mocks cannot see
seams, because the mock replaces the very component whose interaction is broken.

The tests that would have caught these are integration tests that assert on the *state of
the system* after a call: what is in MySQL, what is in Redis, what a second user can see.
That is the practical lesson of project 01, and it is worth more than any of the six fixes.
