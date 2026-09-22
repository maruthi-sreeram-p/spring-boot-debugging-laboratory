# SOLUTION — Northgate HR Directory Service

> **Answer key. Do not read this until you have finished investigating.**

Six defects. All are deterministic and reproducible with a single request each.

| # | Ticket | One-line summary | File |
|---|---|---|---|
| E1 | HR-104 | Client-supplied `sort` is passed straight to `Sort.by`, so DTO field names blow up | `controller/EmployeeController.java` |
| E2 | HR-109 | The search specification joins its predicates with OR instead of AND | `service/EmployeeSpecifications.java` |
| E3 | HR-115 | The salary rule is applied on the detail path but not on the list path | `service/EmployeeService.java` |
| E4 | HR-118 | `@EnableMethodSecurity(prePostEnabled = false)` makes every `@PreAuthorize` inert | `config/SecurityConfig.java` |
| E5 | HR-122 | `update` treats a missing `managerId` as "clear the manager" | `service/EmployeeService.java` + `dto/UpdateEmployeeRequest.java` |
| E6 | HR-127 | N+1: `department` and `manager` are lazily loaded per row by the mapper | `service/EmployeeService.java` + `mapper/EmployeeMapper.java` |

---

## E1 — Sorting by a displayed column returns 500

### Symptom
`GET /api/employees?sort=fullName` and `?sort=managerName` return `500`. `?sort=lastName`,
`?sort=hireDate` and — confusingly — `?sort=departmentName` all return `200`.

### Root cause
The controller hands the raw request parameter to Spring Data:

```java
Sort.Direction sortDirection = Sort.Direction.fromString(direction);
Pageable pageable = PageRequest.of(page, pageSize, Sort.by(sortDirection, sort));
```

`Sort` is resolved against the **entity**, not the response DTO. `Employee` has
`firstName`, `lastName`, `department`, `manager` — it has no `fullName` and no
`managerName`, because those are computed by `EmployeeMapper`. Spring Data throws
`PropertyReferenceException`, the catch-all handler in `GlobalExceptionHandler` turns any
unmapped exception into `500 Unexpected error`, and the caller is told the server broke
when in fact the request was invalid.

The part worth understanding is why `departmentName` works. Spring Data does not simply
look for a property called `departmentName`; when that fails it **splits the camel-case
name and walks the property path**. `departmentName` becomes `department` + `name`, and
since `Department` has a `name`, it resolves to `department.name` and sorts correctly.
`managerName` becomes `manager` + `name` — but `Employee` has no `name` property, so it
fails. `fullName` becomes `full` + `Name`, and there is no `full` property either.

So one of the three virtual columns works by accident, which is exactly the kind of
inconsistency that makes a bug report confusing: "sorting is broken" is not true, and
"sorting by computed fields is broken" is not true either.

### Exact location
`src/main/java/com/northgate/hr/controller/EmployeeController.java`, the `Sort.by(...)`
call in `search`, plus the catch-all handler in
`src/main/java/com/northgate/hr/exception/GlobalExceptionHandler.java`.

### Correct fix
Translate the client's column key into an entity property path, and reject anything not on
the list:

```java
private static final Map<String, String> SORTABLE = Map.of(
        "fullName",         "lastName",
        "email",            "email",
        "jobTitle",         "jobTitle",
        "departmentName",   "department.name",
        "managerName",      "manager.lastName",
        "hireDate",         "hireDate",
        "employmentStatus", "employmentStatus");

String property = SORTABLE.get(sort);
if (property == null) {
    throw new IllegalArgumentException("Cannot sort by " + sort);
}
Pageable pageable = PageRequest.of(page, pageSize, Sort.by(sortDirection, property));
```

`IllegalArgumentException` is already mapped to `400`, which is the right answer: the
request was bad, not the server.

Add a handler for `PropertyReferenceException` → `400` as a safety net, so that any future
path that leaks a raw sort field fails honestly.

