# Kairon — Roadmap

Status: **Draft**. Milestones are vertical slices — each one ends with something
running and deployable, not a half-built layer. Checkboxes are the acceptance
criteria for that milestone.

---

## M0 — Walking skeleton & infrastructure

Prove the whole pipeline with almost no product in it.

- [ ] Monorepo layout created (`backend/`, `web/`, `deploy/`, `docker/`, `docs/`).
- [ ] Spring Boot app boots: Actuator health, `local` profile, Flyway runs
      `V001` (`app_user`), connects to the in-cluster Postgres (Helm chart on
      kind, port-forwarded for host dev — no docker-compose).
- [ ] One trivial endpoint: `GET /api/v1/ping` → `{ "pong": true, "version": … }`.
- [ ] React + Vite + TS shell: one page that calls `/api/v1/ping` and shows the
      result. Tailwind + shadcn/ui wired.
- [ ] Gradle `:web` subproject (node-gradle plugin) builds the SPA into
      `:backend` resources; `./gradlew :backend:bootJar` produces one jar that
      serves both the API and the SPA, with SPA fallback for client routes.
- [ ] Single `docker/Dockerfile` (node → gradle → jre) builds that jar into one
      image.
- [ ] Helm chart `deploy/helm/kairon/` installs on a local **kind** cluster:
      one backend Deployment (API + SPA) + Bitnami Postgres + ingress;
      `kairon.localtest.me` serves the shell and the ping call succeeds through
      the ingress.
- [ ] GitHub Actions: `verify` job green (web build/test, gradle build, helm
      lint, kubeconform).
- [ ] `Taskfile.yml` with `up`, `down`, `db-forward`, `be`, `fe`, `test`,
      `helm-local`.

## M1 — Authentication

- [ ] `app_user` + `refresh_token` schema (`V001`).
- [ ] `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`,
      `POST /auth/logout`, `POST /auth/logout-all`. Argon2id hashing.
- [ ] Access JWT (15 min) + rotating opaque refresh token (hashed, family-based
      reuse detection). Refresh token set as httpOnly Secure SameSite=Strict
      cookie for web.
- [ ] Spring Security: all `/api/v1/**` except `/auth/**` require a valid access
      token; `@CurrentUser` resolver.
- [ ] Bucket4j rate limiting on auth endpoints.
- [ ] `GET /me`, `PATCH /me` (display name, timezone, preferences).
- [ ] Web: register / login / logout flows, auth store (Zustand), TanStack Query
      client with a 401 → refresh → retry interceptor, protected routes.
- [ ] Tests: integration test register → login → call `/me` → refresh → logout.

## M2 — Daily Todo

- [ ] `todo_item` schema (`V002`).
- [ ] CRUD + `:complete`, `:reorder`, `:rollover`. Sparse `position` values.
- [ ] `GET /todo?day=` and `GET /todo?from=&to=`.
- [ ] Web: day view with date nav, quick-add (Enter), inline edit, complete
      shortcut, drag/keyboard reorder, a "roll unfinished from yesterday" action.
- [ ] Tests: `@DataJpaTest` + Testcontainers for the repo; MockMvc for the
      controller; a Playwright happy path.

## M3 — Daily Journal

- [ ] `journal_entry` schema with `content_tsv` + GIN index (`V003`).
- [ ] CRUD, `GET /journal?day=`, `GET /journal?from=&to=`,
      `GET /journal:search?q=` (websearch full-text, ranked).
- [ ] Web: calendar navigation with "has entry" markers, markdown editor +
      preview, multiple entries per day, search screen.
- [ ] Tests: full-text search test with Testcontainers.

## M4 — Projects (core)

- [ ] `project` + `project_task` schema (`V004`).
- [ ] Project CRUD; task CRUD with ≤2-level hierarchy, estimates, status,
      progress, `:reorder`.
- [ ] Web: project list, project detail with a task **list/tree** view and a
      simple **board** by status. No timeline yet.

## M5 — Gantt & dependencies

- [ ] `task_dependency` schema (`V005`); cycle rejection in the service.
- [ ] `GET /projects/{id}/gantt` returns tasks + edges + project window + today.
- [ ] Web: Gantt (`gantt-task-react` or custom SVG) — bars, milestone diamonds,
      dependency arrows, today line, progress fill; drag bar ends → `PATCH`.
- [ ] Soft warning when an `FS` successor starts before its predecessor ends.

## M6 — Today

- [ ] `GET /planning/today?date=` → `{ todos, dueProjectTasks, journalPrompt }`.
- [ ] `POST /planning/today:promote` → creates a `todo_item` linked to the task.
- [ ] Web: the "Today" landing screen assembling todos, due project tasks (with a
      one-click "add to today"), and a journal quick-entry.

## M7 — Hardening & prod

