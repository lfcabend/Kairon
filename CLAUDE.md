# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

**M0 (walking skeleton), M1 (authentication), M2 (daily todo), M3 (daily
journal), M4 (projects core), and M5 (Gantt & dependencies) are implemented.**
The `docs/` (`DESIGN.md`, `DATA_MODEL.md`, `ROADMAP.md`, `milestones/`, `adr/`)
remain the specification — treat them as the source of truth and keep them
updated when decisions change.

- `backend/` — Spring Boot app. Modules under `com.kairon`: `common` (shared kernel:
  `security` — the `SecurityFilterChain`, HS256 `JwtEncoder`/`JwtDecoder`, Argon2id
  `PasswordEncoder`, `@CurrentUser`/`UserId`; `error` — the RFC 7807
  `@RestControllerAdvice` + `ApiException`; `ratelimit` — the Bucket4j auth filter;
  `id` — `Uuidv7`; `logging` — `CorrelationIdFilter`, per-request id in the MDC +
  `X-Request-Id`), `identity` (`api` port, `domain` entities `AppUser`/`RefreshToken`,
  `repo`, `app` services, `web` controllers, `dev` — the `local`-profile
  `DevDataSeeder`), `todo` (`api` port `TodoApi`/`TodoItemView`, `domain`
  `TodoItem`/`TodoStatus`, `repo`, `app` `TodoService`/`RolloverService`/
  `TodoProperties`, `config`, `web` `TodoController`), `journal` (`api` port
  `JournalApi`/`JournalEntryView`/`JournalSearchHitView`/`JournalSearchPage`,
  `domain` `JournalEntry`, `repo` `JournalEntryRepository` (incl. the native
  full-text `search` query + `JournalSearchRow` projection), `app`
  `JournalService`/`JournalSearchService`/`JournalMapper`/`JournalProperties`,
  `config`, `web` `JournalController`), `projects` (`api` port `ProjectsApi`/
  `ProjectView`/`ProjectTaskView`/`ProjectPage`/`ProjectTaskPage`, `domain`
  `ProjectCategory`/`Project`/`ProjectStatus`/`ProjectSize`/`ProjectTask`/
  `ProjectTaskStatus`, `repo` `ProjectCategoryRepository`/`ProjectRepository`/
  `ProjectTaskRepository` (incl. the ad-hoc-join `findDueOrOverdue` query)/
  `TaskDependencyRepository` (M5), `app`
  `ProjectCategoryService`/`ProjectService`/`ProjectTaskService` (implements
  `ProjectsApi`)/`ProjectsProperties`/`SortParsing`/`TaskResolution` (M5, the
  shared "resolve a task with no projectId in the URL" helper extracted out of
  `ProjectTaskService`)/`TaskDependencyService`/`TaskDependencyView`/
  `TaskDependencyMapper` (M5 — dependency CRUD incl. cycle rejection and the
  computed `violatesConstraint` flag), `config`, `web`
  `ProjectCategoryController`/`ProjectController`/`ProjectTaskController`/
  `TaskDependencyController` (M5)), and
  `meta` (the M0 `PingController`; `DeployInfoContributor`, an `InfoContributor`
  adding the running image ref + deploy time to `/actuator/info` for the About
  page — the commit and build time are already there via `BuildProperties`).
  Migrations: `V001__identity.sql` (`app_user` + `refresh_token`),
  `V002__todo.sql` (`todo_item`), `V003__journal.sql` (`journal_entry` +
  generated `content_tsv` + GIN index), `V004__projects.sql`
  (`project_category` + `project` + `project_task`, plus the FK M2 left as a
  bare `uuid` on `todo_item.source_project_task_id`), `V005__task_dependencies.sql`
  (`task_dependency` — predecessor/successor edges, cycle-rejected in the
  service, no soft delete; a task's soft delete or a project's cascade also
  hard-deletes the edges touching it).
