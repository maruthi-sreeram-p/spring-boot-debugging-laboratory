# SOLUTION — Spicebox Food Ordering Service

> **Answer key. Do not read this until you have finished investigating.**

Five defects. Three live in the asynchronous half of the system and never reach an HTTP
response; two are ordinary synchronous validation gaps.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| F1 | SPX-520 | The event is published before commit, so the async listener cannot see the order | `service/OrderService.java` + `service/KitchenIntakeListener.java` |
| F2 | SPX-533 | The dispatch binding key and the published routing key do not match | `config/RabbitConfig.java` |
| F3 | SPX-527 | The async confirmation reads `SecurityContextHolder`, which is empty on a pool thread | `service/NotificationService.java` |
| F4 | SPX-541 | The status endpoint accepts any status from any status | `service/OrderStatusService.java` |
| F5 | SPX-548 | Only the first item's restaurant is used; the rest are never checked | `service/OrderService.java` |

---

## F1 — Kitchen intake never sees the order

### Symptom
Every order stays `PLACED`. Checkout returns `201`. The log contains, on a pool thread:

```
Unexpected exception occurred invoking async method ...
ResourceNotFoundException: Order not found: 4
```

for an order id that is plainly in the database.

### Root cause
`OrderService.placeOrder` is `@Transactional`. Part way through, it publishes a Spring
application event:

```java
FoodOrder saved = foodOrderRepository.save(order);
applicationEventPublisher.publishEvent(new OrderPlacedEvent(saved.getId(), saved.getOrderCode()));
```

`KitchenIntakeListener` consumes it with `@Async @EventListener @Transactional`, so it runs
**on a different thread, in its own transaction, immediately** — while `placeOrder` is still
executing and its transaction is still open.

The order row exists only inside the uncommitted transaction. The listener's transaction
cannot see it (MySQL/InnoDB defaults to `REPEATABLE READ`; the same happens under `READ
COMMITTED`), `findById` returns empty, and `orElseThrow` fires.

The exception goes nowhere useful. A `@Async` method returning `void` has no caller to
throw to, so Spring's `SimpleAsyncUncaughtExceptionHandler` logs it and the request that
caused it has already returned `201`.

This is a race, and the listener essentially always wins it — the publishing thread still
has mapping and commit to do. Under a slower pool or a faster commit it would sometimes
succeed, which is worse: a defect that works one time in twenty is harder to characterise
than one that never works.

### Exact location
`src/main/java/com/spicebox/ordering/service/OrderService.java` (the `publishEvent` call
inside the transaction) together with `@EventListener` in
`src/main/java/com/spicebox/ordering/service/KitchenIntakeListener.java`.

