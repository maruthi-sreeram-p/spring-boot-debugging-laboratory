# API — Northgate HR Directory Service

Base URL: `http://localhost:8080`
Media type: `application/json`
Authentication: **HTTP Basic** (work email as username). The service is stateless — send
the `Authorization` header on every request. Every endpoint below requires authentication.

Error shape:

```json
{
  "timestamp": "2025-03-04T09:12:44.201Z",
  "status": 404,
  "error": "Not Found",
  "message": "Employee not found: 9999",
  "path": "/api/employees/9999",
  "details": ["salary: must be greater than or equal to 0"]
}
```

Status conventions:

| Status | Meaning |
|---|---|
| 400 | payload or query parameter is malformed or fails validation |
| 401 | missing or invalid credentials |
| 403 | authenticated but lacks the role the operation requires |
| 404 | no such employee or department |
| 409 | the record conflicts with an existing one (duplicate email) |

---

## Compensation visibility

`salary` is sensitive. The rule across the whole API is:

- **HR administrators** (`ROLE_HR_ADMIN`) see every salary.
- **Everyone else** sees their own salary and nobody else's.

When the caller is not entitled to a salary the field is **omitted from the JSON entirely**
rather than returned as `null`. This applies to every endpoint that returns an employee,
whether one at a time or in a list.

---

## Employees

### `GET /api/employees`
The directory search. Returns a page of employees matching **all** supplied filters.

*Auth:* any authenticated user.

| Query parameter | Type | Default | Meaning |
|---|---|---|---|
| `departmentId` | long | – | only employees in this department |
| `status` | `ACTIVE` \| `ON_LEAVE` \| `TERMINATED` | – | only employees with this status |
| `jobTitle` | string | – | exact job title, case-insensitive |
| `q` | string | – | case-insensitive substring of first name, last name or email |
| `page` | int | `0` | zero-based page index |
| `size` | int | `20` | page size, capped at 100 |
| `sort` | string | `lastName` | field to sort by |
| `direction` | `asc` \| `desc` | `asc` | sort direction |

Filters **combine with AND** and each one narrows the result set. Supplying
`departmentId=1&q=nair` returns only people in department 1 whose name or email contains
"nair" — never more results than `departmentId=1` alone would return.

`sort` accepts the fields the directory table displays: `fullName`, `email`, `jobTitle`,
`departmentName`, `managerName`, `hireDate`, `employmentStatus`.

Response `200 OK`:
```json
{
  "content": [
    {
      "id": 12,
      "employeeCode": "NG-1012",
      "fullName": "Aarav Nair",
      "email": "aarav.nair@northgate.test",
      "phone": "+91-9812345678",
      "jobTitle": "Senior Software Engineer",
      "departmentName": "Engineering",
      "managerName": "Arjun Reddy",
      "employmentStatus": "ACTIVE",
      "hireDate": "2021-04-12"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 60,
  "totalPages": 3,
  "last": false
}
```

| Status | When |
|---|---|
| 200 | page returned |
| 400 | a query parameter cannot be parsed, or `sort` names a field that is not sortable |
| 401 | not authenticated |

### `GET /api/employees/{employeeId}`
One employee.

*Auth:* any authenticated user.

| Status | When |
|---|---|
| 200 | employee returned |
| 401 | not authenticated |
| 404 | no such employee |

### `GET /api/employees/{employeeId}/reports`
The direct reports of one employee, sorted by last name. Used to draw the org chart, so
salaries are never included regardless of who is asking.

*Auth:* any authenticated user.

| Status | When |
|---|---|
| 200 | list returned, possibly empty |
| 401 | not authenticated |
| 404 | no such employee |

### `POST /api/employees`
Creates an employee record and allocates the next employee code.

*Auth:* **`ROLE_HR_ADMIN` only.**

```json
{
  "firstName": "Nadia",
  "lastName": "Khan",
  "email": "nadia.khan@northgate.test",
  "phone": "+91-9811122334",
  "jobTitle": "Software Engineer",
  "departmentId": 1,
  "managerId": 7,
  "hireDate": "2024-06-03",
  "salary": 1850000.00
}
```

`managerId` is optional — omit it for someone at the top of the tree. New employees start
`ACTIVE`.

Response `201 Created`: the created employee.

| Status | When |
|---|---|
| 201 | employee created |
| 400 | payload fails validation |
| 401 | not authenticated |
| 403 | authenticated but not an HR administrator |
| 404 | the department or the manager does not exist |
| 409 | the email is already in use |

### `PUT /api/employees/{employeeId}`
Updates an employee record.

*Auth:* **`ROLE_HR_ADMIN` only.**

```json
{
  "firstName": "Priyanka",
  "lastName": "Chandra",
  "email": "priyanka.chandra@northgate.test",
  "phone": "+91-9800000001",
  "jobTitle": "Senior Account Executive",
  "departmentId": 2,
  "managerId": 18,
  "salary": 2100000.00,
  "employmentStatus": "ACTIVE"
}
```

`managerId` is optional and **omitting it leaves the existing reporting line untouched** —
the HR console only sends it on the "change manager" screen, and sends the rest of the
fields from whichever edit form the user was on. Everything else in the payload is
required and replaces the stored value.

Response `200 OK`: the updated employee.

| Status | When |
|---|---|
| 200 | employee updated |
| 400 | payload fails validation |
| 401 | not authenticated |
| 403 | authenticated but not an HR administrator |
| 404 | the employee, department or manager does not exist |

### `DELETE /api/employees/{employeeId}`
Marks an employee as `TERMINATED`. The row is kept — payroll, history and the org chart
all need it — so this is a status change, not a delete.

*Auth:* **`ROLE_HR_ADMIN` only.**

| Status | When |
|---|---|
| 204 | employee marked terminated |
| 401 | not authenticated |
| 403 | authenticated but not an HR administrator |
| 404 | no such employee |

---

## Departments

### `GET /api/departments`
Every department with its current headcount, sorted by name.

*Auth:* any authenticated user.

```json
[
  { "id": 5, "code": "SUP", "name": "Customer Support", "location": "Hyderabad", "headcount": 11 },
  { "id": 1, "code": "ENG", "name": "Engineering", "location": "Bengaluru", "headcount": 13 }
]
```

### `GET /api/departments/{departmentId}/employees`
A page of the employees in one department, sorted by last name.

*Auth:* any authenticated user.

| Query parameter | Type | Default |
|---|---|---|
| `page` | int | `0` |
| `size` | int | `20` |

| Status | When |
|---|---|
| 200 | page returned |
| 401 | not authenticated |
| 404 | no such department |

---

## Profile

### `GET /api/me`
The employee record of the caller, including their own salary.

*Auth:* any authenticated user. `200` / `401`.

---

## Operational endpoints

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | none | liveness |
| `GET /actuator/info` | none | build info |