- `web/` — React SPA. Auth lives under `src/features/auth/`; the day view under
  `src/features/todo/` (`DayView` + `DateNav`/`DaySummary`/`QuickAdd`/`TodoList`/
  `TodoRow`, rollover in `RolloverPrompt`/`RolloverPickerDialog`/`useRollover`,
  hooks + `todoKeys`); the journal under `src/features/journal/`
  (`JournalDayView` + `JournalDateNav` (calendar popover with has-entry dots)/
  `EntryList`/`EntryCard`/`EntryEditor` (Tiptap WYSIWYG)/`MoodPicker`,
  `JournalSearchPage`, hooks in `useJournal.ts`/`useJournalSearch.ts` +
  `journalKeys`); projects under `src/features/projects/` (`ProjectListPage`
  (category sections + `ProjectPriorityList` for "Sort by: Priority")/
  `ProjectCard`/`ProjectFormDialog`/`CategoryManagerDialog`/`ProjectDetailPage`
  (Tabs: `TaskTree` ⇄ `TaskBoard` ⇄ `GanttView`, M5)/`TaskRow`/`TaskQuickAdd`/
  `TaskFormDialog` (edit mode embeds `TaskDependencySection`, M5's "Depends on"
  picker)/`TaskCard`/`GanttView` (M5 — `gantt-task-react` bars/milestones/
  dependency arrows/progress fill, an "Unscheduled" panel for undated tasks, a
  soft FS-violation warning list; dragging a bar issues the same whole-form
  `PATCH` as the form), hooks in `useProjectCategories.ts`/`useProjects.ts`/
  `useProjectTasks.ts`/`useTaskDependencies.ts` (M5) + `projectKeys`); the About page under
  `src/features/about/` (`AboutPage`, rendering `/actuator/info` — build
  version/commit/build-time + the running image ref/deploy time — via
  `about.ts`, which calls that endpoint directly rather than through
  `client.ts` since it's public and outside `/api/v1`).
  `src/components/AppLayout.tsx` is the
  top-nav shell wrapping the protected routes. API access is hand-written
  types in `src/lib/api/types.ts` plus `todo.ts`/`journal.ts`/`projects.ts`/
  `auth.ts`/`about.ts` over `client.ts` (the single-flight 401→refresh→retry
  fetch wrapper). Tests: Vitest + MSW (`src/test/msw/`); Playwright happy
  paths in `web/e2e/` (`npm run test:e2e`, needs a running full stack).
- `deploy/`, `docker/` — Helm chart and image from M0; M1 adds a `KAIRON_JWT_SECRET`
  app Secret wired into the Deployment. The About page adds `KAIRON_IMAGE_REF` /
  `KAIRON_DEPLOYED_AT` env vars (rendered at `helm upgrade` time — so an upgrade
  always rolls the Deployment, even with no other change) and a `GIT_COMMIT`
  Docker build arg (wired from `Taskfile.yml`'s `image`/`image-push` tasks).

Next milestone is **M6 — Today** (see `docs/ROADMAP.md`).

## What Kairon is

A single-user-scale personal productivity system (multi-user accounts, but "personal"
data — no assignees/teams/permissions): **Daily Todo**, **Daily Journal**, small
**Projects** with a Gantt chart, and a **Today** aggregation screen. A later phase
(M8–M10) adds an opt-in, off-by-default Anthropic-powered **assistant**.

## Architecture (the parts that span multiple files)

**One deployable.** The React SPA is **not** hosted separately — the Vite `dist/` is
built into the Spring Boot jar and served as static resources, with a `WebMvcConfigurer`
SPA fallback that returns `index.html` for any non-`/api/`, non-`/actuator/` GET that
isn't a real file. One image, one origin: no nginx, no CORS, no runtime API-URL config.
The API is served at the relative path `/api/v1`, and the whole app (SPA, API,
actuator) is mounted under the fixed `/kairon` context path (`server.servlet.context-path`,
matched by the Vite `base` and the Helm chart's `ingress.path`) so a host can serve
several personal apps side by side. See `docs/DESIGN.md` §3.3 / §8.

**Gradle multi-project.** Root build with `:backend` and `:web` subprojects. `:web`
uses the `com.github.node-gradle.node` plugin to run `npm ci && npm run build`, and its
output is wired into `:backend`'s `processResources`. `:backend` cannot be packaged
without an up-to-date web build, and the backend build therefore needs a Node toolchain.

**Modular monolith.** One Spring Boot app, package-by-feature under `com.kairon`:
`common` (shared kernel — no feature logic), `identity`, `todo`, `journal`, `projects`,
`planning` (the "Today" aggregation), and later `assistant`. Rules enforced by **ArchUnit
tests in CI** — a change that breaks them fails the build:

- A module may depend only on another module's thin `api` sub-package (public services +
  DTOs), never on its `domain` or `repo` packages.
- Controllers never take or return JPA entities — DTOs only (MapStruct maps them).
- `assistant` is the **only** module allowed to import the Anthropic SDK
  (`com.anthropic:anthropic-java`).

**Request flow:** `Controller (DTO + Bean Validation)` → `Application service
(@Transactional, authorization: row belongs to @CurrentUser)` → `domain` →
`Spring Data repository` → PostgreSQL. Accessing another user's row returns **404, not
403** (don't leak existence). Errors are RFC 7807 `application/problem+json` from a
single `@RestControllerAdvice`.

**Persistence conventions** (full detail in `docs/DATA_MODEL.md`): UUIDv7 PKs;
`created_at` / `updated_at` / `version` (JPA optimistic locking) on every business row;
`user_id` on every user-owned row; `deleted_at` soft delete on the mobile-synced
entities (todo, journal, project, project_task) with queries filtering it out;
`assistant_*` tables are hard-deleted and not synced. Enums are `varchar` + `check`
constraint, mapped `@Enumerated(EnumType.STRING)`. Migrations are **Flyway** versioned
SQL in `backend/src/main/resources/db/migration/` (`VNNN__module_description.sql`); in
Kubernetes Flyway runs as a Helm pre-upgrade hook Job and the app starts with
`spring.flyway.enabled=false`.

**Auth:** short-lived access JWT (~15 min) + opaque refresh token stored **hashed**,
**rotated on every use**, with family-based reuse detection. Web keeps the access token
in memory and the refresh token in an httpOnly/Secure/SameSite=Strict cookie scoped to
`/api/v1/auth`; CSRF is disabled globally because every other route is pure bearer-header.
Argon2id hashing. See `docs/DESIGN.md` §6.

**Assistant (M8+):** every interaction is a persisted `assistant_run`
(`PENDING → RUNNING → SUCCEEDED | FAILED`) storing `input_snapshot` (audit of exactly
what was sent), `output_markdown`, and token counts. Summaries compute their numbers in
**SQL first**; the model only narrates. Suggest-only — accepting a suggestion is always
an explicit user action that creates a normal `todo_item`. Opt-in is per feature and
off by default; the whole module is dark without an `ANTHROPIC_API_KEY`. Default model
`claude-sonnet-5`. See `docs/DESIGN.md` §13 and `docs/adr/0002-*.md`.

## Stack

Java 25 · Spring Boot 4 / Spring Framework 7 · Spring Data JPA / Hibernate 7 ·
PostgreSQL 16 · Flyway · Spring Security 7 + JWT · MapStruct · Bean Validation ·
springdoc-openapi · Bucket4j (rate limiting) · Testcontainers · ArchUnit ·
React 18 + TypeScript + Vite · React Router · TanStack Query · Zustand ·
React Hook Form + Zod · Tailwind + shadcn/ui · `gantt-task-react` ·
generated API client (`openapi-typescript` + orval) · Vitest + RTL + MSW + Playwright ·
Docker (one multi-stage image) · Kubernetes + Helm 3 · GitHub Actions.

Rationale and rejected alternatives are in `docs/adr/0001-architecture-and-stack.md`.
Keep new dependencies minimal — the project deliberately favours the simplest viable
option and owning lightweight components over large opinionated ones.

## Commands (once M0 scaffolds them)

A `Taskfile.yml` (go-task) is planned to wrap the common commands; the underlying tools:

| Task | Command |
| --- | --- |
| Local Postgres | in-cluster via the Helm chart on kind (`task up`); `kubectl port-forward svc/kairon-postgresql 5432:5432` (`task db-forward`) for host dev. **No docker-compose.** |
| Run backend (host) | `./gradlew bootRun` with the `local` Spring profile |
| Run web dev server | `npm run dev` in `web/` (port 5173, proxies `/kairon` → `localhost:8080`) |
| Full build (runs `:web`, unit + Testcontainers + ArchUnit) | `./gradlew build` |
| Package the single jar | `./gradlew :backend:bootJar` |
| Backend tests | `./gradlew test` |
| One backend test | `./gradlew test --tests 'com.kairon.todo.RolloverServiceTest'` |
| Web tests | `npm run test` in `web/` |
| One web test | `npx vitest run path/to/file.test.ts` in `web/` |
| E2E | Playwright against the built jar + a Testcontainers Postgres |
| Regenerate API client | from the backend OpenAPI spec (CI fails on drift) |
| Deploy to local kind | `helm upgrade --install kairon deploy/helm/kairon -f values-local.yaml` |
| Deploy to xbmc k3s | `task xbmc` — build + push `ghcr.io/lfcabend/kairon`, then `helm upgrade` with `values-xbmc.yaml` (kube-context `xbmc`, namespace `kairon`). Needs `docker login ghcr.io` and the out-of-band `kairon-db` Secret. |

Docker builds one image (`docker/Dockerfile`, stages: `node` build → `gradle` build →
`eclipse-temurin:25-jre`). Confirm exact task names against `Taskfile.yml` / `build.gradle.kts`
once they exist.

## Logging

**Every new controller and application service must log — treat it as part of
"done", not a follow-up.** SLF4J + Logback (Spring Boot's default starter; no
extra framework). Conventions:

- **Controllers**: one `log.debug(...)` on entry per handler with the route, the
  `@CurrentUser` id, and the key path/query params — never request bodies.
- **Application services**: `log.info(...)` for every state change (create, patch,
  delete, complete, reorder, rollover, register, login, token rotation, logout)
  including the affected row id(s) and `userId`; `log.warn(...)` for rejected or
  suspicious operations (bad credentials, refresh-token reuse, version conflict,
  validation failures that reach the service).
- **Repositories** are Spring Data interfaces with no method bodies — persistence
  visibility comes from the service-layer logs above plus `org.hibernate.SQL` at
  DEBUG (already on in the `local` profile).
- **Never log** passwords, raw or hashed tokens, JWTs, or full request/response
  bodies. Emails may appear in identity logs (single-user-scale, the user's own
  data); nothing else PII-ish.

`CorrelationIdFilter` (`common.logging`) puts a per-request id in the MDC under
`correlationId` and echoes it as the `X-Request-Id` response header; the web
`client.ts` fetch wrapper sends that header so browser and server logs join up.
The console shows the id via `logging.pattern.correlation`; the `prod` profile
(`application-prod.yml`) switches the console to ECS JSON using Spring Boot's
built-in structured logging (no `logstash-logback-encoder`). Frontend code logs
through the `log` seam in `web/src/lib/log.ts` (level-gated `console` wrapper),
never bare `console.*`.

## Testing approach

Domain/service logic: plain JUnit 5 + AssertJ, no Spring context. Persistence:
`@DataJpaTest` + **Testcontainers PostgreSQL** (real Postgres, never H2). Web layer:
`@WebMvcTest` + MockMvc. Critical flows: `@SpringBootTest` + Testcontainers. Architecture
rules: ArchUnit. Frontend: Vitest + RTL with MSW mocking the API from the OpenAPI spec;
Playwright e2e against the packaged jar.
