# API — Harbourview Clinic Appointment Service

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic**. Stateless — send the `Authorization` header on every call.

All times are **clinic-local** (`Asia/Kolkata`) and carry no offset, e.g.
`"2025-06-12T10:30:00"`. Dates are `yyyy-MM-dd`.

Error shape:

```json
{
  "timestamp": "2025-06-10T09:12:44.201Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Appointments need at least 30 minutes notice",
  "path": "/api/appointments",
  "details": ["reason: must not be blank"]
}
```

| Status | Meaning |
|---|---|
| 400 | payload or parameter malformed or fails validation |
| 401 | missing or invalid credentials |
| 403 | authenticated but lacks the role |
| 404 | not found, or not visible to the caller |
| 409 | slot already taken, or an illegal status transition |
| 422 | well formed but breaks a scheduling rule (notice period, lead time, inactive doctor) |

---

## Roles

| Role | Who | May do |
|---|---|---|
| `ROLE_PATIENT` | a patient | book, view and cancel **their own** appointments |
| `ROLE_DOCTOR` | a doctor | view **their own** appointments, mark them completed or cancelled |
| `ROLE_OPS` | scheduling desk | everything, including booking on behalf of any patient and running jobs |

A patient may only ever act as themselves. A booking request from a patient is for that
patient, whatever identifiers the payload carries. Only the scheduling desk may book on
behalf of somebody else.

---

## Session

### `GET /api/auth/me`
Who the caller is and which patient or doctor record they are attached to.

*Auth:* any authenticated user. `200` / `401`.

```json
{ "username": "rhea.sundaram@example.com", "role": "ROLE_PATIENT", "patientId": 1, "doctorId": null }
```

---

## Doctors and availability

### `GET /api/doctors`
Every doctor currently taking appointments, by name.

*Auth:* any authenticated user. `200`.

### `GET /api/doctors/{doctorId}`
One doctor, active or not. `200` / `404`.

### `GET /api/doctors/{doctorId}/slots?date=yyyy-MM-dd`
The bookable slots for one doctor on one date, generated from their weekly availability.

*Auth:* any authenticated user.

A slot is emitted for each consultation-length step **within** the working window. A doctor
who works 09:00–13:00 in 30-minute slots has eight slots, the first starting 09:00 and the
last starting 12:30 and finishing exactly at 13:00. No slot ever extends past the end of
the working window. A day with no availability returns an empty list.

`available` is `false` when a `BOOKED` appointment already overlaps that slot.

```json
{
  "doctorId": 1,
  "doctorName": "Dr Anita Deshpande",
  "date": "2025-06-12",
  "dayOfWeek": "THURSDAY",
  "slots": [
    { "startTime": "2025-06-12T09:00:00", "endTime": "2025-06-12T09:30:00", "available": true },
    { "startTime": "2025-06-12T09:30:00", "endTime": "2025-06-12T10:00:00", "available": false }
  ]
}
```

| Status | When |
|---|---|
| 200 | slots returned, possibly empty |
| 400 | `date` is missing or unparseable |
| 404 | no such doctor |

---

## Appointments

### `POST /api/appointments`
Books an appointment. The end time is derived from the doctor's consultation length.

*Auth:* patient (for themselves), or `ROLE_OPS` (for anyone).

```json
{
  "doctorId": 1,
  "patientId": 1,
  "startTime": "2025-06-12T10:00:00",
  "reason": "Persistent cough"
}
```

Rules enforced:

- The doctor must be active.
- `startTime` must be at least **30 minutes in the future** — an appointment that has
  already started, or already finished, is never bookable.
- `startTime` must be no more than **60 days** ahead.
- The doctor must be free. An appointment clashes if its interval overlaps an existing
  `BOOKED` appointment for the same doctor **by even one minute**, in either direction, no
  matter which of the two was created first.

Response `201 Created`:
```json
{
  "id": 6,
  "reference": "APT-20250612-4188",
  "doctorId": 1,
  "doctorName": "Dr Anita Deshpande",
  "patientId": 1,
  "patientName": "Rhea Sundaram",
  "patientMrn": "MRN-100241",
  "startTime": "2025-06-12T10:00:00",
  "endTime": "2025-06-12T10:30:00",
  "status": "BOOKED",
  "reason": "Persistent cough",
  "cancellationReason": null
}
```

| Status | When |
|---|---|
| 201 | booked |
| 400 | payload fails validation |
| 401 | not authenticated |
| 404 | no such doctor or patient |
| 409 | the doctor is already booked across that interval |
| 422 | doctor inactive, too little notice, in the past, or too far ahead |

### `GET /api/appointments`
The caller's own appointments, newest first. A doctor gets the appointments where they are
the doctor; a patient gets the ones where they are the patient.

| Query parameter | Type | Default |
|---|---|---|
| `page` | int | `0` |
| `size` | int | `20` |

`200` / `401`.

### `GET /api/appointments/{appointmentId}`
One appointment the caller is party to. An appointment belonging to somebody else is
indistinguishable from one that does not exist.

`200` / `401` / `404`.

### `POST /api/appointments/{appointmentId}/cancel`
Cancels a `BOOKED` appointment.

```json
{ "reason": "Patient rescheduled" }
```

Patients and doctors must cancel at least **2 hours** before the start time; the scheduling
desk is exempt. A cancelled appointment keeps its `cancellationReason` and never changes
status again.

| Status | When |
|---|---|
| 200 | cancelled |
| 400 | `reason` is blank |
| 404 | not found or not visible to the caller |
| 409 | the appointment is not `BOOKED` |
| 422 | inside the 2-hour cancellation window |

### `POST /api/appointments/{appointmentId}/complete`
Marks a `BOOKED` appointment as `COMPLETED` after the consultation.

`200` / `404` / `409`.

---

## Back office

*Auth for this whole section:* `ROLE_OPS`.

### `GET /api/back-office/doctors/{doctorId}/day-sheet?date=yyyy-MM-dd`
Every appointment for one doctor on one date, in time order, whatever its status. `200`.

### `POST /api/back-office/jobs/no-show-sweep`
Runs the no-show sweep immediately instead of waiting for its cron.

The sweep looks at appointments whose end time has passed by more than the grace period
(20 minutes) and marks the ones the patient did not attend as `NO_SHOW`. Appointments that
already reached a terminal status — `COMPLETED`, `CANCELLED`, `NO_SHOW` — are finished
business and the sweep leaves them exactly as they are.

```json
{ "marked": 2 }
```

`200` / `403`.

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness |
| `GET /actuator/info` | none | build info |
