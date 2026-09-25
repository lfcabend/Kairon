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

- [x] `app_user` + `refresh_token` schema (`V001`).
- [x] `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`,
      `POST /auth/logout`, `POST /auth/logout-all`. Argon2id hashing.
- [x] Access JWT (15 min) + rotating opaque refresh token (hashed, family-based
      reuse detection). Refresh token set as httpOnly Secure SameSite=Strict
      cookie for web.
- [x] Spring Security: all `/api/v1/**` except `/auth/**` (and `/ping`) require a
      valid access token; `@CurrentUser` resolver.
- [x] Bucket4j rate limiting on auth endpoints.
- [x] `GET /me`, `PATCH /me` (display name, timezone, preferences).
- [x] Web: register / login / logout flows, auth store (Zustand), TanStack Query
      client with a 401 → refresh → retry interceptor, protected routes.
- [x] Tests: integration test register → login → call `/me` → refresh → logout.

## M2 — Daily Todo

- [x] `todo_item` schema (`V002`).
- [x] CRUD + `:complete`, `:reorder`, `:rollover` (plus `rollover-preview` and
      `:rollover-undo`). Sparse `position` values.
- [x] `GET /todo?day=` and `GET /todo?from=&to=`.
- [x] Web: day view with date nav, quick-add (Enter), inline edit, complete
      shortcut, drag/keyboard reorder, and rollover in three user-selectable
      modes (manual / pick / auto) — the sweep looks back over every skipped day
      in a bounded window, not just "yesterday".
- [x] Tests: `@DataJpaTest` + Testcontainers for the repo; service unit tests;
      MockMvc for the controller; an `@SpringBootTest` flow test; ArchUnit rule
      for the module; Vitest component tests; a Playwright happy path.

## M3 — Daily Journal

- [x] `journal_entry` schema with `content_tsv` + GIN index (`V003`).
- [x] CRUD, `GET /journal?day=`, `GET /journal?from=&to=`,
      `GET /journal:search?q=` (websearch full-text, ranked).
- [x] Web: calendar navigation with "has entry" markers, minimal WYSIWYG
      editor (bold/italic/heading, no raw markdown shown), multiple entries
      per day, search screen.
- [x] Tests: full-text search test with Testcontainers.

## M4 — Projects (core)

- [x] `project_category` + `project` + `project_task` schema (`V004`).
- [x] Project category CRUD + `:reorder`; project CRUD (optional category);
      task CRUD with ≤2-level hierarchy, estimates, status, progress,
      `:reorder`.
- [x] Web: project list grouped by category with a category-management
      dialog, project detail with a task **list/tree** view and a simple
      **board** by status. No timeline yet.
- [x] About page (pulled forward from M7): `/actuator/info` reports `build`
      (version, commit, build time — `build-info.properties`, the commit
      passed in as a Gradle property since `.dockerignore` excludes `.git`
      from the image build) and `deploy` (running image ref, deploy
      timestamp — `DeployInfoContributor`, sourced from env vars the Helm
      chart sets at `helm upgrade` time).

## M5 — Gantt & dependencies

- [x] `task_dependency` schema (`V005`); cycle rejection in the service.
- [x] `GET /projects/{id}/dependencies` returns edges with a computed
      `violatesConstraint` flag (D7 — no combined `/gantt` endpoint; the web
      app composes it with the already-fetched project window and task list).
- [x] Web: Gantt (`gantt-task-react`) — bars, milestone diamonds, dependency
      arrows, today line, progress fill; drag bar ends → `PATCH`.
- [x] Soft warning when an `FS` successor starts before its predecessor ends.

## M6 — Today

- [x] `GET /planning/today?date=` → `{ todos, dueProjectTasks, journalPrompt }`.
- [x] `POST /planning/today:promote` → creates a `todo_item` linked to the task.
- [x] Web: the "Today" landing screen assembling todos, due project tasks (with a
      one-click "add to today"), and a journal quick-entry.

## M7 — Hardening & prod