### Correct fix
Make the listener wait for the commit:

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onOrderPlaced(OrderPlacedEvent event) { ... }
```

Three details worth getting right:

1. `@TransactionalEventListener` defaults to `AFTER_COMMIT`, but state it explicitly — the
   other phases exist and the default is not obvious to the next reader.
2. `REQUIRES_NEW` matters. An `AFTER_COMMIT` listener runs while the original transaction is
   still technically bound to the thread but no longer writable; without a new transaction
   the listener's writes are silently discarded. (That is the same failure mode as
   project 02's `readOnly` defect, reached by a different road.)
3. If the event is published but the transaction rolls back, the listener never runs at all
   — which is correct.

The larger point: **an event carrying only an id is a promise that the id will be readable
by the time it is handled.** If you want the listener to be independent of commit timing,
put the data in the event instead of the id. Both designs are valid; mixing them is not.

### Affected components
`OrderService`, `KitchenIntakeListener`, `FoodOrderRepository`, the intake queue, every
restaurant tablet.

### Underlying concept
**Publishing an event is not the same as the event being safe to handle.** Inside a
transaction, nothing you have written is visible to anyone else yet. Any handler that reads
the database must run after commit, and Spring gives you
`@TransactionalEventListener(AFTER_COMMIT)` for exactly that.

The second concept is **`@Async void` swallows failures**. Everything you rely on to notice
a problem — the HTTP status, the caller's try/catch, the test assertion — is on the other
side of a thread boundary. The only channel left is the log, which is why the first move
for any "the background step did nothing" report is to read it.

### Why this is realistic
Publishing an event straight after `save()` reads as correct and is what most examples show.
`@EventListener` and `@TransactionalEventListener` differ by one word, and the plain one is
the one people know. The combination with `@Async` makes the race reliable rather than
occasional, which is the only reason this was noticed at all — a synchronous
`@EventListener` would have run inside the same transaction and worked fine, which is
exactly why it survives review.

### Detecting it faster next time
- **Read the log first for any asynchronous step.** The exception here names the problem in
  one line.
- "Not found" for a row you can see in the database means one of two things: you are
  querying a different database, or you are querying before a commit. Check the second.
- Log or breakpoint the commit boundary against the listener entry:
  `logging.level.org.springframework.orm.jpa.JpaTransactionManager=DEBUG` shows you the
  ordering directly.

### Prevention
- Default to `@TransactionalEventListener` for any listener that touches the database.
- An integration test that places an order and asserts the status reaches `ACCEPTED` within
  a couple of seconds — an `Awaitility` one-liner.
- Alert on the depth of the intake queue. A background step that stops working needs a
  monitor, because no user will report it as an error.

---

## F2 — The dispatch queue never receives anything

### Symptom
Orders sit at `READY_FOR_PICKUP`. The `delivery.dispatch` queue has a healthy consumer and
has never held a message. The `analytics.orders` queue, bound to the same exchange, receives
everything.

### Root cause
The publisher takes its routing keys from configuration:

```java
private String placedRoutingKey = "order.placed";
private String readyRoutingKey  = "order.ready-for-pickup";
```

The bindings are declared with string literals:

```java
BindingBuilder.bind(analyticsQueue).to(foodOrdersExchange).with("order.placed");
BindingBuilder.bind(dispatchQueue).to(foodOrdersExchange).with("order.ready");
```

`order.placed` happens to match, so analytics works. `order.ready-for-pickup` does not match
the `order.ready` binding — on a topic exchange, `.` is a segment separator and
`order.ready` matches only the exact two-segment key. The message is **discarded silently**:
a topic exchange with no matching binding drops the message, and unless the publisher sets
the mandatory flag with a return callback, nobody is told.

The evidence is in two places you have to look at together:

```
grep "routing key" app.log      →  order.ready-for-pickup
rabbitmqctl list_bindings       →  order.ready
```

### Exact location
`src/main/java/com/spicebox/ordering/config/RabbitConfig.java`, the `dispatchBinding` bean.

### Correct fix
Bind with the same value the publisher uses, from the same source:

```java
@Bean
public Binding dispatchBinding(Queue dispatchQueue, TopicExchange foodOrdersExchange) {
    return BindingBuilder.bind(dispatchQueue).to(foodOrdersExchange)
            .with(properties.getMessaging().getReadyRoutingKey());
}
```

Do the same for `analyticsBinding` even though it currently works — a literal that happens
to agree is a coincidence, not a design.

Two operational notes:

- **RabbitMQ will not change an existing binding.** The old `order.ready` binding stays
  until it is deleted. After deploying, remove it (management UI, or `rabbitmqctl`), or the
  queue ends up bound to both keys.
- Turn on publisher returns so a future mismatch is loud rather than silent:

```yaml
spring.rabbitmq.template.mandatory: true
```

with a `ReturnsCallback` that logs. An unroutable message then produces a log line instead
of nothing.

### Affected components
`RabbitConfig`, `OrderEventPublisher`, `DispatchListener`, `SpiceboxProperties`, the
delivery tracking screen.

### Underlying concept
**On a topic exchange, delivery is decided entirely by the match between the publisher's
routing key and the binding keys — and a non-match is not an error.** Messaging fails open
into silence. There is no foreign key, no compile error, no 404. The message simply ceases
to exist.

That is why **routing keys must come from one source of truth** shared by producer and
binding, and why publisher confirms and returns exist. In a larger system the same principle
extends to a schema registry: the contract between two sides of a broker has to be
checkable somewhere, because the broker will not check it for you.

### Why this is realistic
Someone renamed the routing key from `order.ready` to the more descriptive
`order.ready-for-pickup` in `application.yml`, where it is a config value, and did not
notice the literal buried in a binding bean. The publisher picked up the new value
immediately; the binding did not. Both files look right in isolation, and the analytics
path — which is the one people test — kept working.

Silent discard makes it worse. A consumer that throws leaves a stack trace; a message that
was never routed leaves nothing at all.

### Detecting it faster next time
- **When one consumer works and another does not on the same exchange, diff the routing
  keys.** `list_bindings` next to a grep of the publisher log is a thirty-second check.
- Use the management UI's **Publish message** form to send a key by hand and watch whether
  it lands. It tells you immediately whether the problem is routing or consuming.
- Learn the topic matching rules: `*` is one segment, `#` is zero or more. `order.ready`
  does not match `order.ready-for-pickup`, but `order.#` would match both.

