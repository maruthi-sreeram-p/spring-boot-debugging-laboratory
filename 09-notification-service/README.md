# Pulsesend — Notification Service

`notification-service` is the shared notification platform. Product teams call one API; it
renders a template, honours the recipient's channel preferences, queues the message, and a
per-channel consumer hands it to the provider.

**No real provider is contacted.** `ChannelDispatcher` simulates the email, SMS and in-app
providers in-process, and records everything they sent.

This is a **debugging lab project**. The application is feature-complete and starts
cleanly, but it does not behave correctly in every case. Work from `DEBUGGING_GUIDE.md`.
Do not open `SOLUTION.md` until you have finished.

> ### Read this before you start
> One of the defects in this project causes a message to be redelivered in a loop. The
> simulated provider times out over half a second, so the loop runs at roughly two attempts
> a second rather than thousands — but it **does not stop on its own**, and it keeps writing
> to the log. If you trigger it, stop it:
>
> ```bash
> # stop the application first, then drain the queue
> docker exec -it lab-rabbitmq rabbitmqctl purge_queue notifications.email
> ```
>
> Purging while the consumer is running leaves the in-flight (unacknowledged) copy behind,
> which is itself worth understanding.

---

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| Web | Spring MVC, REST/JSON |
| Security | Spring Security, HTTP Basic, BCrypt |
| Persistence | Spring Data JPA, Hibernate 6, PostgreSQL 16 |
| Messaging | RabbitMQ — topic exchange, per-channel queues, dead-letter exchange |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.pulsesend.notifications
├── channel/       ChannelDispatcher (simulated providers), DeliveryOutcome, DispatchedMessage
├── config/        SecurityConfig, RabbitTopologyConfig, PulsesendProperties
├── controller/    Notification, Preference, MessagingOps
├── dto/           request and response payloads, PagedResponse, ApiError
├── entity/        NotificationTemplate, NotificationPreference, Notification,
│                  DeliveryLogEntry, AppUser + enums
├── exception/     domain exceptions + GlobalExceptionHandler
├── mapper/        entity to DTO translation
├── messaging/     NotificationPublisher, NotificationConsumer, NotificationDeliveryHandler
├── repository/    Spring Data JPA repositories
├── security/      PulsesendUser, PulsesendUserDetailsService
└── service/       NotificationService, PreferenceService, TemplateRenderer
```

---

## The topology

```
POST /api/notifications
      │  render template, check the recipient's preference
      ▼
notifications.exchange  (topic)
      │  routing key: notify.<channel>.<templateCode>
      │        e.g. notify.email.welcome
      │             notify.email.order.shipped
      │             notify.sms.otp
      ├── notifications.email   ──► consumer ──► email provider
      ├── notifications.sms     ──► consumer ──► SMS provider
      └── notifications.inapp   ──► consumer ──► in-app provider

notifications.dlx (topic) ──► notifications.dlq
      anything a consumer cannot process is meant to land here
```

A routing key is `notify.<channel>.<templateCode>`, and **template codes contain dots** —
`welcome`, `order.shipped`, `password.reset`, `delivery.eta`. That is deliberate: the
segments are what let a future subscriber bind to `notify.email.order.#` and take every
order-related email.

Remember how topic matching works:

| Pattern | Matches |
|---|---|
| `*` | **exactly one** segment |
| `#` | zero or more segments |

So `notify.email.*` matches `notify.email.welcome` but not `notify.email.order.shipped`.

**A message published to a topic exchange that matches no binding is discarded silently.**
No error, no log line, nothing in any queue.

---

## The simulated providers

`ChannelDispatcher` decides what happens from the destination, the way a provider sandbox
uses reserved addresses:

| Destination contains | Behaviour |
|---|---|
| `outage` | the provider hangs for half a second and then fails — a transient outage |
| `bounce` | the provider accepts the request and rejects the recipient — a hard bounce |
| anything else | delivered |

