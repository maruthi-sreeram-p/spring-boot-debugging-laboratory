# Northgate — HR Directory Service

`hr-directory-service` is the people directory behind Northgate's internal tools. It owns
employees, departments and the reporting lines between them, and it backs three things: the
staff directory everyone uses to look each other up, the org-chart view, and the HR
admin console where the People Operations team maintains records.

This is a **debugging lab project**. The application is feature-complete and starts
cleanly, but it does not behave correctly in every case. Work from `DEBUGGING_GUIDE.md`.
Do not open `SOLUTION.md` until you have finished.

---

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| Web | Spring MVC, REST/JSON |
| Security | Spring Security, HTTP Basic, BCrypt, stateless, method-level rules |
| Persistence | Spring Data JPA, Hibernate 6, MySQL 8 |
| Querying | Derived queries plus `JpaSpecificationExecutor` for the dynamic search |
| Build | Maven |
| Tests | JUnit 5, AssertJ, H2 for the repository slice |

---

## Layout

```
com.northgate.hr
├── config/        SecurityConfig, DirectoryProperties
├── controller/    Employee, Department, Profile
├── dto/           request and response payloads, PagedResponse, ApiError
├── entity/        Employee, Department, AppUser, Role, EmploymentStatus
├── exception/     domain exceptions + GlobalExceptionHandler
├── mapper/        entity to DTO translation
├── repository/    Spring Data JPA repositories
├── security/      DirectoryUser, DirectoryUserDetailsService
└── service/       EmployeeService, DepartmentService, EmployeeSpecifications
```

**Authorization model.** The filter chain only separates anonymous traffic from
authenticated traffic — everything past `/actuator/health` requires a login. Anything
finer grained is decided in the service layer with `@PreAuthorize`, next to the business
rule it protects. Three roles exist: `ROLE_EMPLOYEE` (everyone), `ROLE_MANAGER` (people
with direct reports) and `ROLE_HR_ADMIN` (People Operations).

**Data model.** `employees.manager_id` is a self-referencing foreign key, so the org chart
is a tree inside one table. Every employee belongs to exactly one department. Sign-in
accounts live in `users` and point at an employee row, so not every employee necessarily
has a login.

---

## Running it

### 1. Backing services

From the repository root:

```bash
docker compose -f infra/docker-compose.yml up -d mysql
```

MySQL comes up on `localhost:3307` with an `hrdb` database.

### 2. Configuration

`src/main/resources/application.yml` holds placeholders (`YOUR_DATABASE_HOST`,
`YOUR_DATABASE_USER`, `YOUR_DATABASE_PASSWORD`) and reads every value from the
environment. The `local` profile — active by default — fills them in with the credentials
from `infra/docker-compose.yml`.

To point the service at your own database:

```bash
export SPRING_PROFILES_ACTIVE=default
export MYSQL_HOST=... MYSQL_USER=... MYSQL_PASSWORD=...
```

### 3. Start

```bash
mvn spring-boot:run
```

Schema and seed are applied on every start from `schema.sql` and `data.sql`; both are
idempotent. The seed is a 60-person company across five departments, three levels deep:
one managing director, five directors, twelve team leads and forty-two individual
contributors. Two people are `ON_LEAVE` and two are `TERMINATED`.

### 4. Sign in

| Account | Password | Roles | Who they are |
|---|---|---|---|
| `aarav.nair@northgate.test` | `Password123!` | `ROLE_EMPLOYEE` | ordinary employee, no reports |
| `deepak.krishnan@northgate.test` | `Password123!` | `ROLE_EMPLOYEE` | ordinary employee |
| `arjun.reddy@northgate.test` | `Password123!` | `ROLE_EMPLOYEE`, `ROLE_MANAGER` | Director of Engineering |
| `ishita.sheikh@northgate.test` | `Password123!` | `ROLE_EMPLOYEE`, `ROLE_MANAGER` | Managing Director |
| `imran.dsouza@northgate.test` | `Admin123!` | `ROLE_EMPLOYEE`, `ROLE_HR_ADMIN` | People Operations |

Authentication is HTTP Basic on every call.

---

## Exercising it

```bash
BASE=http://localhost:8080
AUTH='aarav.nair@northgate.test:Password123!'
HR='imran.dsouza@northgate.test:Admin123!'

curl -s -u "$AUTH" "$BASE/api/employees?size=5&sort=lastName"
curl -s -u "$AUTH" "$BASE/api/employees?departmentId=1&status=ACTIVE"
curl -s -u "$AUTH" "$BASE/api/employees?q=nair"
curl -s -u "$AUTH" "$BASE/api/employees/2/reports"
curl -s -u "$AUTH" "$BASE/api/departments"
curl -s -u "$AUTH" "$BASE/api/me"

curl -s -u "$HR" -X POST "$BASE/api/employees" \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Nadia","lastName":"Khan","email":"nadia.khan@northgate.test",
       "jobTitle":"Software Engineer","departmentId":1,"managerId":7,
       "hireDate":"2024-06-03","salary":1850000.00}'
```

Full endpoint contracts are in [API.md](API.md).

---

## Inspecting state while you debug

**MySQL** — Adminer is on <http://localhost:8081> (server `mysql`, user `labuser`,
database `hrdb`), or from the shell:

```bash
docker exec -it lab-mysql mysql -ulabuser -plabpass hrdb \
  -e "select id, employee_code, first_name, last_name, department_id, manager_id, employment_status from employees order by id limit 20;"
```

The org chart as a tree:

```sql
SELECT e.id, e.first_name, e.last_name, e.job_title,
       m.first_name AS manager_first, m.last_name AS manager_last
  FROM employees e
  LEFT JOIN employees m ON m.id = e.manager_id
 ORDER BY e.manager_id, e.last_name;
```

Anyone who has fallen out of the org chart:

```sql
SELECT id, employee_code, first_name, last_name, employment_status
  FROM employees WHERE manager_id IS NULL;
```

**SQL logging** is already at `DEBUG` for `org.hibernate.SQL`. Counting the statements a
single request produces is a one-liner:

```bash
BEFORE=$(wc -l < app.log); curl -s -o /dev/null -u "$AUTH" "$BASE/api/employees?size=20"; \
  tail -n +$BEFORE app.log | grep -c select
```

**Which security rules actually ran** — turn this up when an authorization decision
surprises you:

```
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.security.access=TRACE
```

---

## Tests

```bash
mvn test
```

The suite covers directory mapping and a repository slice against H2. It passes. That
tells you the build is sound; it does not tell you the directory is correct.
