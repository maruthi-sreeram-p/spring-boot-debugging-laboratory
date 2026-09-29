# Debugging guide — Lumen Retail Product Catalogue

## The business

Lumen Retail sells laptops, audio gear, monitors and accessories. The catalogue service is the
single source of product data for the storefront, the mobile app and the in-store kiosks.

The traffic shape is lopsided and it drives every design decision in this service. Reads outnumber
writes by roughly five orders of magnitude: the storefront renders the same handful of category
pages and product pages millions of times a day, while merchandising edits a product maybe twenty
times a day. So everything the storefront reads goes through Redis, and only the back office
writes.

Caches were added over about eighteen months, one at a time, by different people, as each page got
slow enough to complain about. The product page came first. The category pages came later. Search
and the home-page browse rails came last, during a peak-season crunch.

## What the system does

- `CatalogService` holds every read path the storefront uses. Each one is `@Cacheable`.
- `MerchandisingService` holds the two write paths. A write is expected to leave the caches in a
  state the storefront can trust.
- `RedisCacheConfig` names the caches, gives each a time to live, and picks the serialisers.
- Cache keys are prefixed `catalog:` and Spring appends `<cacheName>::<key>`.

Time to live matters here. Product entries live 30 minutes, category listings 15, search and
browse 5. Anything wrong tends to heal on its own if you wait long enough, which is part of why
these reports took so long to pin down.

## Reports from the business

These arrived over two weeks. They are written the way they were reported, not the way an engineer
would write them.

---

### R1 — "The price on the category page is wrong"

> Merchandising ran the March repricing. Every product page shows the new price straight away —
> we checked, the product pages are perfect. But the category pages still show the old prices, and
> so do the brand rails on the home page. Customers are adding things to the basket at one price
> and being charged another.
>
> The strange part: if you go and make a cup of tea and come back, it has fixed itself. So by the
> time anyone from engineering looks at it, it looks fine.

Renames behave the same way. A product renamed in the back office keeps its old name on the
category page for a while.

---

### R2 — "Search works once and then dies"

> Type "monitor" into the search box. You get results. Hit refresh. 500.
>
> Wait five minutes and it works again — once. Then it is broken again.
>
> On the QA box it was fine all afternoon, so we nearly closed this as unreproducible. Then
> somebody pointed out QA had Redis switched off.

The response is a generic `500` with no useful message. Whatever went wrong is in the server log.

---

### R3 — "The home page rails are showing the wrong products"

> The home page has a row of rails: "Meridian laptops", "Lumen accessories", "Lumen displays" and
> so on. Each rail is a saved filter — a brand, a category, or both.
>
> The "Lumen displays" rail is showing keyboards and mice. The "Lumen accessories" rail is correct.
> Nothing errors, the page renders, the products are real products, they are just the wrong ones.
>
> It depends which rail loads first after a deploy, which is why it moves around.

---

### R4 — "New products are invisible on the storefront"

> Merchandising created the new USB-C cable at 10:02. The kiosk team scanned the QR code at 10:04
> and got "product not found". Back office shows it. The database shows it. The product page for it
> works if you have the numeric link.
>
> By the time we escalated it at about 10:40 it was working, so support closed it as a caching
> blip. It has happened four times now and always with a brand new SKU.
>
> One thing that might matter: the kiosk team have a habit of scanning the QR label before
> merchandising has finished creating the product, because the labels are printed first.

---

### R5 — "The product cache is not doing anything"

> Ops put a dashboard on the Redis hit rate. The `products` cache barely gets any hits, even though
> the product page is the single most requested page on the site and the product page always reads
> the product.
>
> Database load on the `products` table is roughly what it was before the cache existed.

---

## How to work on this

Five reports. They are not necessarily five separate causes, and they are not necessarily one.

The tools that will actually help here:

- `docker exec lab-redis redis-cli KEYS 'catalog:*'` — what keys exist, and what shape they are.
  The shape of a key is often the whole story.
- `docker exec lab-redis redis-cli GET '<key>'` — what is actually stored. Read the bytes, not
  what you assume is in there.
