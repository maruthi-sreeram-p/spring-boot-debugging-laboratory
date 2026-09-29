# Debugging Guide — Pulsesend Notification Service

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

> **Safety note.** One of these defects makes a message redeliver in a loop. It runs at
> about two attempts a second and will not stop by itself. To stop it: shut down the
> application, then
> `docker exec -it lab-rabbitmq rabbitmqctl purge_queue notifications.email`.

---

## 1. Business context

Pulsesend is the shared notification platform. Every product team at the company sends
through it rather than integrating with providers themselves, which means one bug here is a
bug in every product at once.

Three groups depend on it:

- **Product teams** send transactional messages — welcome mails, OTPs, shipping updates,
  receipts — and watch a dashboard that counts how many were sent.
- **Recipients** get the messages, and have opted in or out per channel. An opt-out is a
  commitment the company has made; sending to somebody who unsubscribed is a compliance
  problem, not a nuisance.
- **Messaging operations** keep the pipes working. They watch queue depths, replay messages
  after provider outages, and are the people who find out first when something is wrong.

The platform's whole job is *exactly once, to people who want it*. Both halves of that are
under attack here.

---

## 2. How the system is supposed to behave

**Every template reaches its channel's consumer.** The routing key is
`notify.<channel>.<templateCode>` and template codes contain dots. The extra segments exist
so that subscribers can bind narrowly later; they never stop a message being delivered.

**A message a consumer cannot process ends up on the dead letter queue**, where operations
can look at it and replay it once the cause is fixed. It does not disappear and it does not
go round forever.

**A notification is delivered once.** Redelivery after a failure, a manual requeue and a
consumer restart are all normal events. None of them may produce a second copy for the
recipient.

**`SENT` means a provider accepted it.** The dashboard counts `SENT`. A message the provider
rejected is `FAILED`. The delivery log and the status always tell the same story.

**Opt-outs bind the whole platform.** Whatever part of the system asks for a notification —
the request API, a campaign, an internal job — a recipient who has switched off a channel
does not receive that channel.

---

## 3. Symptoms

Nobody has told you how many distinct defects there are.

---

### Ticket PS-701 — "Shipping emails never arrive, welcome emails do"

> Filed by: Northwind product team
>
> Our welcome emails are fine. Our `order.shipped` emails have never been delivered — not
> one, since launch.
>
> Your API returns 202 and creates the notification. It sits at PENDING with 0 attempts
> forever. There is nothing in the dead letter queue and nothing in the logs.
>
> Our SMS team uses `delivery.eta`, which also has a dot in it, and theirs works fine. So it
> is not the dot.

---

### Ticket PS-708 — "One bad message and the queue never drains"

> Filed by: Messaging operations
>
> During the provider outage last night, one email stuck. The consumer has been trying it
> over and over ever since — the log has thousands of the same failure, and the queue depth
> never goes to zero.
>
> The whole point of the dead letter queue is that this message ends up there so I can look
> at it in the morning. The DLQ has been empty this entire time.
>
> I had to purge the queue to stop it, which threw the message away. That is exactly what I
> was trying to avoid.

---

### Ticket PS-715 — "Customer received the same email forty times"

> Filed by: Customer support
>
> A customer got the same order confirmation repeatedly. Support replayed a batch of
> notifications after the outage was resolved, using the requeue endpoint, and everyone in
> that batch got a second copy — including the ones that had already gone out before the
> outage.
>
> Is the requeue endpoint supposed to check whether it already went?

---

### Ticket PS-722 — "The dashboard says 100% delivered"

> Filed by: Northwind product team
>
> Our dashboard shows every receipt email as sent. Customers say they never got them.
>
> I looked at one in your API. Status SENT, attempts 1. Then I looked at the delivery log
> for the same notification and it says REJECTED — "Recipient rejected the message".
>
> Which one should I believe, and why do you have both?

---

### Ticket PS-730 — "We emailed people who unsubscribed"

> Filed by: Legal, escalated
>
> The lifecycle campaign that went out on Tuesday reached recipients who had switched email
> off in their preferences. We have the preference rows with dates well before the campaign.
>
> When I test the ordinary send API with the same recipient it correctly refuses — the
> notification comes back SUPPRESSED. So the platform clearly knows.

---

## 4. Investigation hints

Read **one** hint at a time and go back to the code before reading the next.

For every experiment, reset the outbox first so it contains only what you just did, and
check all three views: the outbox, the database, and the queues.

---

### PS-701 — one template never routes

**Hint 1.** The product team has already eliminated the obvious theory. Reproduce their
comparison exactly: send `welcome` and `order.shipped` as email, and `delivery.eta` as SMS.
Note which arrive.

