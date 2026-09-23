# Harbourview Clinic — Appointment Service

`clinic-appointment-service` is the outpatient scheduling backend for Harbourview Clinic.
It owns patients, doctors, weekly availability, and the appointments booked against it. It
backs the patient booking app, the doctor's own schedule view, and the scheduling desk's
back-office console.

This is a **debugging lab project**. The application is feature-complete and starts
cleanly, but it does not behave correctly in every case. Work from `DEBUGGING_GUIDE.md`.
Do not open `SOLUTION.md` until you have finished.

---

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| Web | Spring MVC, REST/JSON |
| Security | Spring Security, HTTP Basic, BCrypt, stateless, `@EnableMethodSecurity` |
| Persistence | Spring Data JPA, Hibernate 6, MySQL 8 |
| Time | `LocalDate` / `LocalTime` / `LocalDateTime` in clinic-local time; JVM pinned to `Asia/Kolkata` |
| Jobs | `@EnableScheduling`, cron-driven no-show sweep |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.harbourview.clinic
├── config/        SecurityConfig, ClinicProperties
├── controller/    Auth, Doctor, Appointment, BackOffice
├── dto/           request and response payloads, PagedResponse, ApiError
├── entity/        Patient, Doctor, DoctorSchedule, Appointment, AppUser
├── exception/     domain exceptions + GlobalExceptionHandler
├── mapper/        entity to DTO translation
├── repository/    Spring Data JPA repositories
├── scheduling/    NoShowSweeper
├── security/      ClinicUser, ClinicUserDetailsService
└── service/       ScheduleService, AppointmentService
```

**Time model.** The clinic operates in one place, so appointment times are stored as
`DATETIME` and handled as `LocalDateTime` in clinic-local time. `ClinicApplication` pins
the JVM default zone to `Asia/Kolkata` at startup so a node in another region still reads
and writes the same wall-clock times.

**Availability model.** `doctor_schedules` holds one row per doctor per weekday with a
start time, an end time and a slot length. Concrete slots are generated on demand for a
requested date; they are not stored. The booking endpoint accepts a start time directly, so
the scheduling desk can also book off-grid follow-ups and walk-ins.

**Status model.** `BOOKED → COMPLETED`, `BOOKED → CANCELLED`, and `BOOKED → NO_SHOW` (by
the sweeper). `COMPLETED`, `CANCELLED` and `NO_SHOW` are terminal.

---

## Running it

### 1. Backing services

```bash
docker compose -f infra/docker-compose.yml up -d mysql
```

MySQL comes up on `localhost:3307` with a `hospitaldb` database.

### 2. Configuration

`src/main/resources/application.yml` holds placeholders (`YOUR_DATABASE_HOST`,
`YOUR_DATABASE_USER`, `YOUR_DATABASE_PASSWORD`) and reads every value from the
environment. The `local` profile — active by default — fills them in with the credentials
from `infra/docker-compose.yml`.

Scheduling rules live under the `clinic:` key: 30 minutes minimum booking notice, 60 days
maximum lead time, 2 hours minimum cancellation notice, and a no-show sweep every five
minutes with a 20-minute grace period.

### 3. Start

```bash
mvn spring-boot:run
```

Schema and seed are applied on every start; both are idempotent.

### 4. Sign in

| Account | Password | Role |
|---|---|---|
| `rhea.sundaram@example.com` | `Password123!` | patient 1 (`MRN-100241`) |
| `joseph.mwangi@example.com` | `Password123!` | patient 2 (`MRN-100258`) |
| `lucia.moretti@example.com` | `Password123!` | patient 3 (`MRN-100263`) |
| `anita.deshpande@harbourview.test` | `Password123!` | Dr Deshpande, General Medicine, 30-minute consultations |
| `mark.ellery@harbourview.test` | `Password123!` | Dr Ellery, Cardiology, 40-minute consultations |
| `scheduling.desk@harbourview.test` | `Admin123!` | scheduling desk (`ROLE_OPS`) |

Doctors and their hours:

| Doctor | Consultation | Availability |
|---|---|---|
| Dr Anita Deshpande | 30 min | Mon–Fri 09:00–13:00, Sat 09:00–12:00, Sun 10:00–12:00 |
| Dr Mark Ellery | 40 min | Mon / Wed / Fri 14:00–18:00 |
| Dr Sofia Keller | 20 min | Tue / Thu 10:00–16:00, Sat / Sun 10:00–14:00 |
| Dr Ravi Balakrishna | 30 min | inactive, not taking appointments |

---

## Exercising it

```bash
BASE=http://localhost:8080
PATIENT='rhea.sundaram@example.com:Password123!'
OPS='scheduling.desk@harbourview.test:Admin123!'
DAY=$(date -d "+1 day" +%Y-%m-%d)

curl -s -u "$PATIENT" "$BASE/api/doctors"
curl -s -u "$PATIENT" "$BASE/api/doctors/1/slots?date=$DAY"

curl -s -u "$PATIENT" -X POST "$BASE/api/appointments" \
  -H 'Content-Type: application/json' \
  -d "{\"doctorId\":1,\"patientId\":1,\"startTime\":\"${DAY}T10:00:00\",\"reason\":\"Persistent cough\"}"

curl -s -u "$PATIENT" "$BASE/api/appointments"
curl -s -u "$OPS" "$BASE/api/back-office/doctors/1/day-sheet?date=$DAY"
curl -s -u "$OPS" -X POST "$BASE/api/back-office/jobs/no-show-sweep"
```

The sweep endpoint runs the same code as the cron job, so you can exercise it on demand
instead of waiting five minutes. Full contracts are in [API.md](API.md).

---

## Inspecting state while you debug

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass hospitaldb \
  -e "select id, reference, doctor_id, patient_id, start_time, end_time, status from appointments order by start_time;"
```

Any doctor double-booked at the same moment:

```sql
SELECT a.doctor_id, a.reference, a.start_time, a.end_time,
       b.reference AS other, b.start_time AS other_start, b.end_time AS other_end
  FROM appointments a
  JOIN appointments b
    ON b.doctor_id = a.doctor_id AND b.id > a.id
   AND b.start_time < a.end_time AND b.end_time > a.start_time
 WHERE a.status = 'BOOKED' AND b.status = 'BOOKED';
```

What the clock actually says inside the JVM versus the database:

```bash
curl -s http://localhost:8080/actuator/info
docker exec -it lab-mysql mysql -ulabuser -plabpass -e "select now(), @@global.time_zone, @@session.time_zone;"
date
```

SQL is logged at `DEBUG`. For scheduling decisions, `com.harbourview.clinic` is at `DEBUG`
too — the slot generator and the sweeper both log what they produced.

---

## Tests

```bash
mvn test
```

The suite covers appointment mapping and a repository slice against H2. It passes.
