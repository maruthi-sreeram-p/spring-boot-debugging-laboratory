# Debugging Guide — Harbourview Clinic Appointment Service

Read this file, then go to the code. Do **not** open `SOLUTION.md`.

---

## 1. Business context

Harbourview is a single-site outpatient clinic. `clinic-appointment-service` decides who
sees which doctor when.

- **Patients** book through the app: pick a doctor, pick a day, pick a slot.
- **Doctors** work from the schedule the system gives them. A double-booked slot means two
  people in the waiting room for the same twenty minutes and a clinic running late all
  afternoon.
- **The scheduling desk** books walk-ins and follow-ups directly by time rather than from
  the slot grid, chases no-shows, and runs the end-of-day reports.
- **The practice manager** reads the no-show report. It feeds a policy: three no-shows and
  a patient loses online booking.

Two things matter more than they look. A patient marked as a no-show when they in fact
cancelled properly is treated unfairly by that policy. And a slot offered to a patient that
the doctor is not actually working is a promise the clinic cannot keep.

---

## 2. How the system is supposed to behave

**Slots come from the working window and never leave it.** A doctor working 09:00–13:00 in
30-minute consultations has eight slots: 09:00, 09:30 … 12:30. The last one finishes at
exactly 13:00. Nothing is ever offered that runs past the end of the session.

**The past is not bookable.** An appointment must start at least 30 minutes from now. A
time earlier today has already gone; the API must refuse it. Likewise nothing more than 60
days out.

**A doctor cannot be in two places at once.** Two appointments for the same doctor clash if
their intervals overlap by even one minute. This is a property of the two intervals, so it
cannot depend on which one happened to be created first. Booking A then B and booking B
then A must produce the same answer.

**A patient acts only as themselves.** When a patient books, the appointment is for that
patient. Only the scheduling desk may book on behalf of somebody else.

**Terminal statuses are terminal.** `COMPLETED`, `CANCELLED` and `NO_SHOW` are final. The
no-show sweep exists to close out appointments still sitting in `BOOKED` after their slot
has passed. It must not touch anything that already reached a conclusion — a patient who
cancelled correctly did not fail to attend.

---

## 3. Symptoms

Nobody has told you how many distinct defects there are.

---

### Ticket CLIN-311 — "The 13:00 slot does not exist"

> Filed by: Dr Deshpande
>
> I finish at 13:00. The app is offering patients a 13:00 appointment with me, which would
> run to 13:30. I have a ward round at 13:15.
>
> Two patients have booked it this month. Both times the front desk had to phone and move
> them, which is exactly the call we are trying to avoid making.

---

### Ticket CLIN-318 — "Appointments booked for times that have already happened"

> Filed by: Scheduling desk
>
> We have appointments in today's list with start times earlier this morning. They were
> created *after* those times had passed — you can see it in `created_at`.
>
> Worse, they turn up in the no-show report almost immediately, because by the time anyone
> looks they are already long past. So we are generating no-shows for appointments that
> could never have been attended.
>
> One of our developers tried to reproduce this on a machine set to UTC and could not. It
> happens here every time.

---

### Ticket CLIN-324 — "Two patients, one slot, sometimes"

> Filed by: Dr Ellery
>
> I had two patients arrive for 14:20 last Friday. My consultations are 40 minutes, so the
> 14:00 appointment and the 14:20 appointment cannot both exist.
>
> I asked the desk to try and break it deliberately. They found that if they book 14:00
> first and then 14:20, both go through. If they do it the other way round — 14:20 first,
> then 14:00 — the second one is correctly refused.
>
> The same two times. Only the order is different. I do not understand how that can matter.

---

### Ticket CLIN-330 — "An appointment I did not make"

> Filed by: A patient, via the front desk
>
> A dermatology appointment appeared in my list. I did not book it. The desk says it was
> created through the patient app, not by them.
>
> Separately, and I do not know whether it is related: my friend and I were comparing the
> app. She could see my full name and my MRN on her screen after she tried booking
> something.

---

### Ticket CLIN-336 — "Cancelled patients are being flagged as no-shows"

> Filed by: Practice manager
>
> I am about to suspend three patients from online booking under the no-show policy. Before
> I did, I checked the records. All three cancelled their appointments properly, days in
> advance. One of them still has the cancellation reason we typed in, sitting on a row that
> now says NO_SHOW.
>
> I need to know how many of my no-show statistics are real before I act on any of them.

---

## 4. Investigation hints

Read **one** hint at a time and go back to the code before reading the next.

---

### CLIN-311 — a slot past the end of the session

**Hint 1.** Call the slots endpoint for a doctor and count what comes back against what the
working window says should exist. Get the exact number before you open any code.

