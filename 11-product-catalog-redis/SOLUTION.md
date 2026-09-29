# Solution — Lumen Retail Product Catalogue

Five defects. Do not read this until you have finished with the project.

---

## C1 — a write evicts the product but not anything derived from it

**Report:** R1.

**Symptom.** After `PUT /api/merch/products/1` changes a name and a price, the product endpoints
are immediately correct, but the category listing and the browse rail keep returning the old values
for up to 15 and 5 minutes respectively. Every response is `200 OK`; nothing is logged.

Reproduced:

```
listing 1 before:  [(3, 'Cadet 13 Student Laptop', 64900.0), (1, 'Meridian 14 Ultrabook', 124900.0), ...]
PUT 200
product after   :  Meridian 14 Ultrabook (2026 refresh) 99999.0
by sku after    :  Meridian 14 Ultrabook (2026 refresh) 99999.0
listing 1 after :  [(3, 'Cadet 13 Student Laptop', 64900.0), (1, 'Meridian 14 Ultrabook', 124900.0), ...]
browse  after   :  [(1, 'Meridian 14 Ultrabook', 124900.0), (2, 'Meridian 16 Creator', 189900.0)]
```

**Root cause.** `MerchandisingService#updateProduct` is annotated

```java
@CacheEvict(cacheNames = {"products", "productsBySku"}, key = "#productId")
```

Six caches hold data derived from a product. Two are evicted. `categoryListing`, `productSearch`
and `productBrowse` all contain copies of the same product and are never touched by a write, so
they serve the pre-edit copy until their TTL expires.

`variants` is in the same position: `VariantResponse.effectivePrice` is computed from the product
price, so a reprice leaves the variant picker quoting the old price too.

