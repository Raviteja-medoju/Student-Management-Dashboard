# Student Management

A full-stack app for managing **students, courses and enrollments**, built with **Spring Boot 4 (Java 21)** and **Angular 22**.

**Author:** Ravi Teja

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6db33f)
![Angular 22](https://img.shields.io/badge/Angular-22-dd0031)
![Backend tests](https://img.shields.io/badge/backend%20tests-118%20passing-brightgreen)
![Frontend tests](https://img.shields.io/badge/frontend%20tests-38%20passing-brightgreen)
![Coverage](https://img.shields.io/badge/line%20coverage-97%25-brightgreen)

![Dashboard](docs/screenshots/01-dashboard.png)

### Features at a glance

- **Students and courses**: full CRUD with validation that runs in the browser and again on the server.
- **Dashboard**: live totals and three charts (enrollments per month, credit load, seats per course).
- **Ctrl+K search**: jump to any student, course or action from anywhere, keyboard only if you like.
- **Drag-and-drop enrollment**: move courses between "Available" and "Enrolled"; full courses are locked; saving is all-or-nothing.
- **Tabs that protect your work**: switching tabs, leaving the page or closing the browser with unsaved edits asks first.
- **Filters that live in the URL**: search, course, status ("not enrolled", "full-time 12+ credits"), sort and page are all shareable links.
- **CSV export** of the filtered student list.
- **Undo delete**: deleted items disappear at once, with a few seconds to undo before anything is removed on the server.
- **Live email check**: the form warns about a duplicate email while you type.
- **Enroll from the course page**: pick students with a live search, or remove them from the roster.
- **Polish**: dark mode, skeleton loaders, page transitions, entrance animations, phone-friendly layout, Swagger API docs.

### AI-assisted development

- AI assistance supported selected parts of the project's development.
- AI was used while developing some backend logic and configuring API endpoints.
- To make the application easy to launch without starting the frontend, backend, and database separately, a Docker environment was created so the services can be started together with a single command.
- Supporting shell scripts provide convenient commands for common tasks, including preflight checks, setup, database restore, startup, and shutdown.
- AI assistance was also used to improve this README's clarity and make the project architecture and setup easier to understand.

---

## Contents

- [Quick start](#quick-start) (including [backup and restore](#backup-and-restore) and [moving to another machine](#moving-to-another-machine))
- [What to look at](#what-to-look-at)
- [Requirements coverage](#requirements-coverage)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [REST API](#rest-api)
- [Design decisions](#design-decisions)
- [Testing](#testing)
- [Project structure](#project-structure)

---

## Quick start

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21 or newer | Maven is **not** needed: the Maven wrapper (`mvnw`) downloads it |
| Node.js | 22.22+ or 24.15+ | Angular CLI is **not** needed globally: it runs from `node_modules` |

No database install is needed for local mode. The backend stores its database in `backend/data/`. On the very first start it loads demo data; after that your data is kept between restarts. Docker mode uses PostgreSQL.

On a fresh machine, run `./preflight.sh` (read-only check) and then `./setup.sh` (dependencies, build, restore of the newest backup). See [Moving to another machine](#moving-to-another-machine).

### Run everything with one command

```bash
./start.sh        # starts locally when Node.js is supported; otherwise starts the Docker stack
./start.sh local  # explicitly use the local Java + Node.js setup
./stop.sh         # stops everything: local processes and the Docker stack
```

- `./start.sh` finds a JDK 21+ by itself (`JAVA_HOME`, `PATH` or the usual install folders), installs frontend dependencies on the first run, and writes logs to `.run/`. It uses local mode when Node.js 22.22.3+, 24.15+, or 26+ is available; if Node.js is missing or too old, it starts Docker mode instead. Running it again while the app is up does nothing.
- `./start.sh docker` runs the full stack in Docker instead (PostgreSQL, backend and nginx), so no JDK or Node is needed.
- `./start.sh --open` also opens the app in your browser. `./stop.sh --clean` also deletes the Docker database volume and the logs.
- On Windows, use Git Bash or WSL, or follow the manual steps below. If the scripts lost their executable flag while being copied, run `bash start.sh`.

### Or run each part manually (two terminals)

```bash
# 1) Backend: http://localhost:8080
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

```bash
# 2) Frontend: http://localhost:4200
cd frontend
npm install
npm start
```

Open **http://localhost:4200**. The Angular dev server proxies `/api` to the backend, so there is no CORS setup.

### Useful URLs

| What | URL |
|------|-----|
| App | http://localhost:4200 |
| REST API | http://localhost:8080/api/students |
| Swagger UI (try every endpoint) | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health check | http://localhost:8080/actuator/health |

### Option: Docker Compose with PostgreSQL

No JDK or Node needed, only Docker:

```bash
./start.sh docker       # or: docker compose up --build
```

This starts PostgreSQL, the backend (with the `postgres` Spring profile) and the frontend served by nginx on http://localhost:4200.
The same Flyway migrations run in local and PostgreSQL modes. To run the backend locally against PostgreSQL, start only the database
(`docker compose up db`) and run `SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run`.

### Backup and restore

```bash
./backup.sh                       # SQL backup into backups/ (works while the app is running)
./restore.sh                      # restore the newest backup (saves the current data first, so it can be undone)
./restore.sh backups/<file>.sql   # restore a specific backup
```

Local-mode backups are plain SQL (schema + data) and can be taken while the app is running. A restore stops the app, rebuilds the database from the file, and starts the app again if it was running. In Docker mode, `./backup.sh --postgres` uses `pg_dump`, and `./restore.sh backups/pg-*.sql` restores into PostgreSQL. To start over with demo data, stop the app and delete `backend/data/`.

### Moving to another machine

```bash
# On the old machine: fresh backup + source, without node_modules/target/live DB (about 2 MB)
./pack.sh                         # writes ../StudentManagement-<date>.tar.gz

# On the new machine
tar -xzf StudentManagement-<date>.tar.gz
cd StudentManagement
./preflight.sh                    # read-only check of Java, Node, network, ports, backups
./setup.sh --install-tools --start
```

`setup.sh` is safe to re-run. It makes the scripts executable, installs a missing JDK 21 or Node 24 into `~/.local/share` when `--install-tools` is given (official downloads, checksums verified, no sudo), runs `npm ci`, builds the backend, restores the newest backup if there's no database yet, and with `--test` runs both test suites.

---

## What to look at

If you only have five minutes:

1. **Tabs with modal confirmation**: open a student, change their name, then click another tab, a sidebar link, or refresh the page. All three ask before discarding the edit, using one shared dialog.
   See [`student-detail.component.ts`](frontend/src/app/features/students/student-detail/student-detail.component.ts).
2. **All-or-nothing enrollment**: in the Courses tab, drag courses into "Enrolled" and save. The server applies the whole set in one transaction; if any course is full or missing, nothing changes, and a test proves the rollback against a real database.
   See [`courses-tab.component.ts`](frontend/src/app/features/students/student-detail/courses-tab/courses-tab.component.ts), [`EnrollmentServiceImpl.java`](backend/src/main/java/com/example/studentmgmt/service/impl/EnrollmentServiceImpl.java) and [`EnrollmentTransactionIntegrationTest.java`](backend/src/test/java/com/example/studentmgmt/integration/EnrollmentTransactionIntegrationTest.java).
3. **Errors end to end**: type an email that already exists. A debounced async validator asks the API and shows the error while you type. If two people race for the same email, the server still answers `409` with a field error, and it lands on the same field.
   See [`email-available.validator.ts`](frontend/src/app/shared/forms/email-available.validator.ts) and [`GlobalExceptionHandler.java`](backend/src/main/java/com/example/studentmgmt/exception/GlobalExceptionHandler.java).
4. **RxJS state**: save enrollments in a student's Courses tab, then open Courses or the Dashboard. Seat counts and charts are already current, with no page reload. Press **Ctrl+K** and type: students are searched on the server with `debounceTime` and `switchMap`.
   See [`student.store.ts`](frontend/src/app/core/state/student.store.ts), [`data-events.ts`](frontend/src/app/core/state/data-events.ts) and [`command-palette.component.ts`](frontend/src/app/shared/command-palette/command-palette.component.ts).
5. **Filters and URL state**: filter the student list, copy the URL, and open it in a new tab. The same view comes back. Delete a student and press **Undo**.
   See [`student-query-params.ts`](frontend/src/app/features/students/student-list/student-query-params.ts), [`StudentSpecifications.java`](backend/src/main/java/com/example/studentmgmt/repository/StudentSpecifications.java) and [`undo.service.ts`](frontend/src/app/core/services/undo.service.ts).
6. **RestTemplate**: a typed client, [`StudentApiClient.java`](backend/src/main/java/com/example/studentmgmt/client/StudentApiClient.java), drives the running server in [`StudentApiIntegrationTest.java`](backend/src/test/java/com/example/studentmgmt/integration/StudentApiIntegrationTest.java).

---

## Requirements coverage

| Requirement | Where it is implemented |
|-------------|-------------------------|
| **CRUD operations** | Students and courses: list, create, edit, delete in the UI ([students](frontend/src/app/features/students), [courses](frontend/src/app/features/courses)) and REST ([controllers](backend/src/main/java/com/example/studentmgmt/controller)) |
| **Routing** | Lazy-loaded routes for dashboard, students, courses, student detail (`/students/:id?tab=courses`), course detail and a 404 page ([`app.routes.ts`](frontend/src/app/app.routes.ts)) |
| **Multiple tabs with modal confirmation** | Student page with Profile, Courses and Summary tabs. Switching away from a tab with unsaved edits opens a confirm dialog. The same dialog guards leaving the page (`canDeactivate`), closing a dirty course dialog (Esc or backdrop) and every delete. The browser's native prompt covers refresh and close. |
| **Error handling** | Backend: one [`GlobalExceptionHandler`](backend/src/main/java/com/example/studentmgmt/exception/GlobalExceptionHandler.java) returns a single `ApiError` JSON shape (400, 404, 405, 409, 415, 500). Frontend: [`errorInterceptor`](frontend/src/app/core/http/error.interceptor.ts) shows a snackbar, forms show field errors, a global `ErrorHandler` catches bugs, and pages render not-found and offline states. |
| **Multi-component communication** | Parent to child: signal `input()`s pass the student into each tab. Child to parent: `output()`s `saved`, `dirtyChange`, `editProfile`. Siblings and other pages: shared RxJS stores plus a small RxJS event bus. |
| **Form handling** | Typed reactive forms with validators that mirror the backend rules, a debounced async validator for duplicate emails, mat-error messages, server field errors mapped onto controls, Save disabled while invalid, pending or saving, and unsaved-change tracking based on values rather than the `dirty` flag |
| **Backend CRUD services** | Service interfaces and `@Transactional` implementations, DTOs as Java records, mappers ([`service`](backend/src/main/java/com/example/studentmgmt/service)) |
| **Angular 18+** | Angular 22: standalone components, signals, new control flow, zoneless change detection, OnPush by default |
| **Java, Spring, RestTemplate** | Spring Boot 4.1 on Java 21. `RestTemplate` bean and `StudentApiClient`, exercised by the end-to-end test |
| **Database** | Local database by default (zero setup), PostgreSQL through the `postgres` profile and Docker Compose, one schema managed by Flyway |
| **State management with RxJS** | `BehaviorSubject` stores, `switchMap` to cancel stale requests (list, Ctrl+K search, enroll picker), `debounceTime` and `distinctUntilChanged` on search, `combineLatest` for filtering, `concatMap` for one-by-one enrollment, an RxJS event bus between stores, `async` pipe in templates |
| **UI framework** | Angular Material 3 and the CDK (drag and drop), custom theme, dark mode, charts built from plain HTML and CSS, responsive down to phone width |
| **Validation, exceptions, transactions** | Bean Validation on DTOs and query parameters, custom exceptions, class-level `@Transactional(readOnly = true)` with write overrides, a pessimistic lock on course seats, DB constraints as a safety net |
| **Unit tests for backend** | 118 tests: services (Mockito), controllers (`@WebMvcTest`), repositories (`@DataJpaTest`), the CSV exporter, and integration tests over HTTP and real transactions. JaCoCo enforces at least 80% line coverage (currently 97.8%). |

---

## Screenshots

| | |
|---|---|
| **Ctrl+K search**: pages, courses and students (server search) ![](docs/screenshots/14-command-palette.png) | **Drag-and-drop enrollment**: live change summary, full courses locked ![](docs/screenshots/05-enrollment-tab.png) |
| **Students**: server-side paging, sorting and debounced search ![](docs/screenshots/02-students.png) | **Filters in the URL**: `/students?status=fulltime` ![](docs/screenshots/15-student-filters.png) |
| **Undo delete**: gone at once, removed on the server only if not undone ![](docs/screenshots/16-undo-delete.png) | **Tabs**: unsaved edits are confirmed before switching ![](docs/screenshots/04-unsaved-changes-modal.png) |
| **Live email check** while typing ![](docs/screenshots/18-live-email-check.png) | **Enroll from the course page** with a live student search ![](docs/screenshots/17-enroll-from-course.png) |
| **Courses**: seat meters, actions menu, skeleton loading ![](docs/screenshots/08-courses.png) | **Course dialog**: client validation that mirrors the backend ![](docs/screenshots/09-course-dialog-validation.png) |
| **Course detail** with roster ![](docs/screenshots/10-course-detail.png) | **Summary tab** ![](docs/screenshots/07-summary-tab.png) |
| **Dark mode** ![](docs/screenshots/11-dark-mode.png) | **Swagger UI** for the API ![](docs/screenshots/13-swagger.png) |

<p align="center"><img src="docs/screenshots/12-mobile.png" width="260" alt="Mobile layout"></p>

---

## Architecture

```mermaid
flowchart TB
    subgraph Browser["Angular 22 app · localhost:4200"]
        C["Components<br/>routes, tabs, forms, dialogs"] --> S["RxJS stores<br/>StudentStore, CourseStore, DataEvents"]
        S --> A["API services + interceptors<br/>loading bar, error → snackbar"]
    end
    A -- "HTTP/JSON /api (dev proxy)" --> CT
    subgraph Server["Spring Boot 4 · localhost:8080"]
        CT["Controllers<br/>@Valid DTOs, status codes"] --> SV["Services<br/>@Transactional business rules"]
        SV --> R["Spring Data JPA repositories"]
        GH["GlobalExceptionHandler → ApiError"] -.-> CT
    end
    R -- "Hibernate + Flyway" --> DB[("Local database (default)<br/>or PostgreSQL")]
    RT["StudentApiClient<br/>(RestTemplate)"] -. "calls the API" .-> CT
```

Each layer only talks to the one below it. Controllers stay thin, all business rules and transactions live in services, and entities never leave the service layer (`spring.jpa.open-in-view=false`).

### Data model

```mermaid
erDiagram
    STUDENT ||--o{ ENROLLMENT : has
    COURSE  ||--o{ ENROLLMENT : has
    STUDENT {
        bigint id PK
        varchar first_name
        varchar last_name
        varchar email UK
        date date_of_birth
    }
    COURSE {
        bigint id PK
        varchar code UK
        varchar title
        varchar description
        int credits "1-6"
        int capacity "1-500"
    }
    ENROLLMENT {
        bigint id PK
        bigint student_id FK
        bigint course_id FK
        date enrolled_on
    }
```

`(student_id, course_id)` is unique. Deleting a student or a course removes its enrollments (JPA cascade plus `ON DELETE CASCADE`).

### How a save flows (and fails)

```mermaid
sequenceDiagram
    participant U as User
    participant F as Profile form
    participant St as StudentStore
    participant I as errorInterceptor
    participant API as StudentController
    participant Svc as StudentService

    U->>F: Submit
    F->>F: Client validators (block bad input)
    F->>St: create(request)
    St->>API: POST /api/students
    API->>API: @Valid → 400 + fieldErrors if invalid
    API->>Svc: create()
    alt email already used
        Svc-->>API: DuplicateResourceException (transaction rolls back)
        API-->>I: 409 ApiError {fieldErrors: [email]}
        I-->>U: Snackbar with the message
        I-->>F: ApiError → error shown under the email field
    else ok
        Svc-->>API: StudentDetailResponse
        API-->>St: 201 Created + Location
        St-->>F: BehaviorSubject updates every subscribed view
    end
```

---

## REST API

All endpoints are under `/api`. Every error response has the same shape.

| Method | Path | Purpose | Success |
|--------|------|---------|---------|
| GET | `/api/students?search=&courseId=&enrolled=&minCredits=&page=&size=&sort=&direction=` | Paged, sortable list with search and optional filters | 200 |
| GET | `/api/students/export?…same filters…` | The filtered list as a CSV download (`text/csv`, dated filename) | 200 |
| GET | `/api/students/email-availability?email=&excludeId=` | Is this email free? (used while typing) | 200 |
| GET | `/api/students/{id}` | Student with enrolled courses | 200 |
| POST | `/api/students` | Create | 201 + `Location` |
| PUT | `/api/students/{id}` | Update | 200 |
| DELETE | `/api/students/{id}` | Delete student and their enrollments | 204 |
| PUT | `/api/students/{id}/courses` | Replace the course set `{ "courseIds": [..] }` (all-or-nothing) | 200 |
| POST | `/api/students/{id}/courses/{courseId}` | Enroll in one course | 201 |
| DELETE | `/api/students/{id}/courses/{courseId}` | Unenroll | 204 |
| GET | `/api/courses?search=` | All courses with live seat counts | 200 |
| GET | `/api/courses/{id}` | Course with its roster | 200 |
| POST | `/api/courses` | Create | 201 + `Location` |
| PUT | `/api/courses/{id}` | Update (capacity cannot drop below current enrollment) | 200 |
| DELETE | `/api/courses/{id}` | Delete course and its enrollments | 204 |
| GET | `/api/stats` | Dashboard numbers and chart data (per month, per course, credit bands) | 200 |

**Error format** (from `GlobalExceptionHandler`):

```json
{
  "timestamp": "2026-10-29T18:18:00.770Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/students",
  "fieldErrors": [
    { "field": "email", "message": "Email must be a valid email address", "rejectedValue": "bad" }
  ]
}
```

| Situation | Status |
|-----------|--------|
| Invalid body or query parameter, malformed JSON, non-numeric id | 400 |
| Unknown student, course or endpoint | 404 |
| Wrong HTTP method or content type | 405 / 415 |
| Duplicate email or course code, full course, capacity below enrollment, DB constraint | 409 |
| Anything unexpected (logged; no internals leak) | 500 |

---

## Design decisions

### Backend

- **Layered monolith** (controller → service → repository). It is simple, testable and the right size for the problem.
- **Explicit `Enrollment` entity** instead of `@ManyToMany`. The link carries `enrolledOn`, the database enforces one row per pair, and it avoids Hibernate's collection pitfalls.
- **DTOs as Java records**, so entities never reach JSON. This avoids lazy-loading errors and recursion and keeps a stable API contract. `PageResponse<T>` gives paging a stable shape too.
- **Transactions**: services are `@Transactional(readOnly = true)` at class level, and write methods override it. `replaceEnrollments` checks every course exists first, then applies removals and additions. If a course turns out to be full, everything rolls back, including rows Hibernate has already flushed. Course rows are locked (`PESSIMISTIC_WRITE`) during the seat check, so two requests cannot both take the last seat.
- **Two layers of validation**: Bean Validation on DTOs and query parameters, plus business checks in services (unique email or code, seat limits, capacity not below enrollment). Database constraints (unique, check, FK) are the final safety net, and `DataIntegrityViolationException` maps to 409.
- **No N+1 queries**: detail endpoints use `@EntityGraph`. List endpoints get course counts and credits for a whole page in one `GROUP BY` query.
- **Filters as Specifications**: each filter (search, course, enrolled or not, minimum credits) is a small JPA `Specification`, combined only when set. Search treats `%` and `_` literally. The list and the CSV export share the same filters.
- **Safe CSV export**: RFC 4180 quoting, a UTF-8 BOM so Excel shows accents correctly, and values that start with `=`, `+`, `-` or `@` are prefixed so a spreadsheet can't run them as formulas.
- **Flyway owns the schema** (`ddl-auto=validate`), and the same SQL runs in local and PostgreSQL modes.
- **RestTemplate**: `StudentApiClient` is a typed client that turns error responses into `StudentApiException` with the parsed `ApiError`. Other services could use it to call this API. The end-to-end test uses it against the running server.
- **Testability**: a `Clock` bean makes "today" deterministic in tests.

### Frontend

- **Standalone components, signals and the new control flow** on Angular 22, which is zoneless with OnPush by default. Server data flows through RxJS stores and the `async` pipe. Local UI state (saving flags, tab selection) uses signals.
- **Stores** (`StudentStore`, `CourseStore`) wrap a `BehaviorSubject` and expose read-only slices (`students$`, `loading$`, `selected$`). Components never keep their own copies of server data. `switchMap` cancels stale requests, so an old search can never overwrite a newer one.
- **`DataEvents` bus**: a store announces changes (for example `enrollments-changed`), and the course store and dashboard refresh. Views stay in sync without knowing about each other.
- **Tabs you can protect**: the tab bar is a Material tab nav whose active tab is fully controlled by the parent. A switch can therefore be confirmed *before* it happens instead of being undone after. The active tab is kept in the URL (`?tab=courses`), so it survives a refresh and can be shared.
- **Unsaved-change detection compares values**, not Angular's `dirty` flag. Typing a change and then undoing it is not "unsaved". Tabs report changes synchronously, so the route guard is never late.
- **Error UX**: the interceptor normalizes every failure (including "server unreachable") into an `ApiError`. Requests whose page shows its own error state opt out of the snackbar through an `HttpContext` token.
- **The URL is the list's state**: search, filters, sort and page are written to the query string and read back from it, so back/forward, refresh and shared links all show the same list. Invalid values in a hand-edited URL fall back to defaults.
- **Undo instead of fear**: a delete hides the row at once and opens an "Undo" snackbar; the server call happens only when the snackbar closes. If the call fails, the row comes back.
- **Drag and drop with a keyboard path**: CDK drag-drop between two lists, plus +/− buttons that do the same thing, so it works without a mouse.
- **Charts without a chart library**: three small HTML/CSS chart components. Their colors were checked with a palette validator for color-blind safety and contrast, in light and dark mode. Every chart has tooltips, direct labels and a screen-reader table.
- **Polish**: a global loading bar (with a 150 ms delay to avoid flicker), skeleton loaders on first load, entrance animations and page cross-fades (both off for people who prefer reduced motion), empty and error states with actions, page titles per record, dark mode that remembers your choice, and a responsive layout.

---

## Testing

```bash
cd backend && ./mvnw verify       # 118 tests + JaCoCo report and 80% line-coverage gate
cd frontend && npm run test:ci    # 38 Vitest unit tests
```

The backend coverage report is written to `backend/target/site/jacoco/index.html`.

| Suite | What it covers |
|-------|----------------|
| `service/*ServiceImplTest` (Mockito) | Create, duplicate email or code, not found, update, delete, email availability, export, enrollment add/remove, full course, missing course, capacity rule, stats math (months, credit bands) |
| `controller/*ControllerTest` (`@WebMvcTest`) | 200, 201 + `Location`, 204, 400 with field errors, 404, 405, 409, 415, 500 without leaking details, paging, sort and filter parameters, CSV headers |
| `repository/*RepositoryTest` (`@DataJpaTest`) | Unique and check constraints, search (including literal `%`/`_`), every filter and combinations, paging, entity graph loading, cascade deletes, monthly and per-student aggregates |
| `export/StudentCsvExporterTest` | CSV rows, quoting, spreadsheet-formula protection |
| `integration/StudentApiIntegrationTest` | Real server on a random port, full CRUD through the RestTemplate client and `TestRestTemplate`, error bodies, CSV export, email check, OpenAPI docs |
| `integration/EnrollmentTransactionIntegrationTest` | Proves the all-or-nothing rollback with real transactions |
| Frontend (Vitest) | Store behavior (filters, request cancellation, cache patching, undo hiding, events), URL ↔ query mapping, undo service, async email validator, error interceptor, unsaved-changes guard, form helpers, the drag-and-drop Courses tab |

A GitHub Actions workflow ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs both suites and the production build on every push.

---

## Project structure

```
.
├── backend/                         Spring Boot API (Maven wrapper included)
│   └── src/main/java/com/example/studentmgmt/
│       ├── entity/                  Student, Course, Enrollment
│       ├── repository/              Spring Data JPA, filter Specifications, aggregate projections
│       ├── dto/                     Request/response records
│       ├── mapper/                  Entity ↔ DTO
│       ├── service/ (+ impl/)       Business rules and transactions
│       ├── controller/              REST endpoints
│       ├── exception/               ApiError, custom exceptions, global handler
│       ├── export/                  CSV exporter
│       ├── client/                  StudentApiClient (RestTemplate)
│       └── config/                  CORS, RestTemplate, OpenAPI, Clock, demo data seeder
│   └── src/main/resources/db/migration/   Flyway SQL
├── frontend/                        Angular 22 app
│   └── src/app/
│       ├── core/                    models, API services, stores, interceptors, guard, undo/theme/confirm services
│       ├── shared/                  Ctrl+K palette, confirm dialog, skeleton, count-up, avatar, seat meter, form helpers
│       ├── features/
│       │   ├── dashboard/           stat tiles + charts/
│       │   ├── students/            list (filters, URL state, export) + detail (profile, courses board, summary)
│       │   └── courses/             list, form dialog, detail, enroll-students dialog
│       └── app.ts / app.routes.ts   shell and lazy routes
├── docs/screenshots/
├── backups/                         SQL backups (./backup.sh); restored by ./restore.sh and ./setup.sh
├── scripts/                         Shared helpers and the JDK/Node installer used by the root scripts
├── start.sh / stop.sh               Start or stop the whole stack (local or Docker)
├── preflight.sh / setup.sh          Check a machine, then install, build and restore on it
├── backup.sh / restore.sh           Database backup and restore (local database, or PostgreSQL in Docker mode)
├── pack.sh                          Archive the project + a fresh backup for moving machines
├── docker-compose.yml               PostgreSQL + backend + nginx frontend
└── .github/workflows/ci.yml
```

---
