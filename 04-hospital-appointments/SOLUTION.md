# SOLUTION — Harbourview Clinic Appointment Service

> **Answer key. Do not read this until you have finished investigating.**

Five defects. All deterministic; one of them (H2) only reproduces where the server zone is
not UTC, which this project guarantees by pinning the JVM to `Asia/Kolkata`.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| H1 | CLIN-324 | Clash detection only inspects the existing start, so overlap is order-dependent | `service/AppointmentService.java` |
| H2 | CLIN-318 | "Now" is built as UTC wall-clock and compared against clinic-local times | `service/AppointmentService.java` |
| H3 | CLIN-311 | Slot loop uses a closed interval, emitting one slot past closing | `service/ScheduleService.java` |
| H4 | CLIN-330 | `patientId` is taken from the request body without checking the caller | `service/AppointmentService.java` |
| H5 | CLIN-336 | The no-show sweep marks anything that is not `COMPLETED` | `scheduling/NoShowSweeper.java` |

---

## H1 — Clash detection depends on the order of booking

### Symptom
Dr Ellery consults for 40 minutes. Booking 14:00 and then 14:20 produces two overlapping
appointments, both `201`. Booking 14:20 and then 14:00 correctly refuses the second with
`409`. Same two intervals, opposite outcomes.

### Root cause
```java
private boolean clashes(Appointment existing, LocalDateTime start, LocalDateTime end) {
    return !existing.getStartTime().isBefore(start) && existing.getStartTime().isBefore(end);
}
```

This asks only one question: *does the existing appointment start inside the new interval?*
It never looks at the existing appointment's **end**, so it cannot see an existing
appointment that started earlier and is still running.

- Existing 14:00–14:40, new 14:20–15:00: `!14:00.isBefore(14:20)` is `false` → no clash.
  The old one started before the new one, so it is invisible to this test.
- Existing 14:20–15:00, new 14:00–14:40: `!14:20.isBefore(14:00)` is `true` and
  `14:20.isBefore(14:40)` is `true` → clash detected.

The test is asymmetric, so the answer depends on which row happens to be the "existing"
one, which is to say on creation order.

Note that `ScheduleService.isFree` — used by the slots endpoint — gets it right:

```java
appointment.getStartTime().isBefore(slotEnd) && appointment.getEndTime().isAfter(slotStart)
```

So the grid correctly shows the slot as taken while the booking endpoint lets it through.
Two implementations of one rule, one correct.

### Exact location
`src/main/java/com/harbourview/clinic/service/AppointmentService.java`, the `clashes`
method.

### Correct fix
```java
private boolean clashes(Appointment existing, LocalDateTime start, LocalDateTime end) {
    return existing.getStartTime().isBefore(end) && existing.getEndTime().isAfter(start);
}
```

Better still, delete the duplicate: extract the single overlap predicate into one place and
have both `ScheduleService` and `AppointmentService` call it. Two copies of an invariant
will always drift.

For real safety, add a database-level guard as well — a uniqueness or exclusion constraint,
or a `SELECT ... FOR UPDATE` on the doctor's day — because two simultaneous bookings can
both pass an application-level check. That is the concurrency problem underneath this one
and it is not what this ticket is about, but it is real.

### Affected components
`AppointmentService`, `ScheduleService`, `AppointmentRepository`, the `appointments` table.

### Underlying concept
**Interval overlap is symmetric, so its test must be too.** Derive it by negation rather
than by enumerating cases: two intervals `[a1,a2)` and `[b1,b2)` do *not* overlap exactly
when `a2 <= b1 || b2 <= a1`. Negate it and you get `a1 < b2 && b1 < a2` — two comparisons,
obviously symmetric, correct for all four overlap shapes. Any overlap test that mentions
one endpoint more often than the other is wrong.

The second lesson: **when the same rule is implemented twice, expect them to disagree**, and
when one of the two behaves correctly, that is where the specification lives.

### Why this is realistic
`!existing.getStartTime().isBefore(start) && existing.getStartTime().isBefore(end)` reads
like a careful piece of code. It has a negation, it has two comparisons, it looks
considered. And it works for the case everyone tests: booking the same slot twice, and
booking a later slot that starts inside an earlier one. It only fails when the *existing*
appointment is the longer or earlier one, which requires deliberately trying the reverse
order.

Order-dependence also makes for a terrible bug report. Dr Ellery was right to be baffled;
nothing about "two appointments overlap" suggests that creation order is a variable.