See [`milestones/M7-hardening-prod.md`](milestones/M7-hardening-prod.md) for the
full plan and rationale (Status: Accepted). xbmc (home k3s, Tailscale Funnel) is
the real deployed environment this milestone hardens; `values-prod.yaml` is kept
as a separate, currently-unused overlay for a hypothetical future managed
cluster. `imagePullSecrets` support (Q3) was explicitly skipped — the GHCR
packages stay public.

- [x] `values-prod.yaml`: external DB (`postgresql.enabled=false`), cert-manager
      TLS, HPA, `RollingUpdate` — for a hypothetical future managed cluster, not
      xbmc (D1/D2).
- [x] Flyway as a Helm pre-install/pre-upgrade hook Job, for **every** k8s
      environment (kind, xbmc, and the hypothetical prod) — a new
      `docker/migrator.Dockerfile` (Flyway CLI + SQL); app starts with
      `SPRING_FLYWAY_ENABLED=false` in k8s (D4–D6).
- [x] Security fix: `SecurityConfig` explicitly denies every `/actuator/**` path
      beyond `health`/`info`, closing a latent gap where any future
      `management.endpoints.web.exposure.include` change would otherwise leak
      publicly and unauthenticated (D10).
- [x] Secrets stay manual (`existingSecret` + documented one-time
      `kubectl create secret`, formalized in `deploy/RUNBOOK.md`) — no SOPS /
      sealed-secrets / external-secrets (D9).
- [x] `pg_dump` backup CronJob to a local PVC, enabled on xbmc; documented
      restore + the accepted on-node single-point-of-failure risk in
      `deploy/RUNBOOK.md` (D7/D8).
- [x] Observability: `micrometer-registry-prometheus` dependency added but not
      yet exposed/scraped (no confirmed Prometheus instance yet — D11); JSON
      logs and correlation id already shipped (M0/M1). (`/info` with git sha +
      deploy facts landed in M4 — see the About page bullet there.)
- [x] `deploy.yml` CI: `package` (build + Trivy-scan the app image and the new
      migrator image, push both to GHCR, publish the OpenAPI spec) then
      `deploy` (auto `helm upgrade` against xbmc via a Tailscale-connected
      runner) on merge to `main`, targeting a `xbmc` GitHub Environment (D12/D15).
      The one-time setup only a repo-admin can do (Tailscale OAuth client,
      `KUBECONFIG_XBMC` secret, the `xbmc` Environment itself) is documented in
      the milestone plan §6.3, not done by this checkbox.
- [x] `springdoc-openapi` added; Swagger UI and the raw spec made live and
      public on the deployed app (same reasoning as `/actuator/info`), plus
      published as a CI artifact — the generated web client swap
      (`openapi-typescript`/orval) and its drift check are deferred to their
      own follow-up, not bundled into this milestone (D13).
- [ ] Assisted scheduling (forward pass, optional critical path) — dropped from
      this milestone; stays an unscheduled backlog item (D14).

## M8 — Assistant foundations & todo suggestions

See [`milestones/M8-assistant-foundations.md`](milestones/M8-assistant-foundations.md)
for the full plan and rationale (Status: Accepted — implemented). The `assistant` module and the
first AI feature. Off by default; dark unless an Anthropic API key is
configured. See also [`DESIGN.md`](DESIGN.md) §13 and
[`adr/0002-ai-assistant-anthropic.md`](adr/0002-ai-assistant-anthropic.md).

- [x] `assistant` module (`com.kairon.assistant`) with a thin
      `assistant.llm.AnthropicClient` over `com.anthropic:anthropic-java`;
      ArchUnit rule: only this module imports the SDK.
- [x] `assistant_run` + `assistant_suggested_task` schema (`V007`).
- [x] Per-feature opt-in in `app_user.preferences`; `kairon.assistant.*` config;
      `ANTHROPIC_API_KEY` wired through `configmap.yaml` / `secret.yaml` and the
      values files.
- [x] Run lifecycle: create + `GET /assistant/runs/{id}` + `DELETE`.
- [x] `POST /assistant/todo-suggestions` (`{day, horizon}`) — builds context
      from active/on-hold projects, recent todo history, recent journal entries
      and "Today"; **structured output** → `assistant_suggested_task` rows.