**Hint 2.** The slots are produced by a single loop. Read its termination condition and ask
what it does when the cursor lands exactly on the closing time.

**Hint 3.** The working window is a half-open interval — the doctor is available *up to*
13:00, not *including* it. Decide which comparison expresses that, and check whether the
slot length is accounted for as well as the start.

---

### CLIN-318 — bookings accepted for the past

**Hint 1.** The rule is implemented; find it and read it before assuming it is missing.

**Hint 2.** The developer who could not reproduce it on a UTC machine has given you the
answer. What is different about this machine, and where would that difference enter the
comparison?

**Hint 3.** Appointment times are clinic-local wall-clock values with no offset. The
comparison needs a "now" of the same kind. Look at exactly how "now" is constructed, and
work out which zone it ends up expressed in.

**Hint 4.** Log both sides of the comparison and read them next to `date`:

```
logging.level.com.harbourview.clinic=TRACE
```

The size of the discrepancy will tell you which zone was used, and that names the bug.

---

### CLIN-324 — clash detection depends on booking order

**Hint 1.** Order-dependence in a symmetric question means the test being applied is not
symmetric. Write down, on paper, the two intervals for both orderings and the condition the
code evaluates for each.

**Hint 2.** There are four ways two intervals can overlap: identical, the new one starting
inside the old, the old one starting inside the new, and one containing the other. Check
the condition against all four.

**Hint 3.** The code only ever examines one of the two endpoints of the existing
appointment. Ask what information is being thrown away.

**Hint 4.** The correct overlap test is a well-known two-comparison expression. Derive it
by negating "they do not overlap" rather than by enumerating cases — you will get it right
first time and it will be obviously symmetric.

**Hint 5.** Note that the slots endpoint and the booking endpoint each decide overlap for
themselves. Compare the two.

---

### CLIN-330 — an appointment the patient did not book

**Hint 1.** Look at the booking request payload in `API.md`, then at what the service does
with each field of it. Ask which of those fields the server should have taken on trust.

**Hint 2.** The endpoint legitimately serves two kinds of caller with different rights. Find
where that distinction is made in the code. If you cannot find it, that is the finding.

**Hint 3.** The second half of the ticket — the friend seeing a name and MRN — is not a
separate defect. Work out how the same cause produces it, and you will have confirmed your
diagnosis without needing to read any more code.

---

### CLIN-336 — cancelled appointments flipped to no-show

**Hint 1.** The practice manager handed you the physical evidence: a `NO_SHOW` row still
carrying a cancellation reason. Only one piece of code writes `NO_SHOW`. Go straight to it.

**Hint 2.** Read the condition that decides whether a row gets marked. List the statuses it
lets through, not the ones it excludes.

**Hint 3.** Reproduce it on demand rather than waiting for the cron:

```bash
curl -s -u 'scheduling.desk@harbourview.test:Admin123!' \
  -X POST http://localhost:8080/api/back-office/jobs/no-show-sweep
```

Cancel a past appointment, run that, and watch the status change.

**Hint 4.** Once you have fixed it, think about the damage already done. The statuses in the
database are wrong and a policy is about to be applied to them. What evidence is left that
would let you identify which rows were flipped, and what does that tell you about what the
sweeper should have recorded?

---

## 5. Before you call it fixed

- Count the slots for each doctor against their working window: Dr Deshpande weekdays
  (09:00–13:00, 30 min) should give **8**, Saturdays (09:00–12:00) **6**, Dr Ellery
  (14:00–18:00, 40 min) **6**, Dr Keller Tuesdays (10:00–16:00, 20 min) **18**.
- Try to book a time earlier today, and a time twenty minutes from now. Both must be
  refused. Then book something an hour out and confirm it succeeds.
- Test the clash matrix in both orders, for every overlap shape: same start, new inside
  old, old inside new, and one containing the other. Every case must be refused, both ways
  round.
- As a patient, try to book with another patient's id. Then as the scheduling desk, book on
  behalf of a patient and confirm that still works. A fix that blocks the desk is not a fix.
- Cancel a past appointment, run the sweep, and confirm it is still `CANCELLED`. Then leave
  a past appointment in `BOOKED`, run the sweep, and confirm it becomes `NO_SHOW`.
- Re-run `mvn test`.

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass hospitaldb -e "
SELECT a.doctor_id, a.reference, a.start_time, b.reference AS other, b.start_time AS other_start
  FROM appointments a JOIN appointments b
    ON b.doctor_id = a.doctor_id AND b.id > a.id
   AND b.start_time < a.end_time AND b.end_time > a.start_time
 WHERE a.status='BOOKED' AND b.status='BOOKED';"
```

That query must return nothing.

When you are done, ask for **verification mode** and I will check your work.