### Detecting it faster next time
- **Order-dependence in a symmetric question is a strong fingerprint.** It almost always
  means an asymmetric predicate. Go straight to the comparison.
- Draw the four overlap shapes and evaluate the condition against each. Ten minutes on
  paper, no debugger.
- When two components answer the same question differently, diff them first.

### Prevention
- One overlap predicate, unit-tested against all four shapes plus the two touching cases
  (`a2 == b1`, `b2 == a1`, which must *not* clash).
- A database constraint so the invariant survives concurrency and any future code path.

---

## H2 — Appointments can be booked in the past

### Symptom
`POST /api/appointments` accepts a start time several hours ago and returns `201`. The
no-show sweep then marks it immediately, generating no-shows for appointments nobody could
have attended. A developer on a UTC machine cannot reproduce it.

### Root cause
```java
LocalDateTime earliest = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
        .plusMinutes(properties.getBooking().getMinimumNoticeMinutes());
if (start.isBefore(earliest)) { throw ... }
```

`start` is a clinic-local wall-clock time (`Asia/Kolkata`, UTC+05:30) — that is what the
column stores and what the API documents. But `earliest` is built by projecting the current
instant onto **UTC**, producing a wall-clock value 5 hours 30 minutes behind clinic time.

At 20:00 clinic time, `earliest` is 14:30 + 30 minutes = 15:00. Anything from 15:00 onwards
passes the check, so the last five hours of the working day are bookable retrospectively.

The same wrong "now" appears in the maximum-lead-time check, where being 5.5 hours out is
harmless and therefore hides the mistake from anyone reading the method quickly.

On a machine whose default zone is UTC, `ZoneOffset.UTC` and the clinic zone coincide, the
offset is zero, and the bug disappears entirely. That is the whole "works on my machine"
mechanism. `ClinicApplication` pins the JVM to `Asia/Kolkata`, so the defect reproduces
wherever you run it — but the underlying hazard is exactly the one that bites teams whose
laptops and servers disagree.

### Exact location
`src/main/java/com/harbourview/clinic/service/AppointmentService.java`,
`validateBookingWindow`, both `LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)`
calls.

### Correct fix
Compare like with like. The simplest correct form:

```java
LocalDateTime now = LocalDateTime.now(ZoneId.of(properties.getTimezone()));
LocalDateTime earliest = now.plusMinutes(properties.getBooking().getMinimumNoticeMinutes());
LocalDateTime latest = now.plusDays(properties.getBooking().getMaximumDaysAhead());
```

Better: inject a `Clock` bean configured with the clinic zone and use `LocalDateTime.now(clock)`
throughout. That makes the zone a single explicit decision and makes the rule testable
without waiting for real time to pass.

Note `cancel` already does `LocalDateTime.now()`, which is correct only because the JVM
default is pinned. Making the clock explicit fixes that latent fragility too.

### Affected components
`AppointmentService`, `ClinicProperties.timezone`, `ClinicApplication` (the JVM default
zone), `NoShowSweeper` (which inherits the same assumption), the `appointments` table.

### Underlying concept
**`LocalDateTime` has no zone, so comparing two of them is only meaningful when both were
produced in the same zone.** The type system will not help you: the code compiles and runs
and silently compares two different wall clocks.

`LocalDateTime.ofInstant(instant, zone)` is a *projection*: it asks "what does the wall
clock read in this zone at this instant?". Choosing the wrong zone there does not throw, it
just shifts the answer by the offset. Prefer `LocalDateTime.now(zone)` when you want local
time, keep `Instant` when you want a point on the timeline, and never mix the two in one
comparison.

And: **a zone-dependent bug is invisible in any environment where the offset is zero.** UTC
developer machines and UTC CI both hide it; production in a real timezone reveals it.

### Why this is realistic
`ZoneOffset.UTC` looks like the responsible, timezone-aware choice. Developers are taught
"store and compute in UTC", and this line is that advice applied in exactly the wrong
place — the values being compared are deliberately *not* UTC, because a clinic books in
clinic time.

It is also invisible in review: the line mentions a zone explicitly, which is more than most
date code does. And the failure is a 5.5-hour window rather than a total breakage, so most
bookings are unaffected and the pattern is hard to spot from the data.

### Detecting it faster next time
- **"Cannot reproduce on my machine" plus anything involving time means check the zone
  first.** Print both sides of the failing comparison and compare them with `date`. The size
  of the discrepancy identifies the zone immediately.