**Location.** [`MerchandisingService.java:50`](src/main/java/com/lumen/catalog/service/MerchandisingService.java#L50).

**Why it happens in real systems.** The product cache and its eviction were written together, by
the same person, in the same commit. The category cache was added eight months later by somebody
solving a different problem — a slow category page — and nothing in the code, the tests or the
compiler connects a new `@Cacheable` to the existing `@CacheEvict`. There is no compile-time link
between a cache and the writes that invalidate it. The set of caches that need evicting is
knowledge that lives only in somebody's head, and it goes stale the moment a new cache is added.

The TTL makes it much worse. A bug that repairs itself in fifteen minutes is a bug that is always
"already fixed" by the time anyone investigates.

**The correct fix.** Evict every cache the write invalidates. The listing, browse and search caches
are keyed by things the write does not know (arbitrary filters), so they have to be cleared
wholesale:

```java
@Caching(evict = {
        @CacheEvict(cacheNames = "products", key = "#productId"),
        @CacheEvict(cacheNames = "variants", key = "#productId"),
        @CacheEvict(cacheNames = "productsBySku", allEntries = true),
        @CacheEvict(cacheNames = "categoryListing", allEntries = true),
        @CacheEvict(cacheNames = "productSearch", allEntries = true),
        @CacheEvict(cacheNames = "productBrowse", allEntries = true)
})
@Transactional
public ProductResponse updateProduct(...)
```

`productsBySku` needs `allEntries` (or a second lookup) because the method receives an id, not a
SKU, and a category change moves the product between two listings, so evicting only the new
category's key is not enough either.

`createProduct` needs the same treatment — a new product belongs in a listing, a search page and a
rail, and none of them will show it until their TTL expires.

Note also that `@CacheEvict` without `beforeInvocation = true` runs *after* the method returns but
inside the same transaction, so a concurrent read between the eviction and the commit can re-cache
the old row. For a catalogue edited twenty times a day this is academic; for a hot aggregate it is
not, and the answer there is to evict after commit via `TransactionSynchronizationManager`.

**Underlying concept.** Cache invalidation is a graph problem, and Spring only gives you a naming
convention. Every derived view of an entity is an edge from that entity, and a write has to walk
all of them. If you cannot enumerate the edges, you cannot invalidate correctly.

**Detecting it faster.** Any time a cache is added, grep for the existing `@CacheEvict` annotations
on the same aggregate and decide, explicitly, whether the new cache belongs in each one. An
integration test that writes and then reads *every* read endpoint would have caught it in one run.

**Prevention.** Keep the cache names for one aggregate in one constants class, so the set is
visible in a single place and a new entry is obvious to a reviewer. Better, for derived views,
prefer a short TTL and accept the staleness explicitly rather than pretending the eviction is
complete.

---

## C2 — a `Page` is cached, and a `Page` cannot be read back

**Report:** R2.

**Symptom.** The first `GET /api/catalog/search?...` after a cache flush returns `200`. The
identical second request returns `500`. Five minutes later — one TTL — it works once again.

```
call 1:  {"content":[{"id":9,...}], ...}   HTTP 200
call 2:  {"status":500,"error":"Internal Server Error"}   HTTP 500
key   :  catalog:productSearch::null|Lumen|null|0|5
```

Log:

```
org.springframework.data.redis.serializer.SerializationException: Could not read JSON:
Cannot construct instance of `org.springframework.data.domain.PageImpl`
(no Creators, like default constructor, exist): cannot deserialize from Object value
```

**Root cause.** `CatalogService#search` returns `Page<ProductResponse>` and is `@Cacheable`.
Jackson serialises the concrete `PageImpl` happily — it has getters. It cannot deserialise it:
`PageImpl` has no no-arg constructor and no `@JsonCreator`, so on the read path
`GenericJackson2JsonRedisSerializer` blows up. The write succeeded, the read cannot.

This is why it fails on the *second* call, and why it worked on QA with Redis disabled: with no
cache there is no read path, so the defect is invisible.

**Location.** [`CatalogService.java:82-90`](src/main/java/com/lumen/catalog/service/CatalogService.java#L82).

**Why it happens in real systems.** `Page<T>` is the natural return type of a Spring Data query and
the natural response body for a paginated endpoint, so it propagates outward through the service
layer without anybody deciding to put it there. Adding `@Cacheable` to an existing method looks
like a zero-risk annotation change — the method body does not move, the tests still pass, the first
manual click still works. Nothing warns you that the return type has to survive a round trip
through a serialiser.

The failure mode is also perfectly shaped to escape review: it needs a warm cache, and the
developer who added the annotation almost certainly tested with a cold one.

**The correct fix.** Do not cache framework types. Cache a DTO you own. `PagedResponse` is already
in the `dto` package for exactly this and is currently unused:

```java
@Cacheable(cacheNames = "productSearch",
        key = "#term + '|' + #brand + '|' + #categoryId + '|' + #minPrice + '|' + #maxPrice"
              + " + '|' + #pageable.pageNumber + '|' + #pageable.pageSize")
@Transactional(readOnly = true)
public PagedResponse<ProductResponse> search(String term, String brand, Long categoryId,
                                             BigDecimal minPrice, BigDecimal maxPrice,
                                             Pageable pageable) {
    Page<Product> page = productRepository.search(term, brand, categoryId, minPrice, maxPrice, pageable);
    return PagedResponse.of(page, catalogMapper.toResponses(page.getContent()));
}
```

`PagedResponse` has a no-arg constructor and plain setters, so it round-trips.

While you are in there: the key omits `minPrice` and `maxPrice`. Two searches that differ only in
their price band share a cache entry and return each other's results — the same class of defect as
C3, sitting quietly in the same method. Fixing C2 without noticing it leaves a live bug behind.

**Underlying concept.** Anything you put in a cache leaves the JVM and has to come back. That makes
the cached type part of a serialisation contract, and framework types are not written to that
contract. The same applies to immutable collections: `Stream.toList()` returns a final
`ImmutableCollections$ListN`, which Jackson's default typing skips (it only writes type information
for non-final types) and then cannot reconstruct. `CatalogMapper` collects to `ArrayList` for that
reason.

**Detecting it faster.** Exercise every cached endpoint twice in a row in your integration tests,
against a real Redis. One call proves the write path. Only the second proves the read path.

**Prevention.** Make it a rule that `@Cacheable` methods return types declared in your own `dto`
package. A simple ArchUnit rule can enforce it.

---

## C3 — the cache key is a display label

**Report:** R3.

**Symptom.** Two browse rails that differ only by category return identical products. `200 OK`, no
log, real products — the wrong ones.

```
rail Lumen/Accessories(4):  [(12,'Laptop Sleeve 14in'), (9,'Mechanical Keyboard 87K'),
                             (10,'Precision Mouse 8K'), (11,'USB-C Dock 11-in-1')]
rail Lumen/Displays(3)   :  [(12,'Laptop Sleeve 14in'), (9,'Mechanical Keyboard 87K'),
                             (10,'Precision Mouse 8K'), (11,'USB-C Dock 11-in-1')]
DB truth for Lumen in 3  :  14 Lumen Portable 15 Monitor
browse keys              :  catalog:productBrowse::Lumen
```

One key for two rails. Whichever rail is requested first wins, and the other silently inherits its
contents — which is why the symptom moves around after a deploy.

**Root cause.** `CatalogService#browse` is annotated `@Cacheable(cacheNames = "productBrowse",
key = "#filter")`. When the key expression evaluates to an object, `RedisCache#convertKey` turns it
into a string, and for a type it does not recognise it calls `toString()`. `BrowseFilter#toString`
is a human-readable label:

```java
@Override
public String toString() {
    return brand == null ? "all-brands" : brand;
}
```

so `categoryId` never reaches the key.

Note what happens if you simply delete that `toString()`: Spring Data Redis refuses outright with
`IllegalStateException: Cannot convert cache key ... to String`. A hard failure is the friendly
version of this bug. The partial `toString()` converts a crash into silent data corruption.

**Location.** [`BrowseFilter.java`](src/main/java/com/lumen/catalog/dto/BrowseFilter.java), the
`toString()` override; consumed at
[`CatalogService.java:95`](src/main/java/com/lumen/catalog/service/CatalogService.java#L95).

**Why it happens in real systems.** The `toString()` was written for logging, and it was correct
for logging — a rail *is* "the Meridian rail" in conversation. Then somebody added `@Cacheable` to
the method and used the filter object as the key because it read well. Two reasonable decisions,
made months apart by different people, neither of which is wrong on its own. The coupling between
them is invisible: nothing in `BrowseFilter` says "this string is a cache key", and nothing in
`CatalogService` says "this key depends on a `toString()` in another file".

The historical version of this is even more common: the rail originally *was* brand-only, the
`toString()` was complete, and `categoryId` was added to the class later without anyone realising a
cache key depended on the field list.

**The correct fix.** Never let `toString()` be the key. Build the key from the fields explicitly:

```java
@Cacheable(cacheNames = "productBrowse", key = "#filter.brand + '|' + #filter.categoryId")
```

or, if you want to keep passing the object, register a key converter and give the class a proper
`equals`/`hashCode`, or implement `KeyGenerator`. For simple cases, a `record` is the cleanest
answer — it generates `equals`, `hashCode` and a `toString()` that includes every component, and it
fails loudly if a component is added.

**Underlying concept.** A cache key must be a total function of every input that changes the
result. Anything less is aliasing, and aliasing in a cache is not a performance bug, it is a
correctness bug that returns another user's data. This is the same defect as the missing
`minPrice`/`maxPrice` in C2's key.

**Detecting it faster.** Read the keys. `redis-cli KEYS 'catalog:*'` next to the list of parameters
the endpoint accepts answers this in ten seconds. If a key has fewer moving parts than the request
does, you have found it.

**Prevention.** Prefer explicit string key expressions over object keys, and review them against
the method signature. If a method takes five arguments, the key should mention five things.

---

## C4 — a 404 is cached as an answer

**Report:** R4.

**Symptom.** A SKU looked up before it exists keeps returning `404` for 30 minutes after it has
been created, while the same product is `200` by numeric id.

```
lookup LM-ACC-3199 before it exists:  HTTP 404
redis:  catalog:productsBySku::LM-ACC-3199  ->  "...org.springframework.cache.support.NullValue..."
merch creates it:                     HTTP 201  (id 16)
database:                             16 LM-ACC-3199 Braided USB-C Cable 2m
storefront deep link again:           HTTP 404
but by id:                            HTTP 200
ttl on the poisoned key:              1797
```

**Root cause.** Two decisions that are individually defensible:

1. `CatalogService#getProductBySku` ends in `.orElse(null)` — a miss returns `null` and the
   controller turns that into a 404.
2. `RedisCacheConfig#baseConfiguration` never calls `disableCachingNullValues()`.

Spring Cache stores a `NullValue` marker so that a genuinely-null result is cached rather than
recomputed. With Redis that marker is written to the key and honoured for the full 30-minute TTL.
The negative answer is now pinned, and creating the product does not evict it because
`createProduct` evicts nothing at all.

The kiosk workflow in the report is the trigger: scanning the printed label before the product
exists is what plants the marker.

**Location.** [`CatalogService.java:61-69`](src/main/java/com/lumen/catalog/service/CatalogService.java#L61)
and [`RedisCacheConfig.java:62-76`](src/main/java/com/lumen/catalog/config/RedisCacheConfig.java#L62).

**Why it happens in real systems.** Caching negative lookups is a legitimate technique — it is the
standard defence against cache-penetration attacks, where an attacker requests thousands of
non-existent keys to force database traffic. So the behaviour is not obviously wrong, and it is the
*default*. What makes it a bug here is the combination with a long TTL and an absent invalidation
on create. Nobody chose this; it is what you get by writing `orElse(null)` and not reading the
`RedisCacheConfiguration` javadoc.

It is also almost impossible to hit in testing, because it requires the lookup to happen *before*
the create — the opposite of the order any developer would try.

**The correct fix.** Pick one, and preferably both:

```java
return RedisCacheConfiguration.defaultCacheConfig()
        .disableCachingNullValues()
        ...
```

and make the method express absence as absence rather than as a cached value:

```java
@Cacheable(cacheNames = "productsBySku", key = "#sku", unless = "#result == null")
@Transactional(readOnly = true)
public ProductResponse getProductBySku(String sku) {
    return productRepository.findBySku(sku)
            .filter(Product::isActive)
            .map(catalogMapper::toResponse)
            .orElseThrow(() -> new ResourceNotFoundException("Product", sku));
}
```

Throwing is better than returning `null`: `@Cacheable` never caches a method that threw, the
controller loses its null check, and the 404 comes from the exception handler like every other 404
in the service. `CatalogController#productBySku` simplifies to a one-liner.

Independently, `createProduct` must evict `productsBySku` — and the listing, search and browse
caches — for the same reason as C1.

If you deliberately want negative caching, cache it with a TTL measured in seconds, not the
product TTL.

**Underlying concept.** "Not found" is not a value. The moment you cache it with the same TTL as a
real value, you have declared that the absence of a row is as stable as its contents, which is
false — rows appear far more often than they change.

**Detecting it faster.** When a read is wrong and the database is right, `GET` the key. The stored
bytes here say `org.springframework.cache.support.NullValue` in plain text; that string is the
whole answer and it takes one command to see.

**Prevention.** Decide negative caching explicitly, per cache, and write it down in the
configuration next to the TTL. Make "does a create evict this cache?" part of the checklist for
adding any cache keyed by a natural key.

---

## C5 — the cache is bypassed by a call that never leaves the bean

**Report:** R5.

**Symptom.** `catalog:products::1` exists and is fresh, yet requesting the variants of product 1
still logs a database load of that product.

```
db loads of product 1 -- baseline 3, after a cached product read 3, after a variants read 4
```

The middle number proves the cache works when the call comes from outside. The last number proves
it does not when the call comes from inside.

**Root cause.** `CatalogService#variantsOf` calls `getProduct(productId)` on `this`:

```java
@Cacheable(cacheNames = "variants", key = "#productId")
@Transactional(readOnly = true)
public List<VariantResponse> variantsOf(Long productId) {
    ProductResponse product = getProduct(productId);
    ...
}
```

`@Cacheable` is implemented by a CGLIB proxy wrapped around the bean. Calls that arrive from
outside go through the proxy and hit the cache interceptor. A call from one method of the bean to
another is an ordinary virtual call on `this` — the proxy is not involved, so the interceptor never
runs, and neither the lookup nor the write to `products` happens.

Because the variant picker is on every product page, the busiest read of a product in the whole
system never populates or consults the product cache. Hence the flat hit rate on ops' dashboard.

**Location.** [`CatalogService.java:111`](src/main/java/com/lumen/catalog/service/CatalogService.java#L111).

**Why it happens in real systems.** This is the single most common Spring proxy mistake, and it is
invisible at the call site: the code reads exactly like a correct call, the IDE offers no warning,
and the method returns the right data. Only the *non-functional* behaviour is wrong. It survives
code review because there is nothing to see, and it survives unit tests because a unit test
instantiates the service with `new` — where there is no proxy at all and therefore no difference to
observe.

The same mechanism silently disables `@Transactional`, `@Async`, `@Retryable`, `@PreAuthorize` and
every other proxy-based annotation, which is why it is worth internalising once.

**The correct fix.** Move the shared read behind a boundary the proxy can see. In order of
preference:

1. Extract the cached read into its own bean and inject it:

   ```java
   @Service
   public class ProductReader {
       @Cacheable(cacheNames = "products", key = "#productId")
       @Transactional(readOnly = true)
       public ProductResponse getProduct(Long productId) { ... }
   }
   ```

   `CatalogService` then depends on `ProductReader`, and both the controller path and the variants
   path go through the proxy. This is the fix that also improves the design.

2. Inject the proxy into itself with `@Lazy CatalogService self` and call `self.getProduct(...)`.
   Works, but it is a workaround wearing a suit.

3. `((CatalogService) AopContext.currentProxy()).getProduct(...)` with
   `@EnableAspectJAutoProxy(exposeProxy = true)`. Avoid.

Do not "fix" it by making `variantsOf` read the product entity directly from the repository — that
removes the symptom on the dashboard while leaving the product page and the variant picker on two
different copies of the price.

**Underlying concept.** Spring's declarative annotations are implemented by proxies, and a proxy
only intercepts calls that cross the object boundary. `this.method()` is not a call that crosses
anything. Whenever you see an annotation that appears to be doing nothing, the first question is
whether the call came through the proxy.

**Detecting it faster.** Put a breakpoint on
`org.springframework.cache.interceptor.CacheAspectSupport#execute` and call the endpoint. If it
does not stop, the annotation is not in play. Alternatively, log
`AopUtils.isAopProxy(...)` or simply watch `redis-cli MONITOR` — a bypassed `@Cacheable` produces
no Redis traffic at all, which is very loud once you are looking for it.

**Prevention.** Treat self-invocation as a review checklist item: any call from one method of a
bean to another annotated method of the same bean is a defect. A single-responsibility split — one
bean per cached read — makes it structurally impossible.

---

## Why the test suite is green

`mvn -B test` passes with all five defects live, and that is the point.

- `CatalogMapperTest` constructs the mapper with `new`. No Spring, no proxy, no Redis — C5 cannot
  exist there, and neither can C2 or C4, because nothing is serialised.
- `ProductRepositoryTest` is a `@DataJpaTest` slice. The repository is correct; every one of these
  defects is in the layer above it or in the cache configuration beside it.

Four of the five defects live in the seam between the application and Redis, and the fifth lives in
the seam between the application and its own proxy. Unit tests are defined by not exercising seams.
The only tests that would have caught these are integration tests that run against a real Redis and
call each endpoint **twice**.