- [x] `POST /assistant/suggested-tasks/{id}:accept` (creates a linked
      `todo_item`) and `:dismiss`.
- [x] Prompt caching on the stable prefix; per-user monthly token budget;
      Bucket4j rate limit on `/assistant/**`; Micrometer token counter;
      timeout + circuit breaker; failures as `application/problem+json`.
- [x] Web: "Suggest todos" on Today/Todo, a review-and-accept list, and a
      settings panel with a data-sharing notice and today's one real toggle
      (todo suggestions) — the execution-summaries/journal-reflection toggles
      are deliberately deferred to M9/M10, when those features actually exist
      to toggle.
- [x] Tests: MockMvc for the endpoints with the Anthropic client faked; an
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

## M12 — Observability: Prometheus & Grafana

Resolves M7's Q1 (`micrometer-registry-prometheus` and the `/actuator/prometheus`
endpoint land in M7, but stay unexposed with no live scraper — see
[`milestones/M7-hardening-prod.md`](milestones/M7-hardening-prod.md)). A real
Prometheus instance and Grafana dashboards were named as a "later" in
[`DESIGN.md`](DESIGN.md) §11 without a milestone; this is that milestone.

- [ ] Deploy Prometheus into the xbmc cluster (or decide it scrapes from
      elsewhere over Tailscale) — pick a footprint that fits xbmc's single
      2-core node (a full `kube-prometheus-stack` may be too heavy; a standalone
      Prometheus + a scrape config may be the better fit).
- [ ] Expose `/actuator/prometheus` to that scraper without reopening it to the
      public Ingress: add `prometheus` to
      `management.endpoints.web.exposure.include` and a scoped allow in
      `SecurityConfig` (narrower than removing M7 D10's `/actuator/**` deny
      outright) — exact mechanism (network-policy-restricted, an internal-only
      Service, or a separate management port) decided at design time.
- [ ] Deploy Grafana; at least one dashboard (app-level: request rate/latency/
      errors, JVM memory/GC; infra-level: node CPU/memory) checked into
      `deploy/` as code, not hand-built in the UI.
- [ ] `deploy/RUNBOOK.md` gains a "read the dashboards" / "what's the alert for
      X" section once there's something to point at.

## M13 — Observability: log aggregation with Loki

Depends on M12 (needs Grafana already deployed). Logs already go to stdout as
structured ECS JSON with a `correlationId` field (M0/M1) — this milestone makes
them queryable and dashboard-able instead of `kubectl logs`-only.

- [ ] Deploy Grafana Loki into the xbmc cluster, sized for its single 2-core
      node (Loki only indexes labels, not full text, so it's far lighter than
      an Elasticsearch-based alternative).
- [ ] Deploy a log-shipping agent (Promtail, or Grafana Alloy) as a DaemonSet to
      ship pod stdout — the app's existing ECS-JSON logs — into Loki, parsing
      `correlationId` and log level out as labels.
- [ ] Wire Loki as a Grafana data source (M12); at least one dashboard mixing
      Loki log panels with Prometheus metric panels on the same timeline
      (e.g. request latency next to the error logs from that same window,
      correlated by `correlationId`), checked into `deploy/` as code.
- [ ] At least one Grafana alert rule against a LogQL query (e.g. error-log
      rate over N/min) as a concrete example of log-based alerting, not just
      metric-based.
- [ ] `deploy/RUNBOOK.md` gains a "search logs in Grafana" section,
      complementing the plain `kubectl logs` steps M7 already documents.

## M14 — CI: streamlined security scanning & reporting

M7 added a Trivy image-vulnerability scan to `deploy.yml`, but its output only
ever lands in that job's raw log lines — there's no persisted, browsable, or
trackable report anywhere, and there's no static code analysis or linting
gate at all yet (`verify.yml` runs tests/build, not a quality/lint scan). This
milestone turns "a scan runs" into "a scan whose findings are visible and
actionable," adds that missing code-quality gate, and cleans up the CI
scanning setup along the way rather than letting near-duplicate scan steps get
bolted on ad hoc later.

- [ ] Both Trivy steps in `deploy.yml` (app image, migrator image) emit SARIF
      (`format: sarif`) instead of (or alongside) the current table output,
      uploaded via `github/codeql-action/upload-sarif`, one `category` per
      image so the two don't overwrite each other's findings.