- Grep for `ZoneOffset.UTC`, `ZoneId.systemDefault()` and `LocalDateTime.now()` whenever
  time behaves oddly. Each occurrence is a decision that should be deliberate.
- Ask of every `LocalDateTime`: which zone was this produced in? If the code cannot answer,
  that is the defect.

### Prevention
- Inject a `Clock`; never call `now()` statically in business logic. It makes the zone
  explicit and the rule unit-testable at any instant.
- Run at least one CI job in a non-UTC timezone. It costs one environment variable and
  catches this whole class.
- Document the zone of every time-typed column and API field, as `API.md` does here.

---

## H3 — One slot too many, past the end of the session

### Symptom
Dr Deshpande works 09:00–13:00 in 30-minute consultations. The slots endpoint returns nine
slots, the last running 13:00–13:30. Patients book it; the front desk has to phone them.

### Root cause
```java
for (LocalTime cursor = schedule.getStartTime();
     !cursor.isAfter(schedule.getEndTime());
     cursor = cursor.plusMinutes(schedule.getSlotMinutes())) {
```

`!cursor.isAfter(end)` means `cursor <= end`, a **closed** interval. The cursor reaches
13:00, passes the test, and emits a slot from 13:00 to 13:30.

The working window is half-open: the doctor is available up to 13:00, not including it. The
condition also checks only the slot's *start*, when what must fit inside the window is the
whole slot.

### Exact location
`src/main/java/com/harbourview/clinic/service/ScheduleService.java`, the loop condition.

### Correct fix
Require the whole slot to fit:

```java
for (LocalTime cursor = schedule.getStartTime();
     !cursor.plusMinutes(schedule.getSlotMinutes()).isAfter(schedule.getEndTime());
     cursor = cursor.plusMinutes(schedule.getSlotMinutes())) {
```

This is correct even when the window does not divide evenly by the slot length — 09:00–13:10
with 30-minute slots yields eight, not a ninth running to 13:30. The simpler
`cursor.isBefore(end)` fixes the reported symptom but silently reintroduces it for any
window that is not an exact multiple.

Note the slot length comes from `doctor_schedules.slot_minutes`, while the booking endpoint
derives the end time from `doctors.consultation_minutes`. They agree in the seed data. That
they *can* disagree is a latent inconsistency worth collapsing.

### Affected components
`ScheduleService`, `DoctorSchedule`, the slots endpoint, and the booking endpoint (which
does not validate against working hours at all, so it accepts whatever the grid offered).

### Underlying concept
**Half-open intervals are the right default for time ranges**, `[start, end)`. They tile
without gaps or overlaps, they compose, and they have no "last microsecond" edge case. A
closed interval at the top end is almost always an off-by-one waiting for the boundary to
line up.

And: **when you are generating fixed-size chunks, the loop condition must be about the
chunk, not about its starting point.** The question is "does this slot fit?", not "does this
slot start before closing?".

### Why this is realistic
`!cursor.isAfter(end)` is the natural way to write "up to and including the end time" in
English, and English is what the requirement was written in. Hours that divide evenly by
the slot length — which almost all of them do — make the extra slot land exactly on the
closing time, where it looks superficially plausible rather than absurd. Nobody notices in
a demo; the doctor notices in clinic.

### Detecting it faster next time
- **Count, do not squint.** Compute the expected number of slots from the window and the
  slot length, compare with what the API returns, and you have the defect before opening
  the file.
- Every loop over a time range: check the first and last iteration explicitly.
- Whenever you see `isAfter`/`isBefore` with a negation, restate it as `<=` or `<` and ask
  whether the boundary belongs inside.

### Prevention
- A test per schedule shape asserting the count and the last slot's end time, including a
  window that does not divide evenly.
- Prefer half-open ranges everywhere and say so in the API contract.

---

## H4 — A patient can book as somebody else

### Symptom
A patient sends `patientId` for a different patient and gets `201`. The appointment is
created against the victim, appears in the victim's list, and the response discloses the
victim's full name and MRN to the attacker.

### Root cause
```java
public AppointmentResponse book(BookAppointmentRequest request, ClinicUser principal) {
    ...
    Patient patient = patientRepository.findById(request.getPatientId())
            .orElseThrow(() -> new ResourceNotFoundException("Patient", request.getPatientId()));
```