Note that sorting by `manager.lastName` or `department.name` makes Hibernate add a join.
That is fine for `ManyToOne`, and it interacts with E6 — see there.

### Affected components
`EmployeeController`, `EmployeeService`, `GlobalExceptionHandler`, `EmployeeMapper` (which
defines the virtual fields), and the directory UI.

### Underlying concept
**A sort field is an input, and every input from a client must be validated against an
allow-list.** Passing it through untouched couples your public API to your entity model:
renaming a field becomes a breaking API change, and the set of legal values is undocumented
and undiscoverable.

There is a security dimension too. Sorting by an arbitrary property path lets a caller sort
by fields you never meant to expose, and — because the path can traverse associations —
force joins you did not anticipate. Ordering by a sensitive column leaks information about
it even when the column itself is not returned: sort by `salary` ascending and descending
and you have learned the ranking of everyone's pay without ever seeing a number. An
allow-list closes that.

Secondly: **a DTO is not an entity.** The moment you compute a response field, you have
created a name that exists on one side of the mapping and not the other, and anything that
crosses that boundary — sorting, filtering, projections — needs an explicit translation.

### Why this is realistic
`Sort.by(sort)` is what almost every tutorial shows, and it works perfectly for as long as
the DTO field names happen to match the entity. `fullName` is usually the first computed
field anyone adds, and it is usually added long after the sorting code was written, by
somebody working on the mapper who has no reason to look at the controller.

The accidental success of `departmentName` is what makes this genuinely realistic rather
than merely instructive. Spring Data's property-path resolution is helpful and mostly
invisible, and it means the failure is partial. Partial failures produce bad bug reports.

### Detecting it faster next time
- **Read the actual exception before theorising.** `PropertyReferenceException: No property
  'full' found for type 'Employee'` names the problem in one line. A generic 500 body is
  the handler talking, not the system; the log has the truth.
- When some values of a parameter work and others do not, enumerate them and look for the
  pattern. Here, that immediately produces the "it resolves through the association" clue.
- Treat any 500 as a bug in its own right, independent of what triggered it. A malformed
  request should never produce a 5xx; that mapping is itself a defect worth fixing.

### Prevention
- An allow-list map plus a test that iterates every documented sortable column and asserts
  `200`, and asserts `400` for an unknown one.
- Never let request-derived strings reach `Sort`, `Specification` paths, or JPQL.
- Narrow the catch-all handler, or at least log-and-classify before falling through, so that
  client errors are not silently reported as server errors.

---

## E2 — Every filter widens the result set

### Symptom
`?departmentId=1` → 13 results. `?q=nair` → 1 result. `?departmentId=1&q=nair` → 13
results. Adding `&status=ACTIVE` → 58 results. Each filter added makes the list longer.

### Root cause
`EmployeeSpecifications.matching` collects one `Predicate` per supplied filter and then
combines them:

```java
if (predicates.isEmpty()) {
    return builder.conjunction();
}
return builder.or(predicates.toArray(new Predicate[0]));
```

`builder.or` produces `WHERE department_id = 1 OR employment_status = 'ACTIVE' OR
(lower(first_name) LIKE '%nair%' OR ...)`. Matching any single condition is enough, so the
result set grows with every filter instead of shrinking.

The OR is not a random mistake. The name search genuinely needs one, a few lines above:

```java
predicates.add(builder.or(
        builder.like(builder.lower(root.get("firstName")), pattern),
        builder.like(builder.lower(root.get("lastName")), pattern),
        builder.like(builder.lower(root.get("email")), pattern)));
```

That inner OR is correct. The outer combiner was written to match it.

### Exact location
`src/main/java/com/northgate/hr/service/EmployeeSpecifications.java`, the final `return`.

### Correct fix
```java
return builder.and(predicates.toArray(new Predicate[0]));
```

`builder.and()` with an empty array is already a tautology, so the `isEmpty()` guard above
can go too — though keeping it is harmless and arguably clearer.