- [ ] `package` job's `permissions:` block gains `security-events: write`,
      required for the SARIF upload step.
- [ ] Decide and implement the fail-the-build mechanism now that SARIF upload
      needs the step to complete rather than exit non-zero: either run Trivy
      twice per image (today's `exit-code: "1"` table-format gate + a separate
      SARIF-format reporting run against the same already-built image), or a
      single SARIF run with a follow-up step that parses the SARIF output and
      exits non-zero on any HIGH/CRITICAL finding. Pick whichever keeps
      `deploy.yml` simplest to read; document the choice here once made.
- [ ] Confirm GitHub code scanning / Advanced Security is actually available
      for this repo's visibility/plan before relying on the Security tab as
      the primary place findings live — fall back to the artifact-upload
      option below if not.
- [ ] Streamline the two near-duplicate Trivy steps (app image, migrator
      image) — a reusable composite action or a matrix job, whichever reads
      cleaner — instead of copy-pasted step blocks that can drift apart.

### Code quality & linting (SonarQube)

Adds a static-analysis gate that doesn't exist yet — today `verify.yml` proves
the app builds and its tests pass, not that the code meets any quality bar
(complexity, duplication, code smells, security hotspots in source rather than
in a built image).

- [ ] **Decide SonarCloud vs. self-hosted SonarQube** before building anything
      else here — this changes the whole shape of the work:
  - *SonarCloud* (sonarcloud.io): free for public repos, zero extra
    infrastructure, analysis runs as a normal `verify.yml` step via
    `sonarsource/sonarqube-scan-action` (or the Gradle `org.sonarqube` plugin)
    posting results to Sonar's own hosted dashboard. Simplest-viable option
    if this repo stays public (it already must be, per M7 — the GHCR image
    packages aren't private either).
  - *Self-hosted SonarQube*: another workload on the xbmc k3s node (its own
    Postgres-backed server, another Helm release, another thing to back up
    and keep patched) — only worth it for data locality or if the repo ever
    goes private without an Advanced-Security-equivalent budget. Given this
    project's own bias toward the simplest viable option (see `CLAUDE.md`),
    default to SonarCloud unless a concrete reason to self-host shows up.
- [ ] Add the Sonar scan as a `verify.yml` job (PR-gating, not `deploy.yml` —
    it's a code-quality check on every push/PR, not a release-time concern),
    running after `./gradlew build` so it can pick up JaCoCo coverage +
    compiled classes for the backend and the Vitest coverage output for `web/`
    in one combined analysis (Sonar supports multi-language projects natively
    — no need for two separate Sonar projects).
- [ ] Backend: add `jacoco` to `backend/build.gradle.kts` so Sonar has real
    coverage data to report, not just static analysis.
- [ ] Configure a quality gate (Sonar's default "Sonar way" gate is a
    reasonable starting point) and decide whether it's blocking (fails the PR
    check) or advisory-only to start, tightened later once the existing
    codebase's baseline is known — don't let day-one noise block every PR.
- [ ] `sonar-project.properties` (or the Gradle plugin's config block) at the
    repo root, scoping analysis to `backend/src/main` + `web/src`, excluding
    generated code (`web/dist`, `build/`, `node_modules`).

### Reporting

Where scan results actually surface, once this milestone lands:

- **GitHub Security tab** (primary, Trivy): each image's vulnerability
  findings appear as code scanning alerts, filterable by severity and
  trackable over time (new vs. already-known), one alert group per
  `category`. This is the main destination — no more digging through Actions
  logs to know what an image scan found.
- **SonarCloud/SonarQube dashboard** (primary, code quality): bugs, code
  smells, security hotspots, duplication, and coverage trends per commit/PR,
  with a PR check comment summarizing new issues introduced by that PR
  specifically (not the whole codebase's backlog) — the same "don't drown a
  PR in pre-existing noise" principle as the quality-gate bullet above.
- **Workflow run summary**: a short human-readable count ("N high, M
  critical" for Trivy; "N new issues, quality gate passed/failed" for Sonar)
  written to `$GITHUB_STEP_SUMMARY`, so a failed run shows *why* at a glance
  on the run's own page, without opening the Security tab or the Sonar
  dashboard.
- **Artifact upload** (fallback, if code scanning isn't available on this
  repo): the human-readable (`format: table`) Trivy report saved as a build
  artifact per image, same pattern as the OpenAPI spec artifact from M7 —
  browsable via the run's Artifacts list, subject to the same retention
  window.
- `deploy/RUNBOOK.md` gains a short "where to see the last scan's findings"
  pointer once the mechanisms above are picked, so it's discoverable without
  re-deriving it from `deploy.yml`/`verify.yml`.

## M15 — Admin console: usage, cost & planning

An operator-facing view over how the instance is actually used — both the
assistant's spend and plain product usage. `assistant_run` already records
every call's token counts (M8), but there's no concept of an "admin" user at
all yet, no visibility into spend (current or trending, instance-wide or
per-user), and no cross-user view of adoption (how many users, how active,
which features). Builds directly on M8's data model for the cost side, and
folds in the cost-estimation idea from the backlog (a per-model pricing table
lands here, as this page's first consumer, rather than as a standalone
add-on).

- [ ] An admin flag on the user (`app_user.is_admin` or similar — exact shape
      decided at design time) plus the `SecurityFilterChain`/`@CurrentUser`
      plumbing to check it. Non-admin users get a 404, not a 403, on admin
      routes — consistent with the rest of the app's "don't leak existence"
      rule (docs/DESIGN.md §3.2), extended here to mean a non-admin can't
      even tell the admin console exists.
- [ ] A per-model pricing table (input/output $ per token, or per 1M tokens)
      to turn `assistant_run.input_tokens`/`output_tokens` into an estimated
      dollar figure. Display/planning only — the token-only monthly budget
      check the assistant module already enforces (M8 D8) stays the hard
      gate, unchanged.
- [ ] SQL aggregation of `assistant_run` by day/week/month, both instance-wide
      and per-user. Decide at design time how admin queries cross the usual
      "a row belongs to `@CurrentUser`" authorization boundary every other
      module follows — this is the first feature that legitimately needs to
      see every user's data, likely via a dedicated admin-only repository
      method or module rather than bending the existing per-user pattern.
- [ ] Product-usage aggregation alongside the assistant numbers: total user
      count (and signups over time, from `app_user.created_at`); per-feature
      activity — todos created/completed per user/period (`todo`), number of
      projects and tasks defined per user (`projects`), journal entries added
      per user/period (`journal`) — each pulled through that same module's
      existing repository, not new tables. Login frequency needs a new
      signal: `refresh_token.created_at` isn't a clean proxy (a row is also
      created on every silent token rotation, not just an explicit login —
      docs/DESIGN.md §6), so this likely needs e.g. an `app_user.last_login_at`
      column touched by `POST /auth/login` specifically.
- [ ] `GET /admin/usage` (exact shape decided at design time): assistant
      token counts and estimated cost, bucketed by day/week/month and
      filterable by user, plus the product-usage figures above (user counts,
      login activity, per-feature activity).
- [ ] Web: an Admin nav entry visible only to admin users (sourced from
      `/me`), route-guarded like the existing `ProtectedRoute`; a dashboard
      covering both halves — assistant tokens/estimated cost over day/week/
      month with a per-user breakdown and a forward-looking planning view
      (e.g. a trend-based projection against the monthly budget), and a
      product-usage view (user counts, login activity, per-feature adoption:
      todo activity, projects defined, journal-entry frequency). Exact chart/
      table set decided at design time.
- [ ] Tests: authorization tests proving a non-admin gets 404 on every admin
      route; aggregation query tests against Testcontainers.

### Backlog (unscheduled)

Bring-your-own Anthropic key per user · Batch API for scheduled summaries (50 %
cost) · local / self-hosted model option · embeddings / semantic search over
journal history if Postgres FTS is outgrown · tags · attachments · recurring
todos · reminders/push notifications · journal revision history · project
templates · CSV/Markdown export · calendar (ICS) feed · dark/light theming
polish.