`principal` is accepted as a parameter and never read. The patient the appointment is booked
for comes entirely from the request body.

The endpoint genuinely serves two kinds of caller: a patient booking for themselves, and the
scheduling desk booking for anyone. `ClinicUser` already exposes `getPatientId()`,
`isPatient()` and `isSchedulingDesk()` — every ingredient for the check exists, and the
check itself is simply absent.

The information disclosure is the same defect, not a second one: `ClinicMapper.toResponse`
faithfully returns the patient's name and MRN, and the attacker asked for a patient who is
not them.

### Exact location
`src/main/java/com/harbourview/clinic/service/AppointmentService.java`, the start of `book`.

### Correct fix
```java
Long patientId;
if (principal.isSchedulingDesk()) {
    patientId = request.getPatientId();
} else if (principal.isPatient()) {
    patientId = principal.getPatientId();   // the body is not consulted
} else {
    throw new AccessDeniedException("Only patients and the scheduling desk may book");
}
Patient patient = patientRepository.findById(patientId)
        .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
```

Deriving the id rather than validating it is deliberate: a mismatched body is then simply
ignored rather than becoming a probe that returns different errors for existing and
non-existent patients.

Stronger still: remove `patientId` from the patient-facing contract entirely and give the
scheduling desk its own endpoint (`POST /api/back-office/appointments`) that takes it. Then
no patient request can even express the illegal intent.

### Affected components
`AppointmentService`, `BookAppointmentRequest`, `AppointmentController`, `ClinicUser`,
patient confidentiality.

### Underlying concept
**Identity comes from the authenticated principal, never from the request body.** Any field
that names *who the caller is* is attacker-controlled. This is the single most common
broken-access-control pattern: the endpoint authenticates correctly and then acts on an
identity the client supplied.

The subtler point is that **the same endpoint serving two audiences needs the audience
check inside it.** The URL rules cannot express "this field is honoured for ops and ignored
for patients", so it must live in the code — and it is easy to leave out precisely because
the endpoint works perfectly for both audiences without it.

### Why this is realistic
The scheduling desk requirement came first or came later — either way, `patientId` had to
exist in the payload for the desk, and once it is in the DTO the obvious implementation is
to use it. `principal` is in the signature, so the method *looks* security-aware. An unused
parameter is one of the easiest things in a codebase to overlook.

### Detecting it faster next time
- **For every write endpoint, list the request fields that identify a principal or an owned
  resource, and check each one against the token.** Usually a two-minute exercise.
- An unused `principal`/`userId` parameter is a red flag on its own; your IDE will grey it
  out.
- Test it with two accounts as a matter of routine: act as A, pass B's identifier.

### Prevention
- Derive identity from the principal; do not accept it as input.
- Separate endpoints for separate audiences, so privileged fields only exist on privileged
  routes.
- A negative test per endpoint: caller A supplying B's id must not affect B.

---

## H5 — The no-show sweep overwrites cancellations

### Symptom
Appointments that were properly cancelled days in advance appear as `NO_SHOW`. The
give-away is a `NO_SHOW` row that still carries its `cancellation_reason`. Patients are
about to be suspended from online booking on the strength of it.

### Root cause
```java
List<Appointment> elapsed = appointmentRepository.findByEndTimeBefore(cutoff);
for (Appointment appointment : elapsed) {
    if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
        appointment.setStatus(AppointmentStatus.NO_SHOW);
        marked++;
    }
}
```

Two mistakes compound. The query selects **every** appointment whose end time has passed,
regardless of status — including ones swept on previous runs. And the guard excludes only
`COMPLETED`, so `CANCELLED` and `NO_SHOW` both fall through and get rewritten.

The intent was "if it is not finished, the patient did not turn up". But `CANCELLED` *is*
finished, and the sweeper destroys that fact in place — there is no history table, so the
original status is gone. Only the orphaned `cancellation_reason` survives as evidence.

Because it runs on a cron every five minutes, it also re-marks rows it already marked, which
is why the `marked` count never settles at zero.

### Exact location
`src/main/java/com/harbourview/clinic/scheduling/NoShowSweeper.java`, the `sweep` method —
both the query and the condition.

### Correct fix
Select only the rows that are actually candidates, in the query:

```java
// AppointmentRepository
List<Appointment> findByStatusAndEndTimeBefore(AppointmentStatus status, LocalDateTime cutoff);
```