### Affected components
`EmployeeSpecifications`, `EmployeeService.search`, `EmployeeController`,
`DepartmentService.employeesOf` (which builds a criteria object with a single filter, and
therefore never revealed the bug), and the directory UI.

### Underlying concept
**Filters compose conjunctively; alternatives compose disjunctively.** A filter panel is a
conjunction of constraints, and each constraint may internally be a disjunction over the
fields it searches. Getting this right means keeping the two levels distinct: build each
constraint independently, however complex, then AND the constraints together.

There is a second lesson in why this survived: `DepartmentService.employeesOf` also uses
this specification, but only ever sets one filter. With one predicate, `or` and `and` are
identical. **A bug in a combiner is invisible until at least two things are combined**, and
most manual testing exercises one filter at a time.

### Why this is realistic
The two lines are six lines apart and use the same method on the same object. Copying the
combining style down from the inner predicate to the outer one is a single mechanical slip,
and the result reads naturally — `builder.or(predicates)` is a perfectly idiomatic-looking
line. A reviewer scanning for "does this build the right predicates?" will check each
`if` block, find them all correct, and never look at the join.

It also produces a symptom people misdescribe. "The department filter is broken" is the
natural report, and it sends you to the department predicate, which is fine.

### Detecting it faster next time
- **Read the generated SQL.** With `org.hibernate.SQL=DEBUG`, one request with two filters
  shows you `OR` where you expected `AND`. That is a ten-second diagnosis.
- When each part works alone and the combination does not, the bug is in the combination.
  Skip the parts.
- Test filter endpoints with the *matrix*, not with examples: no filter, each alone, each
  pair, all together. The invariant "adding a filter never increases the count" is easy to
  assert and catches an entire class of defect.

### Prevention
- Assert the monotonicity invariant in a test: for every pair of filters, the combined
  count is less than or equal to each individual count.
- Prefer composing `Specification` objects with `Specification.allOf(...)` or chained
  `.and(...)`, where the conjunction is expressed by the API rather than by an argument you
  can get wrong.

---

## E3 — Salaries exposed through the list endpoint

### Symptom
An ordinary employee calling `GET /api/employees` receives a `salary` for every person in
the company. The same employee calling `GET /api/employees/{id}` for a colleague correctly
gets no `salary` field, and `GET /api/me` correctly shows their own.

### Root cause
`EmployeeMapper.toResponse` always copies the salary — it is a dumb mapper and that is
reasonable. The visibility rule lives in `EmployeeService`, and is applied in exactly one
method:

```java
public EmployeeResponse getEmployee(Long employeeId, DirectoryUser principal) {
    Employee employee = ...;
    EmployeeResponse response = employeeMapper.toResponse(employee);
    if (!maySeeCompensation(principal, employee)) {
        response.setSalary(null);
    }
    return response;
}
```

`search` does not apply it, and does not even take the principal as a parameter:

```java
public PagedResponse<EmployeeResponse> search(EmployeeSearchCriteria criteria, Pageable pageable) {
    Page<Employee> page = employeeRepository.findAll(EmployeeSpecifications.matching(criteria), pageable);
    return PagedResponse.of(page, employeeMapper.toResponses(page.getContent()));
}
```

`directReports` sidesteps the question by blanking every salary unconditionally, which is
correct for that endpoint but means the file contains three different treatments of the
same rule in three adjacent methods.

`EmployeeResponse` is annotated `@JsonInclude(NON_NULL)`, so nulling the field really does
remove it from the JSON — the masking mechanism works. It is simply not invoked on the
highest-traffic path in the system.

### Exact location
`src/main/java/com/northgate/hr/service/EmployeeService.java` — `search` does not call
`maySeeCompensation`.

### Correct fix
The minimal change threads the principal through and applies the same rule:

```java
public PagedResponse<EmployeeResponse> search(EmployeeSearchCriteria criteria,
                                              Pageable pageable,
                                              DirectoryUser principal) {
    Page<Employee> page = employeeRepository.findAll(EmployeeSpecifications.matching(criteria), pageable);
    List<EmployeeResponse> content = new ArrayList<>();
    for (Employee employee : page.getContent()) {
        EmployeeResponse response = employeeMapper.toResponse(employee);
        if (!maySeeCompensation(principal, employee)) {
            response.setSalary(null);
        }
        content.add(response);
    }
    return PagedResponse.of(page, content);
}
```

`DepartmentService.employeesOf` calls `search`, so it must pass the principal too.

The better fix removes the possibility of forgetting. Make the mapper itself take the
viewer, so there is no way to produce an `EmployeeResponse` without deciding:

```java
public EmployeeResponse toResponse(Employee employee, DirectoryUser viewer) { ... }
```

Delete the no-argument overload. Now every call site is forced to answer the question, and
a new endpoint cannot leak by omission. Alternatives worth considering: a Jackson
`@JsonView` per audience, or a separate `EmployeeDirectoryEntry` DTO that simply has no
salary field — the strongest option, since a field that does not exist cannot leak.

### Affected components
`EmployeeService`, `EmployeeController`, `DepartmentService`, `EmployeeMapper`,
`EmployeeResponse`, `ProfileController`.

### Underlying concept
**A security rule enforced at call sites will eventually be forgotten at a call site.** The
number of places that map an entity to a response only grows, and each new one is an
opportunity to omit the check. Rules of this kind belong at a chokepoint — the mapper, a
serialization view, or a DTO that structurally cannot carry the field.

The second concept is **field-level authorization**. Access control is usually discussed as
"can this user call this endpoint", but sensitive systems also need "can this user see this
field of this row". Endpoint-level rules cannot express it, which is why it tends to be
implemented ad hoc and inconsistently.

### Why this is realistic
The detail endpoint was almost certainly written first, and it is correct — the developer
clearly understood the requirement, wrote `maySeeCompensation`, and used it. That is what
makes the file read as trustworthy. The list endpoint returns a *different-shaped* result
(a page of DTOs, assembled in a single stream call) and the masking step does not fit the
one-liner, so it was not added.

The asymmetry also explains why it survived review and QA: anyone spot-checking "can I see
someone else's salary?" would naturally open a profile page, see nothing, and move on. It
took someone reading a network tab for an unrelated reason to find it, which is exactly how
these are found in real life.

### Detecting it faster next time
- **Enumerate every route to sensitive data, not every screen.** Grep for the field name —
  `salary` — and check each place it is read or written. Four call sites, four decisions.
- When one endpoint enforces a rule and a sibling does not, assume the sibling is wrong
  until proven otherwise, and check *every* sibling rather than the one in the ticket.
- Automate it: one test per sensitive field asserting that a non-privileged caller never
  receives it from any endpoint that returns the entity.

### Prevention
- Put the rule where it cannot be skipped: the mapper signature, a `@JsonView`, or a
  separate DTO without the field.
- Response-level assertions in integration tests, driven by a list of sensitive field
  names.
- Review checklist: "which endpoints return this entity, and does each one apply the same
  field rules?"

---

## E4 — Every `@PreAuthorize` is inert

### Symptom
An account holding only `ROLE_EMPLOYEE` can `POST /api/employees` (`201`),
`PUT /api/employees/{id}` (`200`) and `DELETE /api/employees/{id}` (`204`). The service
methods carry `@PreAuthorize("hasRole('HR_ADMIN')")` and are being called anyway.