### Prevention
- Single source for exchange, routing key and queue names; never a literal in a binding.
- `mandatory: true` plus a returns callback in every environment.
- An integration test with a real broker (Testcontainers) that publishes and asserts the
  message arrives in the expected queue.

---

## F3 — Confirmations are never recorded

### Symptom
No rows appear in `order_notifications` for new orders. Orders are otherwise correct. The
log shows, on a pool thread:

```
NullPointerException: Cannot invoke "org.springframework.security.core.Authentication.getName()"
because the return value of "...SecurityContext.getAuthentication()" is null
```

### Root cause
```java
@Async
@Transactional
public void sendOrderConfirmation(Long orderId) {
    String recipient = SecurityContextHolder.getContext().getAuthentication().getName();
    ...
}
```

`SecurityContextHolder` uses a `ThreadLocal` by default
(`MODE_THREADLOCAL`). The authentication is populated by the security filter chain on the
**request** thread. `@Async` moves this method onto a pool thread, where the holder returns
an empty context and `getAuthentication()` is `null`.

The NPE is thrown before anything is written, so no notification row is created, and — being
an async `void` — it never reaches the caller. Checkout returns `201` with no indication
that anything failed.

The developer who "cannot see anything wrong with it" is right: the method is correct on the
thread it was written for.

### Exact location
`src/main/java/com/spicebox/ordering/service/NotificationService.java`, the first line of
`sendOrderConfirmation`.

### Correct fix
The robust fix removes the dependency entirely. The order already knows its customer:

```java
FoodOrder order = foodOrderRepository.findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
String recipient = order.getCustomer().getEmail();
```

This is better than propagating the context, because the correct recipient is a property of
the *order*, not of whoever happened to trigger the code. When operations later place an
order on a customer's behalf, the context-based version would mail the wrong person while
still "working".

If you genuinely need the caller's identity on an async thread, propagate it explicitly:

```java
SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
```

or wrap the executor with `DelegatingSecurityContextAsyncTaskExecutor`. Note that
`MODE_INHERITABLETHREADLOCAL` only helps for threads created after the context is set, which
is not true of a pooled executor — the delegating executor is the correct mechanism.

Also note this method depends on F1's fix: once it runs after commit it will find the order.
Today it fails before it even gets that far.

### Affected components
`NotificationService`, `OrderService`, `order_notifications`, every customer confirmation.

### Underlying concept
**Thread-local state does not cross a thread boundary.** The security context, the
transaction, the `EntityManager`, MDC logging context and request-scoped beans are all
thread-bound. `@Async` is precisely a thread boundary, and every one of them is empty on the
other side.

The design lesson underneath: **pass what the method needs as arguments, rather than
reaching for ambient context.** A method that takes `orderId` and reads everything else from
the order has no hidden dependency on how it was invoked. Ambient context is convenient and
is the first thing to break when the calling context changes.

### Why this is realistic
`SecurityContextHolder.getContext().getAuthentication().getName()` is idiomatic Spring
Security and appears in thousands of codebases. It is correct in a controller, correct in a
service called from a controller, and wrong the moment `@Async` is added — and `@Async` is
usually added later, by someone optimising checkout latency, who has no reason to read the
body of the method they are moving off the request thread.

### Detecting it faster next time
- **An NPE on a thread named `task-N` is a thread-local problem until proved otherwise.**
  The thread name in the log is the clue.
- When adding `@Async` to an existing method, audit its body for `SecurityContextHolder`,
  `RequestContextHolder`, `TransactionSynchronizationManager`, MDC and request-scoped beans.
- Grep for `SecurityContextHolder` in any class that also mentions `@Async` or `@Scheduled`.

### Prevention
- Pass identity in; do not read it from ambient state in service methods.
- If you must propagate, configure `DelegatingSecurityContextAsyncTaskExecutor` once,
  centrally.