```java
List<Appointment> elapsed = appointmentRepository
        .findByStatusAndEndTimeBefore(AppointmentStatus.BOOKED, cutoff);
for (Appointment appointment : elapsed) {
    appointment.setStatus(AppointmentStatus.NO_SHOW);
    marked++;
}
```

Now the sweep is idempotent — a second run marks nothing — and it cannot reach a terminal
status. Expressing the rule as an allow-list (`only BOOKED`) rather than a deny-list
(`anything except COMPLETED`) is the point: a status added later is excluded by default
instead of being silently swept.

Two follow-ups worth doing: the already-corrupted rows need repairing before the practice
manager acts on the report — rows where `status = 'NO_SHOW' AND cancellation_reason IS NOT
NULL` are the identifiable victims — and a status-history row, or at least an `updated_by`
column, would have made this diagnosable in seconds instead of by inference.

### Affected components
`NoShowSweeper`, `AppointmentRepository`, the `appointments` table, the no-show report, and
the booking-suspension policy built on top of it.

### Underlying concept
**Guard a state machine with an allow-list of source states, never a deny-list.** "Anything
except `COMPLETED`" was true when the enum had three values and became false the moment a
fourth existed. "Only `BOOKED`" stays correct however the enum grows.

**Batch jobs must be idempotent.** A sweep that runs every five minutes will re-process the
same rows forever; its effect must be defined by the rows' current state, not by how many
times it has run. Filtering in the query rather than in the loop achieves that and makes the
job cheap at the same time.

And: **destructive in-place status updates erase the evidence you need to debug them.** The
only reason this was diagnosable at all is that a column from the previous state happened to
survive.

### Why this is realistic
The condition encodes a sentence somebody actually said in a planning meeting: "after the
slot, anything that is not completed is a no-show." That is true of the two statuses the
author had in mind. `CANCELLED` was handled elsewhere in the codebase and simply did not
come to mind here.

The cron schedule hides it further: by the time anyone looks, the status has been wrong for
hours and nobody associates it with a background job they never see run.

### Detecting it faster next time
- **Work backwards from the impossible combination.** A `NO_SHOW` carrying a cancellation
  reason cannot be produced by any legitimate path; find the one piece of code that writes
  that status and read it.
- For any status that appears without a corresponding user action, look for a scheduled job
  first. Grep for `@Scheduled`.
- Make jobs runnable on demand (this one is) so you can reproduce in one request instead of
  waiting for the cron.

### Prevention
- Allow-list source states in every transition, and consider a small explicit state machine
  that rejects illegal transitions centrally.
- Assert idempotence in a test: run the job twice, assert the second run changes nothing.
- Record transitions in a history table, so a wrong status is always traceable to the thing
  that wrote it.

---

## How these interact

**H2 feeds H5.** Booking in the past (H2) creates an appointment that is already elapsed,
so the very next sweep marks it `NO_SHOW` (H5) within five minutes. The reported symptom —
"no-shows for appointments that could never have been attended" — is two defects in a
chain, and fixing only the sweeper leaves the clinic with past-dated bookings instead.

**H1 and H3 both concern interval arithmetic in opposite directions.** H3 offers a slot the
doctor does not work; H1 lets a booking overlap one that exists. Fixing H3 reduces how often
H1 is triggered through the app, without fixing H1 at all — the desk books off-grid and will
still collide. A developer who fixes H3, sees the double-bookings stop for a week, and
closes CLIN-324 has been fooled by a correlation.

**H4 amplifies everything.** While a patient can book as anyone, all the other defects are
reachable against other people's records rather than only their own.

**Suggested fix order:** H4 (confidentiality, isolated), H5 (stops ongoing data corruption
and an unfair policy decision), H2 (stops bad rows being created), H3 (stops bad slots being
offered), H1 (the subtlest, and the one that needs the overlap predicate thought through
properly).

---

## What the test suite tells you

`mvn test` passes with all five defects live.

`ClinicMapperTest` checks field mapping, which was never wrong. `AppointmentRepositoryTest`
checks that the day-and-status query filters correctly, which it does — the defect is in the
*caller* that decides what to do with those rows, not in the query.

Nothing in the suite constructs two overlapping intervals, compares a wall clock against a
zone, counts generated slots, books as the wrong principal, or runs the sweeper. Those are
all behaviours that only appear when the pieces are assembled, which is exactly what an
integration test is for and exactly what this suite does not have.
