# SOLUTION — Pulsesend Notification Service

> **Answer key. Do not read this until you have finished investigating.**

Five defects. Three are RabbitMQ semantics, two are ordinary application logic — and the two
kinds interact.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| N1 | PS-701 | The email binding uses `*` where the routing keys need `#` | `config/RabbitTopologyConfig.java` |
| N2 | PS-708 | Queues have no dead-letter target and rejected messages are requeued forever | `config/RabbitTopologyConfig.java` + `application.yml` |
| N3 | PS-715 | The consumer never checks whether the notification was already delivered | `messaging/NotificationDeliveryHandler.java` |
| N4 | PS-722 | The status is set to `SENT` before the provider is called, and never corrected | `messaging/NotificationDeliveryHandler.java` |
| N5 | PS-730 | The broadcast path does not consult preferences | `service/NotificationService.java` |

---

## N1 — Dotted email templates are silently discarded

### Symptom
`welcome` emails are delivered. `order.shipped`, `password.reset`, `order.delivered` and
`payment.receipt` never are: the notification stays `PENDING` with `attempts = 0` forever,
nothing appears in any queue, nothing appears in the DLQ, and nothing is logged. SMS
`delivery.eta`, which also contains a dot, works fine.

### Root cause
```java
BindingBuilder.bind(emailQueue).to(notificationsExchange).with("notify.email.*");
BindingBuilder.bind(smsQueue).to(notificationsExchange).with("notify.sms.#");
BindingBuilder.bind(inAppQueue).to(notificationsExchange).with("notify.inapp.#");
```

On a topic exchange `*` matches **exactly one** segment and `#` matches **zero or more**.
The publisher builds `notify.<channel>.<templateCode>`:

| Routing key | `notify.email.*` | Result |
|---|---|---|
| `notify.email.welcome` | 3 segments, matches | delivered |
| `notify.email.order.shipped` | 4 segments, no match | **discarded** |
| `notify.sms.delivery.eta` | matched by `#` | delivered |

The email binding is the only one using `*`, which is why the failure looks template-specific
rather than channel-specific — and why the SMS team's dotted template works, killing the
first theory anyone forms.

The disappearance is total: a topic exchange drops a message that matches no binding, and
unless the publisher sets the mandatory flag with a returns callback, nobody is told.

### Exact location
`src/main/java/com/pulsesend/notifications/config/RabbitTopologyConfig.java`, the
`emailBinding` bean.

### Correct fix
```java
return BindingBuilder.bind(emailQueue).to(notificationsExchange).with("notify.email.#");
```

Better, build the pattern from configuration so all three bindings agree by construction:

```java
.with(properties.getMessaging().getRoutingPrefix() + ".email.#")
```

Two operational notes:

- **RabbitMQ never changes an existing binding.** The old `notify.email.*` binding stays
  until it is deleted. Remove it after deploying, or the queue is bound to both — harmless
  here, confusing later.
- Turn on publisher returns so a future mismatch is loud:

```yaml
spring.rabbitmq.template.mandatory: true
```

with a `ReturnsCallback` that logs. Unroutable messages then produce a log line instead of
silence.

### Affected components
`RabbitTopologyConfig`, `NotificationPublisher`, the email consumer, every product team
whose template code contains a dot.

### Underlying concept
**`*` and `#` are not interchangeable, and the difference only shows for keys with a
different number of segments than you tested with.** Whoever wrote this tested `welcome`,
saw it arrive, and moved on. The pattern was correct for the data that existed at the time.

The deeper principle: **messaging fails open into silence.** There is no foreign key, no
404, no compile error connecting a publisher to a binding. The contract lives in two files
that never reference each other, which is why it must come from one source of truth and why
publisher returns exist.

### Why this is realistic
Template codes gained dots gradually — `welcome` first, `order.shipped` later — and the
binding was written against the early ones. `*` also *looks* more precise than `#`, which
makes it the natural choice for someone thinking "one segment for the template code". Two
of the three bindings being right is what makes the diagnosis hard: the file looks
consistent at a glance.

### Detecting it faster next time
- **When one message type vanishes and another arrives, compare routing keys against
  bindings.** Two commands, side by side.
- Use the management UI's **Publish message** form to send a key by hand and watch whether
  it routes. It answers "is this a routing problem or a consumer problem?" in seconds.
- A message with no queue, no DLQ entry and no log line has almost certainly not been routed
  at all. Absence of evidence is the evidence.

