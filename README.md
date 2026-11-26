# Scheduling System

A full-stack system for creating and managing **scheduled executions of predefined tasks**.

- Pick a task from a fixed, read-only list (**Log message**, **Send email (simulated)**, **Database report**),
  fill in its parameters (a per-task schema, validated in the UI **and** in the backend), and choose when it runs:
  **one time**, **every X minutes/hours**, **weekly** (days + time) or a **Quartz cron** expression.
- Manage everything from one table: create, edit, delete, pause/resume, **Run now**, and a **history page** for each
  scheduling (status, attempts, duration, which backend instance ran it, errors, retries).
- Execution engine built on **Quartz** (clustered JDBC job store) with a runtime lock, heartbeats + stuck detection,
  automatic retries, duplicate prevention and edit locking. Runs as **two backend instances** in Docker to show the
  cluster in action.

---

## Contents

1. [Architecture](#architecture)
2. [Tech stack](#tech-stack)
3. [How to run](#how-to-run)
4. [Tests](#tests)
5. [API](#api)
6. [Design decisions](#design-decisions)
7. [Assumptions](#assumptions)
8. [How to add a task](#how-to-add-a-task)
9. [Scaling and future work](#scaling-and-future-work)
10. [Use of AI tools](#use-of-ai-tools)

---

## Architecture

```
                        ┌──────────────────────────┐
 Browser ── :3000 ────► │ frontend (nginx)         │
                        │  /       React SPA       │
                        │  /api/*  round robin ────┼─────────┬───────────────────────┐
                        └──────────────────────────┘         │                       │
                                                             ▼                       ▼
 Swagger UI ── :8080 ──────────────────────────────► ┌──────────────────┐   ┌──────────────────┐
                                                     │ backend-1        │   │ backend-2        │
                                                     │ Spring Boot 3.5  │   │ Spring Boot 3.5  │
                                                     │ REST API + Quartz│   │ REST API + Quartz│
                                                     └────────┬─────────┘   └────────┬─────────┘
                                                              │  one Quartz cluster  │
                                                              └──────────┬───────────┘
                                                                         ▼
                                                        ┌────────────────────────────────┐
                                                        │ PostgreSQL 15 (:5433)          │
                                                        │ schedulings + timings,         │
                                                        │ task_executions, edit locks,   │
                                                        │ qrtz_* (Quartz job store)      │
                                                        └────────────────────────────────┘
```

- The backends are **stateless**; all state (schedulings, executions, edit locks, Quartz triggers) lives in
  PostgreSQL, so any instance can serve any request.
- Quartz runs as a **cluster** on the shared JDBC job store: each trigger fires on exactly one instance, and if an
  instance dies the other one keeps running its schedulings.
- Every execution records which instance ran it (`executed_by`, the "Instance" column of the history page), and every
  log line contains the instance id.

**How one run executes** (`ExecutionRunner`):

```
Quartz trigger fires (on exactly one instance)
  1. load the scheduling; a scheduled or manual run cancels a pending retry (reason SUPERSEDED)
  2. INSERT task_executions (status RUNNING)   unique (scheduling_id, run_key) WHERE status = 'RUNNING'
       already running? ──► recorded as SKIPPED
  3. task.execute(parameters, heartbeat)       no DB transaction is held while the task runs
       heartbeat.beat() ──► last_heartbeat_at = now()   (database clock, at most 1 write / 10 s)
  4. SUCCESS, or FAILED + a one-shot retry trigger in ~10 s / ~20 s (3 attempts in total)

Stuck-execution monitor (every minute): RUNNING without a heartbeat for > 5 min ──► STUCK (lock released)
History retention (daily, 03:00 UTC): executions older than 30 days are deleted
```

---

## Tech stack

| Layer    | Technology |
|----------|------------|
| Backend  | Java 21, Spring Boot 3.5 (Web, Validation, Data JPA / Hibernate 6, Security, Actuator), Quartz 2.5 (JDBC store, clustered), Flyway, springdoc-openapi (Swagger UI) |
| Database | PostgreSQL 15 |
| Frontend | React 18 + TypeScript 5.9, Vite 6, MUI 7 + MUI X date pickers, TanStack Query 5, React Hook Form 7, React Router 7, dayjs, cronstrue, axios |
| Tests    | JUnit 5, Mockito, AssertJ, Spring Boot Test, Testcontainers, Awaitility, JaCoCo · Vitest 3, React Testing Library, MSW 2 · Playwright |
| Runtime  | Docker, docker compose, nginx · GitHub Actions CI |

```
.
├── backend/                 Spring Boot service (Maven wrapper)
│   └── src/main/java/com/memcyco/scheduler/
│       ├── controller/      REST endpoints
│       ├── dto/             API request/response records (timing is a polymorphic DTO)
│       ├── entity/          JPA entities (Scheduling, timing hierarchy, TaskExecution)
│       ├── repository/      Spring Data repositories + JDBC writers (executions, edit locks)
│       ├── service/         use cases, Quartz gateway, execution runner, retries, maintenance
│       ├── scheduling/      one TimingStrategy per timing type: Quartz trigger + canonical form
│       ├── task/            Task base class, registry, parameter schema; task/impl = the 3 tasks
│       ├── job/             Quartz jobs (task execution, stuck monitor, history retention)
│       ├── validation/      request, parameter and timing validators
│       ├── mapper/ exception/ security/ config/
│   └── src/main/resources/db/migration/   Flyway V1–V4 (V4 = Quartz tables)
├── frontend/                React app (src/api, types, hooks, pages, components, utils) + e2e/ (Playwright)
├── docker/                  backend/Dockerfile, frontend/Dockerfile + nginx.conf, docker-compose.yml, .env.example
└── .github/workflows/ci.yml
```

---

## How to run

### Option 1 — Docker (everything, two backend instances)

Requirements: Docker with Compose (Docker Desktop, or e.g. colima + docker-compose).

```bash
docker compose -f docker/docker-compose.yml up --build
```

(Without the compose plugin, e.g. colima with Homebrew's `docker-compose`, run `docker-compose` instead of
`docker compose`. The Dockerfiles use no BuildKit-only features, so they also build without `buildx`.)

| What | URL |
|------|-----|
| UI | http://localhost:3000 |
| Swagger UI (backend-1) | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| PostgreSQL | `localhost:5433`, database / user / password `scheduler` |

- Startup order is handled by health checks: PostgreSQL → both backends (ready = migrations applied, Quartz started)
  → nginx. The first build downloads Maven and npm dependencies and takes a few minutes.
- Ports, credentials and tuning can be changed with `cp docker/.env.example docker/.env` (every value is optional).
- Stop: `docker compose -f docker/docker-compose.yml down` (add `-v` to also delete the database volume).

**Things to try**

- Create a *Log message* scheduling that runs every minute, open its history, and watch the **Instance** column: runs
  are spread over `backend-1` and `backend-2`, and each run happens exactly once.
- Stop one instance (`docker compose -f docker/docker-compose.yml stop backend-2`): the other one keeps running all
  schedulings, and the UI keeps working through nginx.
- Create a *Send email* scheduling to an address at **`@fail.example`** (a simulated SMTP outage) and click
  **Run now**: attempt 1/3 fails, attempt 2/3 follows ~10 s later and attempt 3/3 ~20 s after that, each linked to
  the attempt it retries.
- Pause a scheduling, create an identical one (allowed, with a warning), then try to resume the first one: blocked,
  with a link to the active duplicate.
- Start editing a scheduling in one browser tab, then try the same in a second tab: the second tab is told who is
  editing and cannot edit, delete, pause or resume it until the first tab is done (Run now still works).

### Option 2 — Local development

Requirements: Java 21, Node.js 22, PostgreSQL 15 (Docker is only needed for the integration tests' Testcontainers
mode and for the compose stack).

**1. Database** (once):

```bash
psql -U postgres -c "CREATE USER scheduler WITH PASSWORD 'scheduler';"
psql -U postgres -c "CREATE DATABASE scheduler OWNER scheduler;"
```

(Use your PostgreSQL superuser instead of `postgres` if it differs, e.g. your OS user with Homebrew/Postgres.app.)
Flyway creates all tables (including the Quartz tables) when the backend starts.

**2. Backend** → http://localhost:8080 (Swagger: http://localhost:8080/swagger-ui.html)

```bash
cd backend
./mvnw spring-boot:run
```

**3. Frontend** → http://localhost:5173 (the Vite dev server proxies `/api` to `http://localhost:8080`)

```bash
cd frontend
npm install
npm run dev
```

### Configuration

All settings live in `backend/src/main/resources/application.yml`; the `docker` profile
(`application-docker.yml`) only changes defaults for the compose network.

| Environment variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/scheduler` (docker profile: `…//db:5432/scheduler`) | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `scheduler` / `scheduler` | Database credentials |
| `DB_POOL_SIZE` | `20` | JDBC connection pool (keep above `QUARTZ_THREADS`) |
| `QUARTZ_THREADS` | `10` | Tasks that can run at the same time on one instance |
| `INSTANCE_ID` | `local` | Quartz cluster node id, `executed_by`, log prefix; **unique per instance** |
| `SERVER_PORT` | `8080` | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` (docker profile: `http://localhost:3000`) | Allowed browser origins for `/api/**` |

Engine timings (`app.execution.*`): stuck timeout `5m`, stuck check interval `1m`, heartbeat throttle `10s`,
history retention `30d`, retention job `0 0 3 * * ?` (UTC). They can be overridden like any Spring property, e.g.
`APP_EXECUTION_RETENTION=7d`. The retry policy is deliberately fixed (see below).

---

## Tests

| Suite | Command | What it covers |
|---|---|---|
| Backend unit | `cd backend && ./mvnw test` | Validators, cron canonical form and fingerprints, trigger building per timing type, retry policy, heartbeat throttling, execution runner flow, task registry and each task. No database or Docker needed. |
| Backend unit + integration + coverage gate | `cd backend && ./mvnw verify` | Everything above plus integration tests (`*IT`) on a real PostgreSQL 15 with real Quartz: API CRUD, paging, sorting and error format, duplicates (including parallel creates), edit lock, Run now, retry chain, runtime lock (SKIPPED), stuck monitor, retention, startup reconciliation, the DB report task, and the **task contract test** that runs every `Task` bean. Fails if **line coverage** is below **80%** (JaCoCo, unit + integration merged). |
| Frontend unit/component | `cd frontend && npm test` | Vitest + React Testing Library + MSW: utilities, schema-driven parameter fields, timing editor, form dialog, schedulings page, history page. |
| Frontend coverage gate | `cd frontend && npm run test:coverage` | Same, fails below **70%** coverage. Report: `frontend/coverage/`. |
| Type check | `cd frontend && npm run typecheck` | TypeScript, no emit. |
| End-to-end | `cd frontend && npm run e2e` | Playwright against the running Docker stack (`E2E_BASE_URL`, default `http://localhost:3000`): create a *Log message* scheduling → Run now → success in its history. First time: `npx playwright install chromium`. Report: `frontend/playwright-report/`. |

- **Integration test database.** By default the integration tests start `postgres:15-alpine` with **Testcontainers**
  (needs a running Docker). Without Docker they can use an existing PostgreSQL instead; the database is wiped by the
  tests, so its name must contain `test` or `_it`:

  ```bash
  createdb -O scheduler scheduler_it        # once (as a PostgreSQL superuser)
  cd backend
  IT_DB_URL=jdbc:postgresql://localhost:5432/scheduler_it ./mvnw verify   # optional: IT_DB_USER, IT_DB_PASSWORD (default scheduler)
  ```

  With **colima**, Testcontainers needs to be told where Docker is:
  `export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.

- **Coverage report** (unit + integration merged): `backend/target/site/jacoco/index.html`.
- **CI** (`.github/workflows/ci.yml`, every push and pull request): `backend` (`./mvnw verify` with Testcontainers),
  `frontend` (type check, tests with coverage, production build), and `e2e` (builds and starts the compose stack,
  waits until every service is healthy, runs Playwright; uploads the report and prints container logs on failure).

**Latest results:** backend **183** tests (unit **110**, integration **73**), all passing in ~40 s; coverage
**95.5% lines** / 82.1% branches · frontend **133** tests, coverage **95.7% lines** / 89.9% branches · e2e **2/2**
passing against the full Docker stack (nginx → 2 backends → PostgreSQL).

---

## API

Interactive documentation: **Swagger UI** at `/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/tasks` | Predefined tasks with their parameter schemas |
| `GET` | `/api/schedulings` | List. Query: `page`, `size` (≤ 100), `sort` (`name`, `taskKey`, `enabled`, `createdAt`, `updatedAt`; e.g. `sort=name,asc`), `search` (name contains), `taskKey`, `status` (`ACTIVE`, `PAUSED`, `COMPLETED`) |
| `GET` | `/api/schedulings/{id}` | One scheduling, including duplicate warnings |
| `POST` | `/api/schedulings` | Create → `201` + `Location` |
| `PUT` | `/api/schedulings/{id}` | Full replacement. Requires header `X-Edit-Lock-Token` and the loaded `version` in the body; releases the lock |
| `DELETE` | `/api/schedulings/{id}` | Delete (with its history) → `204` |
| `POST` | `/api/schedulings/{id}/pause` | Pause (pending retries are cancelled) |
| `POST` | `/api/schedulings/{id}/resume` | Resume; `409` if an identical scheduling is active |
| `POST` | `/api/schedulings/{id}/run` | Run now → `202`; allowed while paused or being edited |
| `POST` `PUT` `DELETE` | `/api/schedulings/{id}/edit-lock` | Acquire (header `X-Editor-Id`) / renew / release (header `X-Edit-Lock-Token`) |
| `GET` | `/api/schedulings/{id}/executions` | History, newest first. Query: `status`, `page`, `size` (≤ 100) |
| `GET` | `/actuator/health` | Health, with `/liveness` and `/readiness` groups |

`X-Editor-Id` is a random id per browser tab (optional on reads; it marks locks held by "this tab" as `mine`).

**Create request**

```json
{
  "name": "Monday and Wednesday report",
  "taskKey": "DB_QUERY",
  "parameters": { "report": "EXECUTIONS_BY_STATUS", "lastHours": 24 },
  "timing": { "type": "WEEKLY", "days": ["MON", "WED"], "time": "09:00", "timezone": "Asia/Jerusalem" },
  "enabled": true
}
```

The `timing` object is polymorphic on `type`:

```json
{ "type": "ONE_TIME", "runAt": "2026-12-01T14:00:00Z",                        "timezone": "Asia/Jerusalem" }
{ "type": "INTERVAL", "every": 15, "unit": "MINUTES", "startAt": null,        "timezone": "Asia/Jerusalem" }
{ "type": "WEEKLY",   "days": ["MON", "WED"], "time": "09:00",                "timezone": "Asia/Jerusalem" }
{ "type": "CRON",     "expression": "0 0 9 ? * MON",                          "timezone": "Asia/Jerusalem" }
```

A scheduling in responses also carries `task {key, name, known}`, the normalized `parameters`, `status`
(`ACTIVE` / `PAUSED` / `COMPLETED`), `nextFireTime`, `lastExecution` (status, attempt / maxAttempts, pending retry
time or cancel reason, error), `editLock` (who, since, `mine`), `warnings`, `version` and audit fields.

**Errors** are [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) problem details with a machine-readable `code`.
Validation errors list **every** invalid field at once, keyed by the form field path:

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "The request has 3 invalid field(s)",
  "instance": "/api/schedulings",
  "code": "VALIDATION_FAILED",
  "errors": {
    "name": "is required",
    "parameters.to": "must be a valid email address",
    "timing.every": "must be between 1 and 10000"
  }
}
```

```json
{
  "title": "Duplicate scheduling",
  "status": 409,
  "detail": "\"Morning log\" is already scheduled with the same task, parameters and timing",
  "code": "DUPLICATE_SCHEDULING",
  "conflictingScheduling": { "id": "8f0e3c1a-…", "name": "Morning log" }
}
```

| `code` | HTTP | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Invalid body, parameters, timing, query parameter or missing header |
| `NOT_FOUND` | 404 | Unknown scheduling or endpoint |
| `DUPLICATE_SCHEDULING` | 409 | An identical active scheduling exists (create, edit, resume) |
| `EDIT_LOCKED` | 409 | Someone else is editing (`lockedBy`, `since`) |
| `EDIT_LOCK_LOST` | 409 | Saving or renewing without holding the edit lock (expired or taken over) |
| `CONCURRENT_MODIFICATION` | 409 | The scheduling changed since it was loaded (`version` mismatch) |
| `INTERNAL_ERROR` | 500 | Unexpected error (logged; no internals in the response) |

---

## Design decisions

### Data model

```
schedulings ──1:1──► schedule_timings (id, type, timezone)        ◄── JPA JOINED inheritance
   │                    ├── one_time_timings   (run_at)
   │                    ├── interval_timings   (interval_value, interval_unit, start_at)
   │                    ├── weekly_timings     (time_of_day) ──1:N──► weekly_timing_days (day_of_week)
   │                    └── cron_timings       (cron_expression)
   ├──1:N──► task_executions        one row per attempt; retry chain via previous_attempt_id
   └──1:1──► scheduling_edit_locks  edit lease (only while someone edits)
```

- **Timing is a class hierarchy, stored with JPA `JOINED` inheritance**: a base row (type + timezone) plus exactly
  one table per timing type with **only non-nullable, type-specific columns** and CHECK constraints. This was
  preferred over one wide table with nullable columns (invalid combinations would be representable) and over a JSON
  column (no constraints, no typed queries).
- **Composition**: a scheduling *has a* timing. Changing the timing (even its type) replaces only the timing rows; the
  scheduling keeps its id, its history and its Quartz job.
- **Parameters** are a JSONB column, because their shape depends on the task; they are always validated and
  normalized against the task's schema before they are stored.
- **Every schema change is a Flyway migration**, including the Quartz tables (V4), and Hibernate only validates the
  mapping (`ddl-auto: validate`).
- `version` (optimistic locking), `created_at / updated_at / created_by / updated_by` (JPA auditing).
- The status shown in the UI (`ACTIVE`, `PAUSED`, `COMPLETED` = one-time scheduling whose time has passed) is
  **derived**, not stored, so it can never be out of date.

### Tasks: predefined, polymorphic, schema-driven

- `abstract class Task` (Template Method): each task declares `key()`, `name()`, `description()`, `parameters()` and
  implements `doExecute(TaskParameters, Heartbeat)`. The final `execute()` re-validates the parameters, runs the task,
  and warns if the task never sent a heartbeat.
- Tasks are Spring beans collected by `TaskRegistry`, which **validates every definition at startup** (key format,
  duplicate keys, ENUM values, min/max, default values) and stops the application if one is broken.
- **The parameter schema is the single source of truth**: the UI renders and validates the fields from
  `GET /api/tasks`, and the backend validates and normalizes the same rules (`STRING` ≤ 500 chars, `TEXT` ≤ 5000,
  `NUMBER` with min/max, `BOOLEAN`, `ENUM`, `EMAIL`; required fields; unknown parameters rejected; defaults
  applied). All errors come back at once, keyed `parameters.<name>`.
- The three tasks:

  | Key | Parameters | Behaviour |
  |---|---|---|
  | `LOG` | `message` (required), `level` (`INFO` / `WARN` / `ERROR`, default `INFO`) | Writes the message to the application log |
  | `DUMMY_EMAIL` | `to` (email, required), `subject` (required), `body` (text) | Simulates sending an email (logs it). Recipients `@fail.example` simulate an SMTP failure, to demonstrate retries |
  | `DB_QUERY` | `report` (required: `SCHEDULINGS_BY_STATUS`, `SCHEDULINGS_BY_TASK`, `EXECUTIONS_BY_STATUS`), `lastHours` (1–720, default 24) | Runs a **predefined read-only report** and logs the result. Users choose a report, never SQL, so SQL injection is impossible; queries have a 30 s timeout |

### Duplicate prevention

The UI and the backend do not allow two **active** schedulings that would do the same thing at the same times.

- Each scheduling stores a **fingerprint** = SHA-256 of *task + canonical parameters + canonical timing*, and a
  **partial unique index** `(fingerprint) WHERE enabled` enforces the rule even for two requests racing each other.
- *Canonical parameters*: normalized values with defaults applied and keys sorted, so `{"message": "hi"}` equals
  `{"level": "INFO", "message": "hi"}`.
- *Canonical timing is semantic*, not textual (each `TimingStrategy` computes it):
  - weekly is converted to its cron form; cron fields are expanded to sorted value sets (`MON` → 2, ranges and steps
    expanded, `?` → `*`, full ranges → `*`), so **`0 0 9 ? * MON,WED` (cron) = Monday + Wednesday 09:00 (weekly)**
    and `0 */15 * * * ?` = `0 0,15,30,45 * * * ?`;
  - an interval whose start has passed is converted to a cron when one fires at exactly the same moments
    (e.g. *every 15 minutes from 10:07* = `0 7/15 * * * ?`); otherwise interval + start are compared
    (*every 15 min from 10:00* ≠ *from 10:05*);
  - one-time = the UTC instant; the timezone is always part of the identity.
- **Partial overlap is allowed** (e.g. "every 15 minutes" and "every hour"): they are different schedulings and both
  run when they coincide.
- **Paused duplicates**: creating a scheduling identical to a *paused* one is allowed, with a
  `PAUSED_DUPLICATE_EXISTS` warning. **Resuming** a paused scheduling while an identical one is active is **blocked**
  (`409 DUPLICATE_SCHEDULING` with a link to the active one).

### Scheduling engine (Quartz)

- **One durable Quartz job per scheduling** and a **main trigger built from its timing** by a `TimingStrategy` per
  timing type (Strategy pattern): one-time → simple trigger; interval → simple trigger repeating forever on the grid
  defined by its start; weekly → cron trigger (`0 30 9 ? * MON,WED`) in the scheduling's timezone; cron → cron
  trigger in its timezone. Retries (`retry/<failed attempt>`) and Run now (`manual/<random>`) are extra one-shot
  triggers of the same job.
- **Database and Quartz change in one transaction.** Quartz uses Spring's data source and transaction
  (`LocalDataSourceJobStore`), so creating, editing, pausing or deleting a scheduling updates its rows and its triggers
  atomically: a failed save never leaves a stray trigger behind.
- **Clustered JDBC job store** (two instances in Docker); job data is stored as strings (`useProperties`), not
  serialized Java objects. Triggers are acquired under Quartz's row lock (`acquireTriggersWithinLock`): a trigger
  written inside our transaction is signalled to Quartz before the commit, and the lock makes the scheduler thread
  wait for that commit instead of missing the trigger until its next poll (up to 30 s), so Run now and retries start
  immediately.
- **Missed runs** (instance down or all threads busy for more than 60 s): cron and weekly runs are skipped, interval
  runs continue on their grid, a missed **one-time** run still fires once when the system is back, retries and Run now
  fire as soon as possible.
- **Pause removes the main trigger; resume rebuilds it from the timing.** Runs missed while paused are skipped, and a
  one-time scheduling whose time passed while paused does not run when resumed. (Quartz's own pause/resume would
  fire such missed one-time triggers immediately.) The job is kept, so Run now works on paused schedulings.
- **Startup reconciliation**: on every start, Quartz is compared with the schedulings table and repaired: missing
  triggers of enabled schedulings are recreated, triggers of paused ones removed, jobs without a scheduling deleted.
  Idempotent, so both instances can do it at the same time.
- **Interval start**: when no start time is given, the interval starts *now* and the first run happens immediately.

### Execution: runtime lock, heartbeat, retries

- **Runtime lock**: the same scheduling never runs the same task + parameters twice at the same time. The lock is the
  partial unique index `(scheduling_id, run_key) WHERE status = 'RUNNING'` on `task_executions`
  (`run_key` = hash of task + parameters), taken with `INSERT … ON CONFLICT DO NOTHING`. If it is taken, the attempt is
  recorded as **SKIPPED** instead of waiting. Different schedulings run independently, and if a scheduling's
  parameters are edited while it runs, a run with the new parameters may start. Quartz's
  `@DisallowConcurrentExecution` was deliberately not used: it would delay overlapping runs silently.
- **Heartbeat — every task must implement it.** A running task calls `heartbeat.beat()` when it starts, between steps
  and inside long loops. The heartbeat writes `last_heartbeat_at = now()` using the **database clock** (instances may
  have different clocks), at most once every 10 s. A monitor job (every minute) marks runs without a heartbeat for
  more than **5 minutes** as **STUCK** (e.g. the instance crashed), which releases the runtime lock. If a stuck or
  deleted run beats again, `beat()` throws and the task stops; late results are never written over STUCK
  (all status updates are conditional on `status = 'RUNNING'`). Enforced by the `doExecute` signature, a runtime
  warning, the Javadoc and an auto-discovered contract test.
- **Retries — one fixed policy for all tasks** (not configurable): **3 attempts in total**, exponential backoff
  **~10 s then ~20 s**, with ±20% random jitter so many failures do not retry in the same second. Each retry is a
  **durable one-shot Quartz trigger**: no thread sleeps, a retry survives restarts and can run on either instance.
  Not retried: invalid parameters, unknown task, `NonRetryableTaskException`, SKIPPED, STUCK. A pending retry is
  **cancelled** (reason recorded on the failed attempt) when a new scheduled or manual run starts (`SUPERSEDED`), or the
  scheduling is paused (`PAUSED`) or edited (`EDITED`). The history shows `attempt n/3` and links each retry to the
  attempt it retries.
- **Short transactions**: starting and recording a run are separate short transactions; no transaction or database
  connection is held while the task runs.
- **History retention**: a daily job (03:00 UTC) deletes finished executions older than **30 days** in batches of
  5,000 rows (short transactions), and expired edit locks.
- Internal jobs (stuck monitor, retention) live in a separate `system` Quartz group, so they never appear as user
  schedulings, and the cluster runs each of them on one instance only.

### Concurrent editing

- **Edit lock (lease)**: opening the edit form acquires a lock stored in `scheduling_edit_locks` with one atomic
  statement (`INSERT … ON CONFLICT DO UPDATE … WHERE the lock expired OR is mine`). It lasts **2 minutes**, the UI
  renews it **every 30 seconds** and releases it on close or save; if a browser disappears, the lock simply expires.
- The owner is *user + browser tab* (`X-Editor-Id`), so two tabs of the same user also block each other. While
  someone else holds the lock: **Edit, Delete, Pause and Resume are blocked**, **Run now is allowed**, and the table
  shows who is editing.
- **Optimistic locking** (`@Version`) is kept as a second safety net: saving with an outdated version returns
  `409 CONCURRENT_MODIFICATION`.

### API

- REST with RFC 7807 errors (see [API](#api)); all validation errors at once, keyed by form field path, so the UI shows
  each message under its input.
- `PUT` is a full replacement (the edit form always sends the whole scheduling).
- Paging with a **sort whitelist** (plus `id` for stable paging) and a maximum page size of 100; a stable
  `PageResponse` JSON shape instead of Spring's internal `Page` JSON.
- **A table page costs a fixed number of queries**, whatever its size: schedulings + timings (entity graph), weekly
  days (batch fetch), latest execution per scheduling (one `DISTINCT ON` query), active edit locks, and next run times
  (one SQL query on `qrtz_triggers`).
- Run now returns `202 Accepted`: the run is asynchronous and its result appears in the history.

### Security (prepared, not enforced)

Authentication was out of scope, but the code is ready for it: Spring Security is configured with `permitAll()`
(stateless, CSRF off for a cookie-less JSON API, CORS whitelist), the current user comes from a `CurrentUserProvider`
(today: `anonymous`), and it already feeds the audit columns and the edit-lock owner. Adding JWT/OIDC means
changing `SecurityConfig` and that provider.

### Frontend

- React 18 + TypeScript + Vite, MUI components and MUI X date/time pickers.
- **TanStack Query** for server state; the table and the history page **poll every 10 seconds** (next run, last run,
  locks stay fresh) and refresh right after every change.
- Routes: `/` (schedulings table: search, task and status filters, server-side paging and sorting, kept in the URL)
  and `/schedulings/:id/history` (a **separate page** with a summary, Run now, and the paged execution history).
- The create/edit dialog renders the **task's parameter fields from its schema** (React Hook Form) with the same rules
  as the backend; the timing editor has four modes (one time, repeat every X minutes/hours, weekly, cron with Quartz
  examples and a human-readable description via cronstrue); the timezone defaults to the browser's.
- Backend validation errors are shown under the matching fields; `409` conflicts show a banner with a link to the
  conflicting scheduling; the edit-lock lifecycle (acquire, renew, lost → save disabled) is handled by a hook.

### Choices refined during implementation

These differ from the first draft of the design, for the reasons given:

1. **Pause unschedules and resume rebuilds** (instead of Quartz `pauseJob` / `resumeJob`) — Quartz's resume would
   immediately fire a one-time trigger that was missed during the pause, breaking the "missed runs are skipped" rule.
2. **Execution rows are written with plain SQL** (`TaskExecutionWriter`; the `TaskExecution` entity is read-only) —
   the runtime lock needs `INSERT … ON CONFLICT DO NOTHING`, and every status change must be a single conditional
   statement using the database clock.
3. **`lastExecution` in the table response** also carries `maxAttempts`, `errorMessage` and `retryCancelledReason`, so
   the table can show "attempt 2/3, retry at …" or why a retry was cancelled without loading the history.
4. **Startup reconciliation** also removes triggers of paused schedulings and deletes orphan Quartz jobs (not only
   recreating missing triggers).
5. **Engine timings** (`app.execution.*`: stuck timeout, check interval, heartbeat throttle, retention) are
   configuration properties, while the retry policy stays fixed in code, as agreed.
6. The `DELETED` retry-cancel reason exists in the schema but is effectively unused: deleting a scheduling deletes
   its history together with it.
7. **Duplicate check before applying an edit.** Found by the integration tests: checking after changing the entity
   let Hibernate auto-flush the change first, so editing a scheduling into a duplicate answered 500 instead of 409.
8. **Triggers acquired under Quartz's lock** (`acquireTriggersWithinLock`, see *Scheduling engine*), so Run now does
   not wait for the scheduler's next idle poll.

---

## Assumptions

- Single tenant, no login (authentication is prepared only, see above); all changes are audited as `anonymous`.
- Tasks are defined in code and are read-only; users cannot create or change tasks.
- Times are stored in UTC. Every timing has an IANA timezone (the UI defaults to the browser's); weekly and cron
  timings follow that zone's local time (including daylight-saving changes), intervals are fixed durations.
- Cron expressions use **Quartz syntax** only (6–7 fields, seconds first, `?` for day-of-month or day-of-week), up to
  120 characters, and must have a future run.
- "Every X minutes/hours": X between 1 and 10,000; the start time is optional (default: now, first run immediately).
- A one-time scheduling must be in the future when created. After it runs it stays in the table as `COMPLETED`;
  it can still be run manually, or edited to a new time.
- Two schedulings are duplicates only if task, normalized parameters and timing (including timezone) are
  semantically identical; overlapping schedules are allowed and both run.
- Tasks are safe to retry (the same retry policy applies to all of them) and finish within minutes; a task must send
  a heartbeat at least every 5 minutes.
- Up to 10 tasks run at the same time per backend instance (`QUARTZ_THREADS`).
- Deleting a scheduling deletes its execution history. History is kept for 30 days.
- The scheduling name does not need to be unique; duplicates are defined by what runs and when.

---

## How to add a task

1. Create a subclass of `Task` in `com.memcyco.scheduler.task.impl` and annotate it with `@Component`.
2. Declare its parameters; they appear in the UI automatically and are validated on both sides.
3. Implement `doExecute` and **call `heartbeat.beat()` regularly** (at the start, between steps, inside long loops;
   at most a few minutes apart). Give every blocking call a timeout shorter than the 5-minute stuck timeout.
4. Throw `NonRetryableTaskException` for failures a retry cannot fix; any other exception is retried.

```java
@Component
public class HttpPingTask extends Task {

    @Override public String key() { return "HTTP_PING"; }
    @Override public String name() { return "HTTP Ping"; }
    @Override public String description() { return "Calls a URL and logs the response status."; }

    @Override
    public List<ParameterDefinition> parameters() {
        return List.of(
                ParameterDefinition.builder("url", ParameterType.STRING).label("URL").required().build(),
                ParameterDefinition.builder("timeoutSeconds", ParameterType.NUMBER)
                        .label("Timeout (seconds)").defaultValue(10).min(1).max(60).build());
    }

    @Override
    protected void doExecute(TaskParameters parameters, Heartbeat heartbeat) throws Exception {
        heartbeat.beat();
        // ... call parameters.getString("url") with a timeout of parameters.getLong("timeoutSeconds") ...
        heartbeat.beat();
    }
}
```

The task registry validates the definition at startup, and the **task contract test** picks the new task up
automatically: it runs it with valid parameters from its schema and fails if it never calls `heartbeat.beat()` or
accepts invalid parameters.

---

## Scaling and future work

**Scaling as it is**

- More backend instances can be added behind nginx with no code change (stateless API, Quartz cluster, database
  clock, all locks in PostgreSQL). Per instance, `QUARTZ_THREADS` limits concurrent runs and `DB_POOL_SIZE` should
  stay above it.
- Hot queries are indexed: active fingerprints (partial unique), running executions by heartbeat (partial), history
  by scheduling and start time, retention by start time, retry chains.
- The table page loads with a fixed number of queries, and history deletion runs in small batches.

**Next steps**

- Partition `task_executions` by month (dropping a partition replaces the retention deletes) and move history reads to
  a read replica.
- For long-running or very many tasks: let Quartz only enqueue runs to a message broker and run tasks on a separate
  worker pool.
- Real authentication (JWT/OIDC) with ownership and roles; an audit log of changes.
- Push updates (WebSocket / server-sent events) instead of polling.
- Cancel a running execution (the heartbeat already provides a cooperative stop point), end dates and run limits,
  calendars and blackout windows, per-task retry policies.
- Alerts on repeated failures or stuck runs; metrics (Micrometer / Prometheus) and tracing.

---

## Use of AI tools

**Claude Code** was the only AI tool used in this project.

- **Design**: the whole design was discussed with Claude Code topic by topic before any code was written — data
  model, task framework, duplicate rules, execution engine (runtime lock, heartbeat, retries), API, UI, testing and
  deployment. Every decision described in this README was agreed in that discussion.
- **Implementation**: Claude Code implemented the agreed design step by step — backend foundation, task framework,
  scheduling API, Quartz execution engine, tests, frontend, Docker and CI, and this README.
- **Verification**: Claude Code ran the test suites and end-to-end checks against a real PostgreSQL 15 (for example
  the retry chain, stuck detection and startup reconciliation after deleting Quartz data).
