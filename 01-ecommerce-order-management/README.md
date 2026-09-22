# Northwind Shop — Order Management Service

`shop-order-service` is the backend behind the Northwind storefront. It owns customer
accounts, the product catalogue, the shopping cart and the order history. It is a
conventional layered Spring Boot monolith: MySQL is the system of record, Redis holds the
carts (which are volatile and not worth a table) and caches product detail reads.

This is a **debugging lab project**. The application is feature-complete and starts
cleanly, but it does not behave correctly in every case. Work from `DEBUGGING_GUIDE.md`.
Do not open `SOLUTION.md` until you have finished.

---

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| Web | Spring MVC, REST/JSON |
| Security | Spring Security, HTTP Basic, BCrypt, stateless |
| Persistence | Spring Data JPA, Hibernate 6, MySQL 8 |
| Cart + cache | Redis 7 (`StringRedisTemplate` for carts, Spring Cache for product reads) |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.northwind.shop
├── cache/         CartKeys, CartRedisRepository  (Redis-backed cart storage)
├── config/        SecurityConfig, RedisCacheConfig, ShopProperties
├── controller/    Auth, Product, AdminProduct, Cart, Order
├── dto/           request and response payloads, PagedResponse, ApiError
├── entity/        Customer, Role, Category, Product, Order, OrderItem
├── exception/     domain exceptions + GlobalExceptionHandler
├── mapper/        entity to DTO translation
├── repository/    Spring Data JPA repositories
├── security/      AuthenticatedCustomer, CustomerDetailsService
└── service/       CustomerService, ProductService, CartService, OrderService, PricingCalculator
```

---

## Running it

### 1. Backing services

From the repository root:

```bash
docker compose -f infra/docker-compose.yml up -d mysql redis
```

MySQL comes up on `localhost:3307` with a `shopdb` database, Redis on `localhost:6380`.
Those host ports are deliberately shifted so the lab never collides with a MySQL or
Redis you already have installed on the standard ports.

### 2. Configuration

`src/main/resources/application.yml` holds placeholders (`YOUR_DATABASE_HOST`,
`YOUR_DATABASE_USER`, `YOUR_DATABASE_PASSWORD`, `YOUR_REDIS_HOST`) and reads every value
from the environment. The `local` profile — active by default — fills them in with the
credentials from `infra/docker-compose.yml`, so a plain `mvn spring-boot:run` works.

To point the service at your own database instead:

```bash
export SPRING_PROFILES_ACTIVE=default
export MYSQL_HOST=... MYSQL_USER=... MYSQL_PASSWORD=... REDIS_HOST=...
```

### 3. Start

```bash
mvn spring-boot:run
```

Schema and seed data are applied on every start from `schema.sql` and `data.sql`. Both are
idempotent, so restarting never duplicates rows and never wipes anything you changed.

### 4. Sign in

| Account | Password | Role |
|---|---|---|
| `priya.sharma@example.com` | `Password123!` | customer, has order history |
| `arjun.mehta@example.com` | `Password123!` | customer, has order history |
| `lena.fischer@example.com` | `Password123!` | customer, no orders yet |
| `sam.oduya@example.com` | `Password123!` | customer, account is `SUSPENDED` |
| `ops.admin@northwind.test` | `Admin123!` | back office (`ROLE_ADMIN`) |

Authentication is HTTP Basic on every call. In Postman: *Authorization → Basic Auth*.

```bash
curl -u priya.sharma@example.com:'Password123!' http://localhost:8080/api/orders
```

---

## Exercising it

A normal shopping session:

```bash
BASE=http://localhost:8080
AUTH='priya.sharma@example.com:Password123!'

curl -s "$BASE/api/products?size=5"

curl -s -u "$AUTH" -X POST "$BASE/api/cart/items" \
  -H 'Content-Type: application/json' \
  -d '{"productId": 4, "quantity": 2}'

curl -s -u "$AUTH" "$BASE/api/cart"

curl -s -u "$AUTH" -X POST "$BASE/api/orders" \
  -H 'Content-Type: application/json' \
  -d '{"shippingAddress": "12 Carter Road, Bandra West, Mumbai 400050, IN"}'

curl -s -u "$AUTH" "$BASE/api/orders"
```

Full endpoint contracts are in [API.md](API.md).

---

## Inspecting state while you debug

**MySQL** — Adminer is on <http://localhost:8081> (server `mysql`, user `labuser`,
database `shopdb`), or from the shell:

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass shopdb -e "select id, sku, stock_quantity from products order by id;"
```

**Redis** — carts and the product cache live here:

```bash
docker exec -it lab-redis redis-cli KEYS '*'
docker exec -it lab-redis redis-cli HGETALL 'shop:cart:1'
docker exec -it lab-redis redis-cli TTL 'products::1'
```

**SQL logging** is already at `DEBUG` for `org.hibernate.SQL`, so every statement Hibernate
issues appears in the application log. If you want parameter values too, add:

```
logging.level.org.hibernate.orm.jdbc.bind=TRACE
```

---

## Tests

```bash
mvn test
```

The suite covers pricing arithmetic, product mapping and a repository slice against H2.
It passes. That tells you the build is sound; it does not tell you the application is
correct.
