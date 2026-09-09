# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

This repo is **design/planning only — there is no code yet.** It contains `README.md`
and `docs/` (`DESIGN.md`, `DATA_MODEL.md`, `ROADMAP.md`, `adr/`). Those documents are
the specification; treat them as the source of truth and keep them updated when
decisions change. The first milestone (**M0 — walking skeleton**, see `docs/ROADMAP.md`)
creates the `backend/`, `web/`, `deploy/`, and `docker/` trees described below.

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
The API is served at the relative path `/api/v1`. See `docs/DESIGN.md` §3.3 / §8.

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
| Run web dev server | `npm run dev` in `web/` (port 5173, proxies `/api` → `localhost:8080`) |
| Full build (runs `:web`, unit + Testcontainers + ArchUnit) | `./gradlew build` |
| Package the single jar | `./gradlew :backend:bootJar` |
| Backend tests | `./gradlew test` |
| One backend test | `./gradlew test --tests 'com.kairon.todo.RolloverServiceTest'` |
| Web tests | `npm run test` in `web/` |
| One web test | `npx vitest run path/to/file.test.ts` in `web/` |
| E2E | Playwright against the built jar + a Testcontainers Postgres |
| Regenerate API client | from the backend OpenAPI spec (CI fails on drift) |
| Deploy to local kind | `helm upgrade --install kairon deploy/helm/kairon -f values-local.yaml` |

Docker builds one image (`docker/Dockerfile`, stages: `node` build → `gradle` build →
`eclipse-temurin:25-jre`). Confirm exact task names against `Taskfile.yml` / `build.gradle.kts`
once they exist.

## Testing approach

Domain/service logic: plain JUnit 5 + AssertJ, no Spring context. Persistence:
`@DataJpaTest` + **Testcontainers PostgreSQL** (real Postgres, never H2). Web layer:
`@WebMvcTest` + MockMvc. Critical flows: `@SpringBootTest` + Testcontainers. Architecture
rules: ArchUnit. Frontend: Vitest + RTL with MSW mocking the API from the OpenAPI spec;
Playwright e2e against the packaged jar.
