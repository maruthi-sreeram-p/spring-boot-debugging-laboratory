# 11 — Lumen Retail Product Catalogue

A read-heavy product catalogue service for an electronics retailer. The storefront reads the
catalogue constantly; merchandising edits it a handful of times a day. Everything the storefront
reads is cached in Redis.

**Difficulty:** 8 / 10 · **Theme:** caching, cache invalidation, cache key design, serialisation

---

## Stack

| Concern        | Choice |
| -------------- | ------ |
| Runtime        | Java 21, Spring Boot 3.3.5 |
| Persistence    | Spring Data JPA / Hibernate 6 on PostgreSQL 16 |
| Cache          | Redis 7 via Spring Cache (`@Cacheable` / `@CacheEvict`) and `RedisCacheManager` |
| Security       | Spring Security 6, HTTP Basic, BCrypt, stateless |
| Build / test   | Maven, JUnit 5, AssertJ |

Storefront reads under `/api/catalog/**` are public. `/api/merch/**` and `/api/ops/**` require
`ROLE_MERCH`.

---

## Running it

The lab infrastructure lives in [`../infra/docker-compose.yml`](../infra/docker-compose.yml).

```bash
docker compose -f ../infra/docker-compose.yml up -d lab-postgres lab-redis
```

Then, from this directory:

```bash
mvn -B -DskipTests package && java -jar target/product-catalog-service-11.5.3.jar --spring.profiles.active=local
```

The `local` profile points at `localhost:5432` (PostgreSQL) and `localhost:6380` (Redis) with the
lab credentials. `application.yml` itself carries placeholders — `YOUR_DATABASE_HOST`,
`YOUR_DATABASE_USER`, `YOUR_DATABASE_PASSWORD`, `YOUR_REDIS_HOST` — so set those (or the
`POSTGRES_*` / `REDIS_*` environment variables) if you run it anywhere else.

`schema.sql` and `data.sql` are applied on every boot and are idempotent.

### Accounts

| Username | Password | Role |
| -------- | -------- | ---- |
| `storefront@lumen.test` | `Password123!` | `ROLE_STOREFRONT` |
| `merch@lumen.test` | `Admin123!` | `ROLE_MERCH` |

---

## Inspecting the cache

Two ways in. From the shell:

```bash
docker exec lab-redis redis-cli KEYS 'catalog:*'
```

```bash
docker exec lab-redis redis-cli GET 'catalog:products::1'
```

Or through the service, which is handy when you want to see the cache from the application's own
point of view (both require the merchandising account):

| Method | Path | Purpose |
| ------ | ---- | ------- |
| `GET` | `/api/ops/cache/keys?pattern=catalog:*` | every key that matches |
| `GET` | `/api/ops/cache/summary` | key count per cache |
| `DELETE` | `/api/ops/cache` | drop every key |

`GET /actuator/caches` lists the configured caches and their manager.

Caches and their time to live, as configured in `RedisCacheConfig`:

| Cache | Holds | TTL |
| ----- | ----- | --- |
| `products` | one product, keyed by id | 30 min |
| `productsBySku` | one product, keyed by SKU | 30 min |
| `variants` | the variants of one product | 30 min |
| `categoryListing` | every active product in one category | 15 min |
| `productSearch` | one page of search results | 5 min |
| `productBrowse` | one browse rail | 5 min |

The 30-minute TTL matters: several of the symptoms in this project heal on their own if you walk
away and come back, which is exactly what makes them annoying in production.

---

## Where to start

Read [`API.md`](API.md) for the endpoints, then [`DEBUGGING_GUIDE.md`](DEBUGGING_GUIDE.md) for the
reports that came in from the business.

`SOLUTION.md` exists. Do not open it until you have a diagnosis you are willing to defend.

---

## Tests

```bash
mvn -B test
```

They pass. That is not evidence of anything.