Everything the providers actually sent is recorded in the **outbox**, which is the only
honest answer to "did the customer receive this":

```bash
curl -s -u 'msgops@pulsesend.test:Admin123!' localhost:8080/api/ops/outbox
```

When the outbox and the `notifications` table disagree, the outbox is right.

---

## Running it

### 1. Backing services

```bash
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
```

PostgreSQL on `localhost:5432` (`notificationdb`), RabbitMQ on `localhost:5672` with the
management UI at <http://localhost:15672> (`labuser` / `labpass`).

### 2. Configuration

`application.yml` holds placeholders (`YOUR_DATABASE_*`, `YOUR_RABBITMQ_*`) and reads every
value from the environment. The `local` profile — active by default — fills them in from
`infra/docker-compose.yml`. Exchange, queue and routing names live under
`pulsesend.messaging`.

### 3. Start

```bash
mvn spring-boot:run
```

Exchanges, queues and bindings are declared by the application at startup. Schema and seed
are applied on every start; both are idempotent.

### 4. Sign in

| Account | Password | Role |
|---|---|---|
| `product@pulsesend.test` | `Password123!` | `ROLE_PRODUCT` — request notifications, read preferences |
| `msgops@pulsesend.test` | `Admin123!` | `ROLE_MSGOPS` — broadcasts, requeue, the outbox |

Seeded templates: `welcome`, `password.reset`, `order.shipped`, `order.delivered`,
`payment.receipt` (EMAIL), `otp`, `delivery.eta` (SMS), `inbox.mention` (IN_APP), and a
retired `legacy.newsletter`.

Seeded preferences: `CUST-1002` has opted out of SMS; `CUST-1003` has opted out of EMAIL.

---

## Exercising it

```bash
BASE=http://localhost:8080
PROD='product@pulsesend.test:Password123!'
OPS='msgops@pulsesend.test:Admin123!'

curl -s -u "$OPS" -X POST "$BASE/api/ops/outbox/reset"

curl -s -u "$PROD" -X POST "$BASE/api/notifications" \
  -H 'Content-Type: application/json' \
  -d '{"recipientRef":"CUST-1001","destination":"asha.menon@example.com",
       "templateCode":"welcome","payload":"{\"name\":\"Asha\",\"brand\":\"Northwind\"}"}'

curl -s -u "$PROD" "$BASE/api/notifications?recipientRef=CUST-1001"
curl -s -u "$OPS" "$BASE/api/ops/outbox"
curl -s -u "$PROD" "$BASE/api/preferences/CUST-1003"
```

Full contracts are in [API.md](API.md).

---

## Inspecting state while you debug

**The three views, in this order:**

1. What did the providers send? `GET /api/ops/outbox`
2. What does the database say? `select * from notifications`
3. What is in the broker? `rabbitmqctl list_queues`

**RabbitMQ:**

```bash
docker exec -it lab-rabbitmq rabbitmqctl list_queues name messages consumers
docker exec -it lab-rabbitmq rabbitmqctl list_bindings source_name routing_key destination_name
docker exec -it lab-rabbitmq rabbitmqctl list_exchanges
```

The management UI at <http://localhost:15672> is worth using here. **Exchanges →
notifications.exchange → Publish message** lets you send a routing key by hand and shows
whether it was routed; the same page lists the bindings so you can compare them against the
keys the publisher logs.

**PostgreSQL:**

```bash
docker exec -it lab-postgres psql -U labuser -d notificationdb \
  -c "select notification_ref, channel, template_code, status, attempts from notifications order by id;"
```

Notifications the dashboard calls sent that no provider ever accepted, and notifications
delivered more than once, are both worth a query:

```sql
SELECT status, count(*) FROM notifications GROUP BY status;
SELECT notification_id, count(*) FROM delivery_log GROUP BY notification_id HAVING count(*) > 1;
```

---

## Tests

```bash
mvn test
```

The suite covers template rendering and a repository slice against H2. It passes, and it
starts no broker.