### Prevention
- One source for exchange, routing prefix and binding patterns.
- `mandatory: true` plus a returns callback in every environment.
- A broker-backed integration test that publishes each template code and asserts it arrives.

---

## N2 — Poison messages loop forever and the dead letter queue stays empty

### Symptom
A message the provider cannot deliver is retried endlessly — roughly twice a second, which
is the provider timeout — filling the log, holding a consumer, and never draining. The DLQ,
which is declared and bound, stays empty. The only way to stop it is to purge the queue,
which throws the message away.

### Root cause
Two independent omissions, both required for the symptom.

**1. The queues have no dead-letter target.**

```java
return QueueBuilder.durable(properties.getMessaging().getEmailQueue()).build();
```

`notifications.dlx` and `notifications.dlq` are declared and bound to each other, so the
topology *looks* complete. But a queue only dead-letters when it is told where to:

```java
QueueBuilder.durable(name)
        .deadLetterExchange("notifications.dlx")
        .deadLetterRoutingKey(name)
        .build();
```

Without `x-dead-letter-exchange`, the DLQ is an orphan that nothing can ever reach.

**2. Rejected messages are requeued.**

`spring.rabbitmq.listener.simple.default-requeue-rejected` defaults to **`true`**, and this
application does not override it. When the listener throws, Spring AMQP nacks with
`requeue=true`, the broker puts the message back at the head of the queue, and the consumer
picks it up again immediately. There is no backoff, so the loop runs as fast as the failure
does.

Even with dead-lettering configured, requeue-on-reject would still loop: a requeued message
is not a dead letter. Both have to change.

### Exact location
`src/main/java/com/pulsesend/notifications/config/RabbitTopologyConfig.java` (the three
channel queue beans) and `src/main/resources/application.yml` (the missing listener
setting).

### Correct fix
Attach the dead-letter exchange to each channel queue:

```java
@Bean
public Queue emailQueue() {
    return QueueBuilder.durable(properties.getMessaging().getEmailQueue())
            .deadLetterExchange(properties.getMessaging().getDeadLetterExchange())
            .deadLetterRoutingKey(properties.getMessaging().getEmailQueue())
            .build();
}
```

and stop requeueing rejections:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1s
          multiplier: 2
          max-interval: 10s
```

The retry block is the important refinement. Without it, a single transient blip
dead-letters immediately, which is the opposite mistake. With it, Spring retries in-process
with backoff and only dead-letters after the attempts are exhausted — retry the transient,
quarantine the permanent.

**Queues cannot be reconfigured in place.** Adding `x-dead-letter-exchange` to an existing
queue fails with `PRECONDITION_FAILED` at startup. Delete the queues (they are empty after a
purge) or declare new ones under a versioned name.

Finally, note what happens to a message the consumer has already taken: purging removes
*ready* messages, not unacknowledged ones. To clear an in-flight poison message you must
stop the consumer first, which is why the loop survived the operator's purge.

### Affected components
`RabbitTopologyConfig`, `application.yml`, all three channel consumers, the DLQ, and the
on-call engineer.

### Underlying concept
**Declaring a dead letter queue is not the same as routing to it.** The DLX is a property of
the *source* queue, not of the DLQ. A bound, durable, visible dead letter queue that no
queue points at will sit at zero forever and look healthy.

**And "reject" does not mean "discard".** `basic.nack` carries a requeue flag; the default
in Spring AMQP is to requeue, which is right for a message that failed because of a
transient condition on *this* consumer and catastrophically wrong for one that will fail on
every consumer, every time. The distinction between retryable and poison is yours to make,
and if you do not make it the broker will retry forever.

### Why this is realistic
The topology reads as complete and thoughtful: there is a dead letter exchange, a dead
letter queue, and a binding between them. Everything is present except the one attribute
that connects them to the queues that need it — and that attribute lives on the queue
declaration, several beans away from the DLQ it refers to.

`default-requeue-rejected` is worse, because it is a default. It is not in the config file,
so there is nothing to read and nothing to review. It only exists in the documentation.

### Detecting it faster next time
- **An empty DLQ during an incident is a finding, not a relief.** If messages are failing and
  nothing is dead-lettering, the dead-lettering is not configured.
- `rabbitmqctl list_queues name arguments` shows a queue's `x-dead-letter-exchange`
  directly. Nothing there, no dead-lettering.
- A queue whose depth never falls while a consumer is attached and busy is a redelivery
  loop. Check the redelivery count on the message, or just watch the log for the same
  reference repeating.

### Prevention
- Declare dead-lettering on every work queue, from shared configuration rather than per
  queue.
- Set `default-requeue-rejected: false` explicitly and configure listener retry, so the
  policy is written down.
- Alert on DLQ depth **and** on queue depth that is not falling — the second catches exactly
  this case.

---

## N3 — Replay delivers a second copy

### Symptom
Requeueing a notification that has already been delivered sends it again. Support replayed a
batch after an outage and everyone in it received a duplicate, including the ones that had
gone out before the outage. The database shows `attempts = 2` and two delivery-log rows for
one notification; the outbox shows two messages.

### Root cause
`NotificationDeliveryHandler.handle` loads the notification and immediately starts work:

```java
Notification notification = notificationRepository
        .findByNotificationRef(message.getNotificationRef())
        .orElseThrow(...);