- Give `@Async` methods a real exception handler: implement `AsyncUncaughtExceptionHandler`
  via `AsyncConfigurer` so failures are alerted rather than merely logged.

---

## F4 — Any status from any status

### Symptom
Operations moved an order `PLACED → DELIVERED → PREPARING → READY_FOR_PICKUP` and every call
returned `200`. The customer's tracking screen showed the order going backwards.

### Root cause
```java
OrderStatus target;
try {
    target = OrderStatus.valueOf(requestedStatus.trim().toUpperCase());
} catch (IllegalArgumentException ex) {
    throw new IllegalArgumentException(requestedStatus + " is not a known order status");
}

OrderStatus previous = order.getStatus();
order.setStatus(target);
```

The only validation is that the string names an enum constant. Nothing checks that the move
is legal from the current status. `IllegalStatusChangeException` exists and is mapped to
`409` in the exception handler — it is simply never thrown from here.

`markOutForDelivery`, a few lines below in the same class, does it correctly:

```java
if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
    throw new IllegalStatusChangeException(order.getStatus().name(), "OUT_FOR_DELIVERY");
}
```

One method in the class understands the rule and the other does not.

### Exact location
`src/main/java/com/spicebox/ordering/service/OrderStatusService.java`, `changeStatus`.

### Correct fix
Put the track in one place and check it:

```java
private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
        OrderStatus.PLACED,           Set.of(OrderStatus.ACCEPTED, OrderStatus.CANCELLED, OrderStatus.REJECTED),
        OrderStatus.ACCEPTED,         Set.of(OrderStatus.PREPARING, OrderStatus.CANCELLED, OrderStatus.REJECTED),
        OrderStatus.PREPARING,        Set.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED),
        OrderStatus.READY_FOR_PICKUP, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED),
        OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED),
        OrderStatus.DELIVERED,        Set.of(),
        OrderStatus.CANCELLED,        Set.of(),
        OrderStatus.REJECTED,         Set.of());

if (!ALLOWED.getOrDefault(previous, Set.of()).contains(target)) {
    throw new IllegalStatusChangeException(previous.name(), target.name());
}
```

Then have `markOutForDelivery` use the same map instead of its own hand-written check, so
there is one definition of the track.

Worth considering: an order-status history table. When a status is wrong, the first question
is always "who set it and when", and an in-place update cannot answer that.

### Affected components
`OrderStatusService`, `OpsController`, `DispatchListener`, the customer tracking screen.

### Underlying concept
**An enum is a set of names, not a state machine.** Validating that a value parses says
nothing about whether the transition is legal. Any entity with a lifecycle needs its
transitions declared somewhere explicit — a map, a table, or a library — and checked on
every write path.

The tell here is a defined-but-unused exception. `IllegalStatusChangeException` exists and is
wired into the handler; that is strong evidence that someone intended the check and it was
lost.

### Why this is realistic
The method looks defensive: it parses carefully, it handles the bad-value case, it logs the
before and after. It does everything except the one thing that matters. Endpoints like this
often start as an internal tool for operations — where "trust the operator" is the implicit
model — and become load-bearing once dispatch and the customer app start depending on the
status.

### Detecting it faster next time
- **Read the contract, then count the rules in the code.** `API.md` lists four constraints;
  the method implements one.
- An exception class that nothing throws is a missing check. Grep for the type.
- Test lifecycles by walking them: every legal step, then every illegal one.

### Prevention
- Declare transitions as data and enforce them centrally, on every path.
- A parameterised test over the full transition matrix — legal moves succeed, everything
  else returns `409`.
- Record status history so a wrong value is always attributable.

---

## F5 — An order can span two restaurants

### Symptom
A basket containing a Curry Leaf dosa and a Napoli Corner pizza is accepted as one order,
assigned to Curry Leaf. Curry Leaf makes the dosa; nobody makes the pizza; the customer pays
for both.

### Root cause
The restaurant is chosen from the first item only:

```java
MenuItem firstItem = menuItemRepository.findById(request.getItems().get(0).getMenuItemId())...;
Restaurant restaurant = firstItem.getRestaurant();
```

The loop that follows validates each item's *availability* but never compares its restaurant
with the one already chosen:

```java
if (!menuItem.isAvailable()) {
    throw new OrderRuleException(menuItem.getName() + " is not available at the moment");
}
```

So an item from any restaurant is accepted as long as it is available, and the order is
filed under whichever restaurant happened to be first in the array.