- [ ] `values-prod.yaml`: external DB (`postgresql.enabled=false`), real host,
      cert-manager TLS, resource limits, HPA.
- [ ] Flyway as a Helm pre-upgrade hook Job; app starts with Flyway disabled in
      k8s.
- [ ] Secrets via SOPS / sealed-secrets / external-secrets — none in git.
- [ ] `pg_dump` backup CronJob + documented restore in `deploy/RUNBOOK.md`.
- [ ] Observability: Prometheus scrape, JSON logs, correlation id, `/info` with
      git sha.
- [ ] `package` + `deploy` CI jobs; single image to GHCR; Trivy scan; OpenAPI
      spec published and web client drift check.
- [ ] Assisted scheduling (forward pass, optional critical path) — *optional
      here or deferred.*

## M8 — Assistant foundations & todo suggestions

The `assistant` module and the first AI feature. Off by default; dark unless an
Anthropic API key is configured. See [`DESIGN.md`](DESIGN.md) §13 and
[`adr/0002-ai-assistant-anthropic.md`](adr/0002-ai-assistant-anthropic.md).

- [ ] `assistant` module (`com.kairon.assistant`) with a thin
      `assistant.llm.AnthropicClient` over `com.anthropic:anthropic-java`;
      ArchUnit rule: only this module imports the SDK.
- [ ] `assistant_run` + `assistant_suggested_task` schema (`V007`).
- [ ] Per-feature opt-in in `app_user.preferences`; `kairon.assistant.*` config;
      `ANTHROPIC_API_KEY` wired through `configmap.yaml` / `secret.yaml` and the
      values files.
- [ ] Run lifecycle: create + `GET /assistant/runs/{id}` + `DELETE`.
- [ ] `POST /assistant/todo-suggestions` (`{day, horizon}`) — builds context
      from active/on-hold projects, recent todo history, recent journal entries
      and "Today"; **structured output** → `assistant_suggested_task` rows.
- [ ] `POST /assistant/suggested-tasks/{id}:accept` (creates a linked
      `todo_item`) and `:dismiss`.
- [ ] Prompt caching on the stable prefix; per-user monthly token budget;
      Bucket4j rate limit on `/assistant/**`; Micrometer token counter;
      timeout + circuit breaker; failures as `application/problem+json`.
- [ ] Web: "Suggest todos" on Today/Todo, a review-and-accept list, and a
      settings panel with the per-feature toggles and a data-sharing notice.
- [ ] Tests: MockMvc for the endpoints with the Anthropic client faked; an
      ArchUnit test for the SDK-containment rule.

## M9 — Weekly & monthly execution summaries

- [ ] SQL aggregation in `assistant` (via other modules' query APIs): todos
      created/completed/rolled over, completion rate, estimate-vs-actual hours
      per project, task-status deltas.
- [ ] `WEEKLY_SUMMARY` / `MONTHLY_SUMMARY` runs — the model narrates the
      computed numbers and adds efficiency suggestions; markdown output.
- [ ] `POST /assistant/summaries` (`{period: WEEK|MONTH, date}`) on demand, plus
      `@Async` execution and a `@Scheduled` job that auto-generates for opted-in
      users (weekly Monday AM, monthly on the 1st).
- [ ] `GET /assistant/runs?kind=&from=&to=` for history.
- [ ] Web: a summaries screen with history.

## M10 — Weekly journal reflection

Last, by design: this is the feature that sends full journal text off the
instance, so it ships after the mechanical features have proven the plumbing.

- [ ] `JOURNAL_REFLECTION` run over a week's `journal_entry` content, with light
      todo/project grounding; configurable tone (`assistant.tone`).
- [ ] `POST /assistant/journal-reflection` (`{weekOf}`).
- [ ] Its own opt-in, separate from the planning/summary opt-ins, with a
      prominent confirmation that journal text is sent to Anthropic.
- [ ] Web: a weekly reflection view.

## M11+ — Mobile & beyond

- [ ] Publish a stable OpenAPI spec; generate Kotlin and Swift clients.
- [ ] Soft-delete + `updated_at` audit confirmed on synced entities;
      `GET /api/v1/sync?since=` delta endpoint.
- [ ] Android app (Jetpack Compose): auth, Today, Todo, Journal.
- [ ] iOS app (SwiftUI): same scope.
- [ ] Offline cache with client-generated UUIDv7 ids; last-write-wins + `version`
      conflict surfacing.
- [ ] (Interim) PWA wrapper of the web app.

### Backlog (unscheduled)

Bring-your-own Anthropic key per user · Batch API for scheduled summaries (50 %
cost) · local / self-hosted model option · embeddings / semantic search over
journal history if Postgres FTS is outgrown · tags · attachments · recurring
todos · reminders/push notifications · journal revision history · project
templates · CSV/Markdown export · calendar (ICS) feed · dark/light theming
polish.