NotificationTemplate template = ...;
String subject = renderer.render(...);
String body = renderer.render(...);

notification.setAttempts(notification.getAttempts() + 1);
notification.setStatus(NotificationStatus.SENT);
DeliveryOutcome outcome = dispatcher.deliver(...);
```

It never asks whether this notification has already been delivered. Any second arrival —
a manual requeue, a broker redelivery after a consumer crash, a redelivery caused by N2 —
produces a second send.

This matters more than it looks, because **at-least-once is the only delivery guarantee a
broker gives you.** Redelivery is not an error condition; it is normal operation, and the
consumer is the only place that can turn it into effectively-once.

### Exact location
`src/main/java/com/pulsesend/notifications/messaging/NotificationDeliveryHandler.java`, the
start of `handle`.

### Correct fix
Make the handler idempotent by checking the state it already has:

```java
if (notification.getStatus() == NotificationStatus.SENT) {
    log.debug("{} has already been delivered, ignoring redelivery", notification.getNotificationRef());
    return;
}
```

**This only works once N4 is fixed.** Today the status is set to `SENT` *before* the
provider is called, so a notification whose delivery failed is already marked `SENT` — and a
status check added now would skip the retry and silently drop it. Fix N4 first, then N3, or
you convert a duplicate-delivery defect into a lost-message defect.

For a stronger guarantee, make the dedupe a database constraint rather than a read: a unique
index on `(notification_id, attempt_no)` in `delivery_log`, or a dedicated
`processed_messages` table keyed by message id, inserted at the start of the transaction. A
read-then-act check is still racy if two consumers take the same message concurrently, and
with `concurrency: 1..2` configured here, that is reachable.

### Affected components
`NotificationDeliveryHandler`, `NotificationService.requeue`, `delivery_log`, `ChannelDispatcher`,
and every recipient of a replayed batch.

### Underlying concept
**Brokers deliver at least once; consumers make it effectively once.** Requiring exactly-once
delivery from the transport is asking for something it cannot provide. The consumer must be
able to see the same message twice and do the work once — by checking state, by an
idempotency table, or by making the side effect naturally idempotent.

The related trap: **idempotency checks that read mutable state are only as trustworthy as
that state.** Here the status is the natural key, and N4 makes the status lie. An
idempotency check built on a field that is set optimistically is not an idempotency check.

### Why this is realistic
The handler reads as a clean pipeline: load, render, send, log. Nothing is obviously
missing, because the check that should exist has no natural place in that sequence — it
would be a guard clause before the pipeline starts, and pipelines invite you to start with
the first step.

And it is untestable without a broker. In a unit test the handler is called once, which is
exactly the case that works.

### Detecting it faster next time
- **Call the handler twice and count the side effects.** That is the whole test, and it
  applies to every consumer you will ever write.
- Count deliveries per notification in the data. The `HAVING count(*) > 1` query finds every
  instance, historical ones included.
- Whenever you see a `@RabbitListener`, `@KafkaListener` or webhook handler, ask what it does
  on redelivery before you read anything else.

### Prevention
- Every consumer starts with an idempotency check, ideally backed by a unique constraint.
- Test redelivery explicitly — invoke the handler twice in an integration test.
- Track a message id end to end so the check has a stable key that does not depend on mutable
  business state.

---

## N4 — The dashboard reports rejected messages as sent

### Symptom
A notification the provider bounced shows `status = SENT`, `attempts = 1`, while its delivery
log row says `REJECTED — Recipient rejected the message` and the provider outbox is empty for
it. The delivery dashboard counts it as a success.

### Root cause
```java
notification.setAttempts(notification.getAttempts() + 1);
notification.setStatus(NotificationStatus.SENT);