### Exact location
`src/main/java/com/spicebox/ordering/service/OrderService.java`, the item loop in
`placeOrder`.

### Correct fix
```java
if (!menuItem.getRestaurant().getId().equals(restaurant.getId())) {
    throw new OrderRuleException("An order cannot mix restaurants: "
            + menuItem.getName() + " is from " + menuItem.getRestaurant().getName()
            + ", not " + restaurant.getName());
}
```

A cleaner shape resolves every item up front, derives the distinct restaurants, and rejects
anything other than exactly one — which removes the asymmetry between "the first item" and
"the rest" entirely:

```java
List<MenuItem> items = menuItemRepository.findByIdIn(requestedIds);
Set<Long> restaurantIds = items.stream().map(i -> i.getRestaurant().getId()).collect(toSet());
if (restaurantIds.size() != 1) { throw new OrderRuleException("An order cannot mix restaurants"); }
```

That also fixes an N+1: the current loop issues one `findById` per line.

### Affected components
`OrderService`, `PlaceOrderRequest`, `food_orders`, `order_items`, the restaurant tablets.

### Underlying concept
**When one element of a collection determines a property of the whole, every other element
must be checked against it.** "First item wins" is a decision about the aggregate made from a
sample of one. The safe formulation is to derive the property from the whole collection and
assert it is singular.

More generally: **a validation loop that checks some properties creates the impression that
all properties are checked.** The loop here is not missing — it is present and it validates
the wrong thing, which is far more convincing than no loop at all.

### Why this is realistic
The UI only lets a customer add items from one restaurant at a time, so this is
unreachable through the app. It surfaces through the API directly, or through a client bug,
or when a customer switches restaurants and the front end fails to clear the basket — which
is exactly the kind of thing that ships in a mobile release and arrives as a mysterious
backend ticket.

### Detecting it faster next time
- **Any request containing a list: send a list whose elements disagree.** Two items from two
  restaurants, two lines with different currencies, two addresses in different countries.
  It takes one request and finds a whole class of defect.
- When an aggregate has a property derived from its parts, look for where it is derived and
  check whether the derivation consults every part.
- A database query for violations of the invariant — the one in the README — tells you
  immediately whether it has already happened in production.

### Prevention
- Derive aggregate properties from the whole collection, never from `get(0)`.
- Validate the collection as a unit before processing its elements.
- A check that reconciles `order_items.menu_item.restaurant_id` against
  `food_orders.restaurant_id`, run as a test and as a monitor.

---

## How these interact

**F1 hides F3.** With intake broken, an investigator watching the order status sees nothing
move and stops there. The missing confirmations are a *separate* async failure with a
separate exception in the same log — and once F1 is fixed, F3 is still there, which is the
"fixing one thing exposes another" shape. Both exceptions are in the log from the start if
you read it rather than watching the status.

**F1 and F3 share one root discipline** — async work does not inherit the caller's world.
F1 loses transaction visibility; F3 loses the security context. Same boundary, two different
things dropped crossing it.

**F2 is masked by F4.** Because operations can set any status by hand, somebody will
eventually set `OUT_FOR_DELIVERY` manually and the dispatch problem will look intermittent
rather than total. Fix F4 first and F2 becomes unmistakable: orders now genuinely cannot
leave `READY_FOR_PICKUP` by any route.

**F5 is independent**, and it is the only defect here that a customer can see immediately.

**Suggested fix order:** F5 (synchronous, isolated, stops bad data entering), F4 (restores
the invariant the rest of the system assumes), F2 (one line plus a binding to delete), F1
(the async transaction boundary), F3 (which only becomes observable once F1 is fixed).

---

## What the test suite tells you

`mvn test` passes with all five defects live.

`OrderingMapperTest` maps an order that was constructed by hand, so it never exercises
`placeOrder` and never sees the restaurant rule. `FoodOrderRepositoryTest` proves the intake
query finds `PLACED` orders — which it does; the orders really are all `PLACED`, and the
query is the one part of that pipeline that works.

Neither test starts an async executor, a broker or a security filter chain. Every defect in
this project needs at least one of those three to be visible, and that is the point: the
asynchronous half of an application is invisible to tests that do not run it asynchronously,
and invisible to HTTP responses by construction. What is left is the log, the database and
the broker — which is why the hints in this project push you at all three.