**Hint 2.** Two of those three template codes contain a dot, and only one of the dotted ones
fails. The difference is not the template — it is which queue it was going to.

**Hint 3.** List the bindings and put them next to the routing keys the publisher logs:

```bash
docker exec -it lab-rabbitmq rabbitmqctl list_bindings source_name routing_key destination_name
```

**Hint 4.** Read the topic matching table in the README. One binding uses a different
wildcard from the other two. Work out precisely which keys each one matches.

**Hint 5.** The message is not lost in a queue, not in the DLQ, and not in an error log.
Look up what a topic exchange does with a message that matches no binding — that silence is
the defect's signature, and you will meet it again.

---

### PS-708 — poison message loops and never reaches the DLQ

**Hint 1.** Reproduce it deliberately, and be ready to stop it. Send to a destination
containing `outage`, watch for a few seconds, then shut the app down and purge the queue.

**Hint 2.** When a Spring AMQP listener throws, the broker is told to requeue or not. Find
the setting that decides which, look up its default, and check whether this application
changes it.

**Hint 3.** Now the second half: a dead letter exchange and queue are declared and bound.
Why does nothing arrive? Read how a queue is told where to send its dead letters, and check
the queue declarations.

**Hint 4.** These two are separate and both are needed. Work out what each one does on its
own: what happens with dead-lettering configured but requeue still on, and what happens with
requeue off but no dead-letter target?

**Hint 5.** Even with both fixed, a message that fails once transiently should probably not
be dead-lettered immediately. Look at what Spring AMQP offers between "retry forever" and
"give up at once".

---

### PS-715 — replay delivers a second copy

**Hint 1.** Send one notification, let it deliver, then requeue it and watch the outbox.

**Hint 2.** Read the consumer's handler from the top. It loads the notification and starts
work. Ask what it never asks about the notification it just loaded.

**Hint 3.** The notification already carries a field that says whether it was delivered.
Consider using it — and then read PS-722 before you trust it, because these two tickets are
connected in a way that matters for the order you fix them in.

---

### PS-722 — status and delivery log disagree

**Hint 1.** Reproduce it: send to a destination containing `bounce`, then compare the
notification, its delivery log and the outbox.

**Hint 2.** Find the line that sets the status to `SENT` and note where it sits relative to
the call to the provider.

**Hint 3.** The provider reports a rejection by *returning* an outcome, not by throwing. Read
what the handler does with that returned value, and what it does not do.

**Hint 4.** Decide what the status should be for each of the three cases — delivered,
rejected, provider unavailable — before you change anything. One of those three should not
be a terminal status at all.

---

### PS-730 — campaign ignored opt-outs

**Hint 1.** The ordinary send path gets it right. Find where it checks, then find the other
method that creates notifications.

**Hint 2.** Both methods build a notification and publish it. Diff them line by line; the
difference is short.

**Hint 3.** Think about why the duplication existed in the first place, and what shape of
fix stops a third path from being added later with the same omission.

---

## 5. Before you call it fixed

Reset the outbox before each check.

- **PS-701:** send every seeded template on every channel — `welcome`, `password.reset`,
  `order.shipped`, `order.delivered`, `payment.receipt`, `otp`, `delivery.eta`,
  `inbox.mention`. Every one must reach `SENT` and appear in the outbox.
- **PS-708:** send to an `outage` destination. The message must stop being retried and must
  appear in `notifications.dlq`. Confirm the queue depth returns to zero and the loop stops
  on its own.
- **PS-715:** send, let it deliver, then requeue. The outbox must contain **one** copy.
  Then requeue a notification that genuinely failed and confirm that one *is* retried.
- **PS-722:** send to a `bounce` destination. Status must be `FAILED`, the log `REJECTED`,
  and the outbox empty for it.
- **PS-730:** broadcast to `CUST-1003` (opted out of email). It must be `SUPPRESSED` and the
  outbox must be empty for it. Then broadcast to `CUST-1001` and confirm it is delivered.

Then check the data:

```bash
docker exec -it lab-postgres psql -U labuser -d notificationdb -c "
SELECT n.notification_ref, n.status, count(d.id) AS log_rows
  FROM notifications n LEFT JOIN delivery_log d ON d.notification_id = n.id
 GROUP BY n.id, n.notification_ref, n.status
HAVING count(d.id) > 1;"
```

and the broker:

```bash
docker exec -it lab-rabbitmq rabbitmqctl list_queues name messages
```

All channel queues at zero, and anything unprocessable sitting in the DLQ where operations
can see it.

Re-run `mvn test`.

When you are done, ask for **verification mode** and I will check your work.