DeliveryOutcome outcome = dispatcher.deliver(...);

DeliveryLogEntry entry = new DeliveryLogEntry();
entry.setOutcome(outcome.delivered() ? DeliveryOutcomeType.DELIVERED : DeliveryOutcomeType.REJECTED);
deliveryLogRepository.save(entry);
```

The status is set **before** the provider is called, and the returned outcome is written only
to the delivery log. Nothing ever moves the notification to `FAILED`.

The three provider outcomes are handled inconsistently:

| Provider result | Delivery log | Notification status | Correct? |
|---|---|---|---|
| delivered | `DELIVERED` | `SENT` | yes, by luck |
| rejected (bounce) | `REJECTED` | `SENT` | **no** |
| unavailable (throws) | nothing | rolled back to previous | acceptable, by accident |

The third row is only right because the exception rolls the transaction back, undoing the
premature `SENT`. Nobody designed that.

### Exact location
`src/main/java/com/pulsesend/notifications/messaging/NotificationDeliveryHandler.java`, the
`setStatus(SENT)` before `dispatcher.deliver`.

### Correct fix
Set the status from the outcome, in the branch that knows it:

```java
notification.setAttempts(notification.getAttempts() + 1);

DeliveryOutcome outcome = dispatcher.deliver(...);

if (outcome.delivered()) {
    notification.setStatus(NotificationStatus.SENT);
    notification.setLastError(null);
} else {
    notification.setStatus(NotificationStatus.FAILED);
    notification.setLastError(outcome.detail());
}
```

Leave the transient case alone: when `deliver` throws, the transaction rolls back, the
status stays where it was, and — once N2 is fixed — the message is retried and eventually
dead-lettered. That is the correct handling of "we do not know yet", and it is why `PENDING`
must remain a real state rather than being optimistically advanced.

### Affected components
`NotificationDeliveryHandler`, the `notifications` table, the delivery dashboard, every
product team's reporting, and N3's idempotency check.

### Underlying concept
**Do not record an outcome before you have one.** A call to an external system has three
results — success, failure, unknown — and code that assigns a status before the call can
only be right about one of them.

The second concept is that **two representations of the same fact will diverge unless one
code path writes both.** The delivery log was correct throughout; the status was not,
because they are written in different places from different information. Either derive the
status from the log, or write both from the same branch.

### Why this is realistic
Setting the status alongside the attempt counter is natural — they look like the same
bookkeeping step, and the author was thinking "record that we are handling this". The happy
path is correct, the throwing path is accidentally correct, and only the middle case is
wrong. One branch in three, in a method that otherwise looks careful.

It is also self-concealing: the dashboard shows success, so nobody investigates. It took
customers reporting non-delivery to surface it.

### Detecting it faster next time
- **When two stores disagree, find every line that writes each one.** Two writes, one before
  the decision point.
- Compare the status distribution against the provider outbox. If `SENT` exceeds what the
  provider accepted, the status is being set from the wrong place.
- For any external call, enumerate the three outcomes and check each has an explicit branch.

### Prevention
- Assign terminal status only from the outcome.
- An invariant test: every `SENT` notification has a `DELIVERED` delivery-log row.
- Build the dashboard on the delivery log, or on a status derived from it, so there is one
  source of truth.

---

## N5 — Broadcasts ignore opt-outs

### Symptom
A campaign sent through `POST /api/ops/broadcast` reached recipients who had switched email
off. The same recipient sent through `POST /api/notifications` is correctly `SUPPRESSED`.

### Root cause
`NotificationService.send` checks:

```java
if (!channelEnabledFor(request.getRecipientRef(), template.getChannel())) {
    notification.setStatus(NotificationStatus.SUPPRESSED);
    ...
    return notificationMapper.toResponse(suppressed);
}
```

`NotificationService.broadcast` does not:

```java
for (BroadcastTarget target : request.getTargets()) {
    Notification notification = newNotification(target.getRecipientRef(), target.getDestination(),
            template, request.getPayload());
    Notification saved = notificationRepository.save(notification);
    publisher.publish(saved);
    queued.add(notificationMapper.toResponse(saved));
}
```

Both methods build a notification from the same helper and publish it; only one consults
`channelEnabledFor`. The check is four lines away in the same class and was not carried
across.

### Exact location
`src/main/java/com/pulsesend/notifications/service/NotificationService.java`, the loop in
`broadcast`.

### Correct fix
The minimal change adds the same guard inside the loop. The better fix removes the
possibility of a third path getting it wrong — extract one method that both callers use:

```java
private Notification prepare(String recipientRef, String destination,
                             NotificationTemplate template, String payload) {
    Notification notification = newNotification(recipientRef, destination, template, payload);
    if (!channelEnabledFor(recipientRef, template.getChannel())) {
        notification.setStatus(NotificationStatus.SUPPRESSED);
        notification.setLastError("Recipient has opted out of " + template.getChannel());
        return notificationRepository.save(notification);
    }
    Notification saved = notificationRepository.save(notification);
    publisher.publish(saved);
    return saved;
}
```

`send` and `broadcast` then both call `prepare`, and creating a notification without the
check is no longer expressible.

Defence in depth is worth considering for a rule with legal weight: a final check in the
consumer, immediately before handing to the provider, catches anything that reaches the
queue by any route — including messages queued before an opt-out was recorded.

### Affected components
`NotificationService`, `NotificationPreferenceRepository`, `MessagingOpsController`, and the
company's compliance position.

### Underlying concept
**A rule enforced at call sites will eventually be missed at a call site.** The number of
paths that create a thing only grows, and each new one is an opportunity to omit a check.
Rules belong at a chokepoint that every path must cross.

For consent specifically, the chokepoint should be as late as possible. A preference checked
at request time is already stale by the time the message is delivered; checking again at the
moment of sending is the only way to honour an opt-out recorded in between.

### Why this is realistic
`send` was written first and is correct, which makes the class look like one that thinks
about preferences. `broadcast` came later for the growth team, and was written by
copying the shape of `send` while dropping the early-return that did not fit a loop.
Nothing in the diff looks wrong.

And it stays hidden, because the obvious test — send to an opted-out recipient — uses the
path that works.

### Detecting it faster next time
- **Enumerate every path that produces the thing, and check each for the rule.** Grep for
  `publisher.publish` — two call sites, two checks needed, one present.
- When one endpoint enforces a rule and a sibling does not, assume the sibling is wrong and
  check *all* siblings, not just the one in the ticket.
- For consent and access rules, test through every entry point, not just the main one.

### Prevention
- One method owning creation, so the rule cannot be bypassed.
- Re-check consent at the point of delivery.
- A test per entry point asserting an opted-out recipient is suppressed.

---

## How these interact

**N4 must be fixed before N3.** The obvious fix for the duplicate-delivery defect is to skip
notifications already marked `SENT`. But N4 marks a notification `SENT` before the provider
is called, so a bounced notification already carries that status — and the "fix" would skip
its retry and drop it silently. Fixing N3 first turns a visible duplicate into an invisible
loss. This is the sharpest ordering constraint in the project.

**N2 amplifies N3.** Every requeue caused by the missing dead-letter configuration is another
redelivery, and without an idempotency check each one is another copy to the recipient. The
"forty emails" in PS-715 and the loop in PS-708 are the same event seen from two sides —
support counts messages, operations counts queue depth.

**N1 and N2 are the same silence at different points.** N1 loses a message before it reaches
a queue; N2 loses one after a consumer gives up. Neither produces an error, and the DLQ is
empty in both cases — which makes an empty DLQ ambiguous until you have fixed one of them.

**N5 is independent**, and it is the only defect here with a legal dimension rather than an
operational one.

**Suggested fix order:** N1 (restores delivery for most templates), N5 (stops sending to
people who opted out), N4 (makes the status honest), N3 (now safe, and stops duplicates),
N2 (the topology change — it needs a queue redeclaration, so do it when you can afford a
restart).

---

## What the test suite tells you

`mvn test` passes with all five defects live.

`TemplateRendererTest` tests placeholder substitution, which was never wrong.
`NotificationRepositoryTest` tests that notifications can be found by recipient and by
status. Both pass, and neither can see a single defect in this project, because **every one
of them lives in the broker configuration or in the consumer**, and the suite starts
neither.

That is the structural lesson. A messaging application has three surfaces —
the API, the broker topology, and the consumer — and unit tests cover only the first. The
tests that would have caught these need a real broker (Testcontainers has a RabbitMQ module)
and would assert things unit tests cannot express: that a published routing key arrives in
the expected queue, that a failing message reaches the DLQ and stops being retried, and that
handling the same message twice produces one delivery.