- `docker exec lab-redis redis-cli MONITOR` — every command the application sends to Redis, live.
  Run it in one terminal and hit an endpoint in another.
- `docker exec lab-redis redis-cli TTL '<key>'` — how long a wrong answer is going to keep being
  given.
- `GET /api/ops/cache/summary` and `GET /api/ops/cache/keys` — the same view, from inside the app.
- `DELETE /api/ops/cache` — a clean slate. Use it between experiments; a warm cache from a previous
  experiment will lie to you.
- The application log at `target/app.log`. `com.lumen.catalog` is at `DEBUG`, and the service logs
  a line every time it goes to the database. Compare that against what you think should have been
  a cache hit.
- `org.hibernate.SQL` is at `DEBUG` too, so you can see exactly which queries ran.
- A breakpoint in `org.springframework.cache.interceptor.CacheAspectSupport#execute` will show you
  the key Spring computed, before Redis ever sees it.
- `psql` against `catalogdb`, to establish what is actually true.

A useful discipline in this project: for every symptom, decide first whether the *database* is
right. If the database is right and the API is wrong, the bug is between them, and there is only
one thing between them.

---

## Hints

Read one. Stop. Go back to the terminal. Only come back for the next one when you are genuinely
stuck.

### Level 1 — the shape of the problem

1. Three of these reports are about a cached answer being **wrong**, and they are wrong in
   different ways. One is about a cached answer being **unreadable**. One is about the cache not
   being **used**.
2. For every report, the database is correct. Confirm that first, with `psql`, so you can stop
   suspecting the repository layer.
3. Every one of these five behaves differently on a cold cache than on a warm one. Get in the habit
   of `DELETE /api/ops/cache` before each experiment, and of running each experiment twice.

### Level 2 — narrowing

4. R1: a write path exists, and it does say something about caches. Read what it says, then list
   every cache that holds data derived from the product being written. Compare the two lists.
5. R2: the first request after a flush succeeds and the second fails. That tells you which
   direction is broken — writing to the cache, or reading back from it. Look at the stack trace and
   note which class Jackson is complaining about.
6. R3: look at the Redis key for a browse rail. There are two inputs to a rail. How many of them
   appear in the key?
7. R4: `redis-cli GET` the key for the missing SKU *before* the product is created. Something is
   stored there. Work out what it is and where it came from.
8. R5: the product page reads the product and the variant picker reads the product. Only one of
   them results in a `products` key. Find the difference between the two paths.

### Level 3 — the mechanism

9. R1: Spring evicts exactly what you name and nothing else. Derived views are not related to their
   source in any way Spring can see.
10. R2: the thing being cached is an interface implementation from Spring Data, not a class you
    wrote. Ask whether Jackson can reconstruct it. There is an unused class in the `dto` package
    that suggests what the original author intended.
11. R3: when the cache key is an object rather than a string, Spring Data Redis has to turn it into
    a string somehow. Find out which method it calls, and read that method.
12. R4: Spring Cache stores a marker for "the method returned nothing", and by default the Redis
    cache is allowed to store it. Check `RedisCacheConfig` for what it does *not* say.
13. R5: `@Cacheable` is implemented by a proxy wrapped around the bean. Ask what happens to a call
    that never leaves the bean.

### Level 4 — where to look

14. R1 lives in `MerchandisingService`, on the annotation above `updateProduct`.
15. R2 lives in the return type of `CatalogService#search`.
16. R3 lives in `BrowseFilter`, in a method that has nothing to do with caching.
17. R4 lives in two places at once: the `orElse` at the end of `CatalogService#getProductBySku`,
    and a builder call that is missing from `RedisCacheConfig#baseConfiguration`.
18. R5 lives on a single line inside `CatalogService#variantsOf`.

---

Do not tell me why. Reproduce it, trace it, and then tell me what you found.

When you think you have one, give me:

1. the symptom, precisely,
2. the root cause,
3. the evidence — the key, the stored value, the log line, the stack frame,
4. your proposed fix.

I will tell you whether the reasoning holds.