### Root cause
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, prePostEnabled = false)
public class SecurityConfig { ... }
```

`@EnableMethodSecurity` enables pre/post annotations by default. Here they are explicitly
**disabled**, and `securedEnabled = true` switches on `@Secured` instead — which nothing in
the codebase uses. So the only method-security machinery that is registered is the one no
method is annotated with, and the annotations that do exist are never evaluated.

Nothing fails and nothing is logged. `@PreAuthorize` with no advisor registered is a
comment.

The filter chain does not compensate, and that is deliberate:

```java
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
.anyRequest().authenticated()
```

The class Javadoc states the intent — URL rules separate anonymous from authenticated, and
everything finer is decided in the service layer. That design is defensible, but it makes
method security load-bearing, and it is switched off.

### Exact location
`src/main/java/com/northgate/hr/config/SecurityConfig.java`, the arguments to
`@EnableMethodSecurity`.

### Correct fix
```java
@EnableMethodSecurity
```

That is the Spring Security 6 default: `prePostEnabled = true`. Keep `securedEnabled =
true` only if you actually use `@Secured`; here, drop it.

Verify both directions after the change — an ordinary employee must get `403` and an HR
administrator must still succeed. It is worth adding a URL-level rule as defence in depth
so that a future configuration slip cannot open every write endpoint again:

```java
.requestMatchers(HttpMethod.POST,   "/api/employees/**").hasRole("HR_ADMIN")
.requestMatchers(HttpMethod.PUT,    "/api/employees/**").hasRole("HR_ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/employees/**").hasRole("HR_ADMIN")
```

### Affected components
`SecurityConfig`, every `@PreAuthorize` in `EmployeeService`, and every write endpoint in
`EmployeeController`.

### Underlying concept
**Annotations do nothing on their own — something has to process them.** `@PreAuthorize`,
`@Transactional`, `@Cacheable` and `@Validated` are all inert without their enabling
configuration and their proxy. When an annotation appears to have no effect, the first
question is never "is the expression right?" but "is anything evaluating this at all?"

The second concept is **fail-open versus fail-closed**. Disabling an authorization
mechanism grants access rather than denying it. A misconfigured authentication mechanism
usually locks everyone out and is discovered in minutes; a misconfigured *authorization*
mechanism silently opens the doors and is discovered by an auditor, or by an attacker.
Design so that the failure mode is denial: defence in depth at the URL layer means a
method-security outage degrades to "locked down" rather than "wide open".

### Why this is realistic
Spring Security 6 renamed `@EnableGlobalMethodSecurity(prePostEnabled = true,
securedEnabled = true)` to `@EnableMethodSecurity`, and changed the defaults —
`prePostEnabled` is now `true` by default and `securedEnabled` is `false`. During a
migration it is very easy to carry the old argument list across and adjust the wrong one,
and the result compiles, starts, passes every test that does not specifically assert a
`403`, and looks *more* deliberate than the bare annotation because it has explicit
arguments.

That is the cruel part: `@EnableMethodSecurity(securedEnabled = true, prePostEnabled =
false)` reads like someone who thought about it.

### Detecting it faster next time
- **When an authorization rule does not fire, verify the mechanism before the rule.** Turn
  on `org.springframework.security=DEBUG` and look for an authorization decision at the
  service layer. Nothing in the log means nothing ran.
- Check whether the bean is even proxied: log the class of the injected service. A service
  with active method security appears as a CGLIB proxy, not the plain class.
- Keep one negative test per protected operation. A single `403` assertion would have
  failed the moment this configuration changed.

### Prevention
- One test per protected method asserting `403` for an under-privileged caller. These are
  the cheapest high-value tests in a codebase and almost nobody writes them.
- Defence in depth: URL rules *and* method rules, so neither alone is load-bearing.
- Treat security configuration changes as requiring a test diff, not just a code diff.

---

## E5 — Editing any field clears the reporting line

### Symptom
HR changes an employee's phone number through the contact-details form. Afterwards the
employee has no manager, disappears from their manager's team view, and appears in
`SELECT ... WHERE manager_id IS NULL`. Their profile otherwise looks normal.

### Root cause
`EmployeeService.update` rebuilds every field from the request, including the manager:

```java
employee.setManager(resolveManager(request.getManagerId()));
```

and `resolveManager` maps a missing value to no manager:

```java
private Employee resolveManager(Long managerId) {
    if (managerId == null) {
        return null;
    }
    return employeeRepository.findById(managerId)...;
}
```

`UpdateEmployeeRequest.managerId` is a nullable `Long` with no constraint, so a payload
that simply omits the field deserialises to `null`, and `null` is then interpreted as
"this employee has no manager" rather than "the client said nothing about the manager".

`resolveManager` is shared with `create`, where mapping `null` to no manager is exactly
right — a new managing director has no manager. The same helper is correct in one caller
and wrong in the other.

The HR console sends whichever fields its current form knows about, and `managerId` is only
on the "change manager" screen. So every edit from any other screen silently detaches the
employee from the org chart.

### Exact location
`src/main/java/com/northgate/hr/service/EmployeeService.java` (`update`, via
`resolveManager`) and `src/main/java/com/northgate/hr/dto/UpdateEmployeeRequest.java`
(`managerId` cannot express the difference between absent and null).

### Correct fix
The request object must be able to distinguish "absent" from "explicitly null". Two clean
options:

**Option A — `JsonNullable` / `Optional` wrapper.** Model the field as three-valued:

```java
private JsonNullable<Long> managerId = JsonNullable.undefined();
```

```java
if (request.getManagerId().isPresent()) {
    employee.setManager(resolveManager(request.getManagerId().get()));
}
```

Absent leaves the manager alone; present-and-null clears it; present-and-set changes it.
This requires `jackson-databind-nullable` or a small custom type.

**Option B — separate the operation.** Stop letting the general update touch the reporting
line at all, and expose an explicit endpoint:

```
PUT /api/employees/{id}/manager      { "managerId": 18 }
DELETE /api/employees/{id}/manager
```

The general `PUT` then never writes `manager_id`. This is usually the better answer: moving
someone in the org chart is a distinct business action with distinct authorization, distinct
auditing and distinct downstream effects, and it does not belong hidden inside a form save.

Whichever you choose, also consider a guard: refuse to leave an employee without a manager
unless they are the top of the tree, so that this class of mistake cannot corrupt the chart
silently.

### Affected components
`EmployeeService`, `UpdateEmployeeRequest`, `EmployeeController`, the `employees` table,
the org-chart view, and anything downstream that routes by manager (approvals, reviews).

### Underlying concept
**In JSON, absent and null are different, and a plain field type cannot tell you which one
you got.** Jackson deserialises both into `null`, so the information is destroyed before
your code runs. Any partial-update API has to decide how it handles this, and the decision
has to be represented in the type.

Relatedly: **`PUT` means full replacement.** If the contract is "send everything", then
omitting `managerId` legitimately means "no manager" and the client is at fault. If the
contract is "send what changed", the operation is a `PATCH` and the DTO needs three-valued
fields. What is not defensible is a `PUT` documented as partial, implemented as full —
which is exactly the state here, and it is why the API contract in `API.md` is worth reading
carefully as part of the diagnosis.

### Why this is realistic
`resolveManager` was written for `create`, where it is correct, and reused in `update`,
where the same null has a different meaning. Reuse of a helper across two contexts with
different null semantics is one of the most common sources of quiet data loss in CRUD
services.

The symptom is also badly separated from the cause in time and in space. The edit succeeds
and returns `200` with a body that looks right. The damage is a foreign key going null,
noticed days later by a completely different person looking at a completely different
screen, who correlates it with "HR edited something" and has no reason to suspect the phone
number.

### Detecting it faster next time
- **Reproduce with a before-and-after on the database row, not on the response.** Read
  `manager_id`, send the minimal edit, read it again. One minute.
- When a field changes that nobody set, look for code that writes it unconditionally. Grep
  for `setManager` — two call sites, and the answer is in the second.
- Whenever you see an update method that assigns every field from a request, ask what each
  one does with a missing value. This is a standing review question for CRUD code.

### Prevention
- Document and test the absent-versus-null behaviour of every optional field on every
  update endpoint.
- Prefer explicit sub-resource endpoints for relationship changes.
- A database-level or application-level invariant check — "exactly one employee has a null
  manager" — turns silent corruption into a loud failure.

---

## E6 — N+1 queries on the directory listing

### Symptom
One request for a page of twenty employees issues twenty-plus `SELECT` statements: one for
the page, one count query, then one per distinct department and one per distinct manager
referenced on that page.

### Root cause
`Employee.department` and `Employee.manager` are both `@ManyToOne(fetch = FetchType.LAZY)`,
which is the right default. The page query fetches only the `employees` rows. Then
`EmployeeMapper.toResponse` dereferences both associations for every row:

```java
if (employee.getDepartment() != null) {
    response.setDepartmentName(employee.getDepartment().getName());
}
if (employee.getManager() != null) {
    response.setManagerName(employee.getManager().getFirstName() + " " + employee.getManager().getLastName());
}
```

Each first touch of an uninitialised proxy triggers its own `SELECT`. The mapping runs
inside `EmployeeService.search`, which is `@Transactional(readOnly = true)`, so the session
is open and the lazy loads succeed — they are just expensive.

The persistence context deduplicates within one request, so the statement count is bounded
by the number of *distinct* departments and managers on the page rather than by the page
size. With five departments and eighteen managers in the seed, a page of twenty lands
around twenty-plus statements. That also explains a confusing measurement: request a page
large enough to contain the managers themselves and the count drops, because the managers
are already in the persistence context from the main query.

### Exact location
`src/main/java/com/northgate/hr/service/EmployeeService.java` (`search`, which fetches
without the associations) and `src/main/java/com/northgate/hr/mapper/EmployeeMapper.java`
(which dereferences them).

### Correct fix
The subtlety is that the obvious fix interacts badly with paging, so choose deliberately.

**Entity graph — recommended here.** Both associations are `ManyToOne`, so the fetch join
does not multiply rows and paging stays correct:

```java
@EntityGraph(attributePaths = {"department", "manager"})
Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);
```

Declared on `EmployeeRepository` as an override. One query fetches the page with both
associations joined; the count query is unaffected.

**Projection — the leanest option.** The directory needs seven scalar fields; it does not
need entities at all:

```java
public interface EmployeeDirectoryView {
    Long getId();
    String getEmployeeCode();
    String getFirstName();
    String getLastName();
    String getJobTitle();
    String getDepartmentName();   // maps to department.name
}
```

No proxies, no lazy loading, less memory. Loses the ability to reuse `EmployeeMapper`.

**What not to do:** a `join fetch` over a **collection** association with `Pageable`.
Hibernate cannot paginate a collection fetch in SQL, so it fetches everything and paginates
in memory, logging `HHH90003004: firstResult/maxResults specified with collection fetch`.
It appears to work, quietly loads the whole table, and returns the wrong number of rows per
page once duplicates are involved. `department` and `manager` are `ManyToOne`, so this
project is not exposed — but it is the trap waiting one refactor away, and it is why "just
add a fetch join" is not a safe reflex.

After any fix, re-verify the page contents and `totalElements`, not just the query count.

### Affected components
`EmployeeRepository`, `EmployeeService`, `EmployeeMapper`, `DepartmentService` (which calls
the same search), and the fetch strategy on `Employee`.

### Underlying concept
**Lazy loading moves the cost, it does not remove it.** `LAZY` is the correct default
because it stops you loading a graph you do not need — but it makes the cost of *needing*
it invisible at the point of use. `employee.getDepartment().getName()` looks like a field
access and is a database round trip.

The structural insight is that **the decision about what to fetch belongs with the query,
not with the code that reads the result.** The mapper cannot know whether it is being called
once or five hundred times. Only the repository call knows the shape of the work, so that
is where the fetch plan belongs.

And: **N+1 is a correctness problem waiting to happen, not only a performance problem.**
The same pattern outside a transaction throws `LazyInitializationException`; inside a
`readOnly` transaction it merely costs. This project has `open-in-view: false`, so moving
that mapping one layer outwards would turn a slow endpoint into a broken one.

### Why this is realistic
Every part of this code is individually best practice. `LAZY` on `ManyToOne` is the
recommendation. A mapper that reads whatever it needs is clean. A read-only transaction
around the service method is correct. The defect exists only in the *combination*, and it
is invisible in any single file.

It is also invisible at small scale. Sixty employees on an idle laptop is fast, so nobody
notices until the dataset or the traffic grows — which is exactly what the database team
warned about.

### Detecting it faster next time
- **Count statements per request.** Make it a habit for any list endpoint. The one-liner in
  the README takes seconds and gives you a number you can compare before and after.
- `spring.jpa.properties.hibernate.generate_statistics=true` prints the query count per
  session in the log, which is even more direct.
- Learn the fingerprint: one query returning N rows, followed by a run of near-identical
  single-row queries against another table. Once you have seen it, you recognise it
  instantly.
- Consider a development-time guard that fails a test when a request exceeds a query budget.

### Prevention
- Decide the fetch plan at the query, with entity graphs or explicit joins.
- Use projections for read-only list endpoints; they cannot N+1 because there is nothing to
  lazily load.
- Query-count assertions in integration tests for the hot endpoints.

---

## How these interact

**E4 turns E3 from a leak into a takeover.** With salaries exposed to any authenticated
user (E3) and write operations unprotected (E4), an ordinary employee can read the entire
compensation table and then edit records — including their own salary, and including
terminating someone. Fix E4 first: it is one word and it closes the larger hole.

**E5 is amplified by E4.** While method security is off, *anyone* can trigger the manager
wipe, not just HR. After fixing E4 the blast radius shrinks to HR, which is where the
ticket described it.

**E2 amplifies E3 and E6.** Because filters widen instead of narrowing, a filtered
directory request returns far more rows than intended — more leaked salaries and more lazy
loads per request. Fixing E2 makes both of the others look less severe without touching
either, which is a good illustration of why you should fix causes rather than tune symptoms.

**E1 and E6 touch the same code path in opposite directions.** Fixing E1 properly means
allowing `sort=managerName` to become `manager.lastName`, which adds a join. Fixing E6 with
an entity graph adds the same joins. Doing both is consistent and cheap; doing E1 alone
adds a join for sorting while still lazily loading the same association for display, which
is worth noticing.

**Suggested fix order:** E4 (one word, closes the biggest hole), E3 (stops the leak), E2
(restores correct results), E5 (stops ongoing data corruption), E1 (correct status and an
allow-list), E6 (performance, and the one that needs the most deliberate design choice).

---

## What the test suite tells you

`mvn test` passes with all six defects live.

- `EmployeeMapperTest` verifies that the mapper copies fields correctly — it does, including
  the salary, which is precisely the field that should sometimes not be there. The test is
  green *because* of E3, not despite it.
- `EmployeeRepositoryTest` verifies direct-report lookup and headcount counting. Both are
  correct, and neither touches the specification, the sort, the security configuration, the
  update path or the fetch plan.

Every defect here lives in a seam: between a request parameter and an entity property (E1),
between individual predicates and their combination (E2), between two service methods that
should share a rule (E3), between an annotation and the configuration that enables it (E4),
between a JSON absence and a Java `null` (E5), between a query and the code that reads its
result (E6).

Unit tests with mocks cannot see seams, because the mock replaces exactly the component
whose interaction is broken. What would have caught these is a handful of integration tests
that make real HTTP calls as real users and assert on the response and the database:
`403` for an under-privileged caller, no `salary` field for a non-HR caller, a result count
that never grows when a filter is added, and a `manager_id` that survives an unrelated edit.
