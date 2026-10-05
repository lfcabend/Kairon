# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

**M0 (walking skeleton), M1 (authentication), M2 (daily todo), M3 (daily
journal), M4 (projects core), M5 (Gantt & dependencies), M6 (Today), M7
(hardening & prod), M8 (assistant foundations & todo suggestions), M8.5
(AI project generation from a description), M9 (weekly & monthly
execution summaries), and M10 (weekly journal reflection, plus a Batch API
migration for M9's scheduled summary sweep) are implemented.**
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
  `DevDataSeeder`), `todo` (`api` port `TodoApi`/`TodoItemView` (gained
  `periodStats`/`PeriodStats` for M9's execution-summary aggregation), `domain`
  `TodoItem`/`TodoStatus`, `repo`, `app` `TodoService`/`RolloverService`/
  `TodoProperties`, `config`, `web` `TodoController`), `journal` (`api` port
  `JournalApi`/`JournalEntryView`/`JournalSearchHitView`/`JournalSearchPage`,
  `domain` `JournalEntry`, `repo` `JournalEntryRepository` (incl. the native
  full-text `search` query + `JournalSearchRow` projection), `app`
  `JournalService`/`JournalSearchService`/`JournalMapper`/`JournalProperties`,
  `config`, `web` `JournalController`), `projects` (`api` port `ProjectsApi`/
  `ProjectView`/`ProjectTaskView`/`ProjectPage`/`ProjectTaskPage`/
  `ProjectPlanCommand`/`PlannedTask`/`PlannedDependency` (M8.5, the
  string-`key`-based cross-reference shape `createFromPlan` takes)/
  `ProjectPeriodStats` (M9 — per-project completed-task counts, estimate-vs-actual
  hours, and the denormalized owning category, for execution-summary
  aggregation), `domain`
  `ProjectCategory`/`Project`/`ProjectStatus`/`ProjectSize`/`ProjectTask`/
  `ProjectTaskStatus`, `repo` `ProjectCategoryRepository`/`ProjectRepository`/
  `ProjectTaskRepository` (incl. the ad-hoc-join `findDueOrOverdue` query and,
  for M9, `projectPeriodStats` — a `GROUP BY`/`LEFT JOIN ProjectCategory`
  aggregate of tasks completed in a date range)/
  `TaskDependencyRepository` (M5), `app`
  `ProjectCategoryService`/`ProjectService`/`ProjectTaskService` (implements
  `ProjectsApi`, incl. M6's `requireTask` — resolves a task the caller owns via
  `TaskResolution`, 404 if missing/foreign; M8.5's `createFromPlan` delegates
  to `ProjectPlanImportService` via a `@Lazy`-injected reference, breaking
  the constructor cycle that collaborator's own reuse of `ProjectTaskService`
  would otherwise create; `categories` delegates to its same-module peer
  `ProjectCategoryService#list`, mapped down to id+name — added so the
  assistant module can read the user's categories through the port without
  depending on `projects.app`)/`ProjectsProperties`/`SortParsing`/
  `TaskResolution` (M5, the shared "resolve a task with no projectId in the
  URL" helper extracted out of `ProjectTaskService`)/`TaskDependencyService`/
  `TaskDependencyView`/`TaskDependencyMapper` (M5 — dependency CRUD incl. cycle
  rejection and the computed `violatesConstraint` flag)/`ProjectPlanImportService`
  (M8.5, package-private — creates a project + its ≤2-level task tree + its
  dependency edges from a `ProjectPlanCommand` in one transaction, calling
  `ProjectService`/`ProjectTaskService`/`TaskDependencyService` as ordinary
  collaborators; flattens excessive nesting and drops unresolvable/cycle-forming
  dependency edges rather than failing the whole import; `resolveCategory`
  passes an already-matched `categoryId` straight through, or creates a new
  category for a `newCategoryName` via `ProjectCategoryService` in the same
  transaction — falling back to a same-name existing category on a create
  conflict rather than failing the import), `config`, `web`
  `ProjectCategoryController`/`ProjectController`/`ProjectTaskController`/
  `TaskDependencyController` (M5)), `planning` (M6 — no `domain`/`repo`, no
  migration: a pure aggregation over `todo`/`projects`/`journal` for the Today
  screen. `app` `PlanningService` (`today` composes `TodoApi.forDay`/
  `ProjectsApi.dueOrOverdue`/`JournalApi.hasEntryForDay`; `promote` calls
  `ProjectsApi.requireTask` then `TodoApi.create` to link a project task into
  today's todo list), `web` `PlanningController`/`PlanningDtos`),
  `meta` (the M0 `PingController`; `DeployInfoContributor`, an `InfoContributor`
  adding the running image ref + deploy time to `/actuator/info` for the About
  page — the commit and build time are already there via `BuildProperties`), and
  `assistant` (M8 — the only module allowed to import the Anthropic SDK. `domain`
  `AssistantRun`/`AssistantRunKind`/`AssistantRunStatus`/`AssistantSuggestedTask`/
  `AssistantSuggestedTaskStatus`/`AssistantSuggestedProject`/
  `AssistantSuggestedProjectStatus` (M8.5)/`AssistantBatch`/`AssistantBatchStatus`
  (M10 — the latter mirrors `MessageBatch.ProcessingStatus.Known` 1:1), `repo`
  `AssistantRunRepository` (incl. the
  monthly-token-budget `sumTokensSince` query and, for M10, `findByBatchId`)/
  `AssistantSuggestedTaskRepository`/
  `AssistantSuggestedProjectRepository` (M8.5)/`AssistantBatchRepository`
  (M10 — `findByStatusNot`, backing `SummaryBatchPollingScheduler`'s sweep),
  `llm` `AnthropicClient` (the SDK wrapper interface, now
  `suggestTodos`/`generateProjectPlan`/`generateSummary` (M9 — plain markdown,
  not structured output)/`generateReflection` (M10, identical shape to
  `generateSummary`)/`submitBatch`/`pollBatch`/`retrieveBatchResults` (M10 —
  the Batch API trio used only by `SummaryBatchDispatcher`/
  `SummaryBatchPollingScheduler`))/`AnthropicClientImpl`
  (Resilience4j `@CircuitBreaker`, structured outputs via
  `StructuredMessageCreateParams`)/`FakeAnthropicClient` (`local` profile +
  `kairon.assistant.fake-client=true` — stands in for Playwright e2e, never a
  real network call; M10 adds a same-tick-"ended" fake for its three batch
  methods)/`TodoSuggestionsPayload`/`SuggestedTaskPayload`/
  `ProjectPlanPayload`/`PlannedTaskPayload`/`PlannedDependencyPayload` (M8.5,
  the structured-output shape for a `PROJECT_GENERATION` run), `app`
  `AssistantRunService` (run lifecycle, budget/availability gating, suggestion
  capping/clamping; M8.5's `requestProjectPlan` follows the same shape; M9's
  `requestSummary` queues a `PENDING` run and returns — the actual generation
  is dispatched by the caller only after that transaction commits; M10's
  `requestJournalReflection` follows the same queue-and-return shape, refusing
  up front with a 422 if the target week has no journal entries, and the
  formerly-summary-only in-flight guard is now `requireNoRunInFlight(userId,
  Set<AssistantRunKind>)` — the summary call site passes
  `{WEEKLY_SUMMARY, MONTHLY_SUMMARY}` unchanged, reflection passes its own
  single kind so a reflection run never blocks, or is blocked by, a summary
  run)/
  `SuggestedTaskService` (accept/dismiss)/`SuggestedProjectService` (M8.5 —
  accept/dismiss for a whole plan; accept cascades caller-excluded task keys
  to their children, then calls `ProjectsApi.createFromPlan`)/
  `SummaryGenerationService` (M9 — a separate `@Async`/`@Transactional` bean,
  deliberately not a self-call on `AssistantRunService`, since Spring's
  proxy-based AOP can't intercept one; builds the context, calls
  `AnthropicClient.generateSummary`, and persists `SUCCEEDED`/`FAILED`; still
  used for on-demand `POST /assistant/summaries` after M10's batch migration —
  only the `@Scheduled` sweep moved off this path)/
  `ReflectionGenerationService` (M10 — identical control flow to
  `SummaryGenerationService`, for `JOURNAL_REFLECTION` runs; no appended
  stats table, unlike a summary's output)/
  `SummaryScheduler` (M9 — two `@Scheduled` cron triggers, weekly
  Monday AM/monthly on the 1st, both UTC by default; resolves "yesterday" via
  the shared `Clock` bean so a Monday firing summarizes the week that just
  ended; every per-user failure is caught and logged so the sweep always
  reaches the next user; since M10, queues every opted-in user's `PENDING`
  run as before but then hands the whole firing's run ids to
  `SummaryBatchDispatcher` as one Anthropic Message Batch instead of
  dispatching each via `SummaryGenerationService` individually)/
  `SummaryBatchDispatcher` (M10, package-private — used only by
  `SummaryScheduler`; builds each queued run's `SummaryContextBuilder.Context`
  exactly like `SummaryGenerationService` does, stores the rendered stats
  table under `input_snapshot["statsTable"]` before submitting so it's never
  recomputed hours later at poll time, then submits one
  `AnthropicClient.submitBatch` call and tags every staged run with the new
  `assistant_batch` row's id)/
  `SummaryBatchPollingScheduler` (M10, package-private — a fixed-delay
  `@Scheduled` job, `kairon.assistant.summary.batch-poll-interval` default
  `PT15M`, separate from `SummaryScheduler` since it's a different trigger
  shape and responsibility: it checks every non-`ENDED` `assistant_batch`,
  and once Anthropic reports a batch `ENDED`, reads back every per-request
  result and writes `SUCCEEDED`/`FAILED` onto the matching run — one bad
  result fails only that run, never the rest of the batch)/
  `TodoSuggestionContextBuilder` (the system prompt + per-request context)/
  `ProjectPlanContextBuilder` (M8.5, lighter than the todo-suggestion builder
  — the only cross-module aggregation is `ProjectsApi.categories`, fetched so
  the model can match the user's existing categories by name instead of
  always proposing a new one; the same list comes back on `Context` so
  `AssistantRunService` can resolve the model's answer without a second
  query)/
  `SummaryContextBuilder` (M9 — five `todo`/`projects` data sources, no
  journal, no tone; `Context.renderStatsTable()` is a pure function that
  formats the same structured values already in the prompt into a markdown
  table, appended to the model's narrative after the call returns — never
  model-authored)/`SummaryPeriod` (`WEEK`/`MONTH`, `.resolve(LocalDate)` ->
  the Mon–Sun week or calendar month containing that date, `.kind()` -> the
  matching `AssistantRunKind`; M10's `requestJournalReflection` reuses only
  `WEEK.resolve(LocalDate)` for the date math, never `.kind()`, since that
  always maps to `WEEKLY_SUMMARY`)/
  `JournalReflectionContextBuilder` (M10 — three sources, deliberately
  narrower than `SummaryContextBuilder`: the week's `JournalApi.range` entries
  rendered in full (day, title, mood, content, capped at
  `journal-reflection.max-entries`), one sentence of `TodoApi.periodStats`,
  and the top `journal-reflection.max-projects` projects by
  `ProjectsApi.projectPeriodStats`, named only; three fixed system-prompt tone
  variants selected by the user's `assistant.tone` preference — no stats
  table, the model's narrative is the entire output)/
  `AssistantProperties` (gained a `Summary` nested config record — cron
  schedules, `zone`, `maxHighlightItems`, `effort` — and, for M10, a
  `JournalReflection` nested record — `maxEntries`/`maxProjects`/`effort`, no
  cron/zone fields since this kind has no scheduled trigger)/`Horizon`/
  `AssistantMapper`/
  `AssistantRunView`
  (M8.5: gained a `suggestedProject` field alongside `suggestions`, one run
  view for both kinds; M9: gained `outputMarkdown`, populated only for
  `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` runs; M10: the same field is now also
  populated for `JOURNAL_REFLECTION` runs, no shape change needed)/
  `AssistantRunPage` (M9 — the
  `content`/`page`/`totalElements` shape backing `GET /assistant/runs`, same
  convention as `ProjectPage`)/`AssistantSuggestedTaskView`/
  `AssistantUpstreamException`/
  `AssistantSuggestedProjectView`/`PlannedTaskView`/`PlannedDependencyView`/
  `PersistedProjectPlan` (M8.5 — the latter is what's actually stored as
  `assistant_suggested_project.plan`: the model's payload plus the
  caller-supplied `startDate`/`endDate` it never chooses itself, plus a
  `categoryId`/`categoryName` pair resolved against the user's existing
  categories right after the model call — a non-null `categoryId` means an
  exact name match; a null `categoryId` with a non-null `categoryName` means
  no match, to be created as a new category on accept), `config`
  `AssistantConfig`, `web` `AssistantRunController` (M9: gained
  `POST /assistant/summaries` and `GET /assistant/runs`; M10: gained
  `POST /assistant/journal-reflection`)/`SuggestedTaskController`/
  `AssistantDtos` (M10: gained `JournalReflectionRequest`)/`ProjectPlanController`/
  `SuggestedProjectController`/
  `ProjectPlanDtos` (M8.5)). Extends `TodoApi` with `range` and, for M9,
  `periodStats`; `JournalApi` with `range`, now also read directly by
  `AssistantRunService.requestJournalReflection` (M10, the empty-week check)
  and by `JournalReflectionContextBuilder`;
  `ProjectsApi` with `openTasksInActiveProjects`, for M8.5 `createFromPlan`
  and (added for this same feature, post-launch) `categories`, and for M9
  `projectPeriodStats`;
  and `UserAccountApi` with
  `assistantPreferences` (backed by `identity.app.AssistantPreferenceMapper`,
  a defensive parse of `app_user.preferences.assistant`, now including a
  `projectGenerationEnabled` flag) and, for M9, `usersOptedIntoExecutionSummaries`
  (an unauthenticated, no-`@CurrentUser` native jsonb query — the
  `@Scheduled` sweep's own cross-user use, never exposed on any controller) —
  all five added specifically for the assistant module's context-building (M8/M9).
  Migrations: `V001__identity.sql` (`app_user` + `refresh_token`),
  `V002__todo.sql` (`todo_item`), `V003__journal.sql` (`journal_entry` +
  generated `content_tsv` + GIN index), `V004__projects.sql`
  (`project_category` + `project` + `project_task`, plus the FK M2 left as a
  bare `uuid` on `todo_item.source_project_task_id`), `V005__task_dependencies.sql`
  (`task_dependency` — predecessor/successor edges, cycle-rejected in the
  service, no soft delete; a task's soft delete or a project's cascade also
  hard-deletes the edges touching it), `V007__assistant.sql` (`assistant_run` +
  `assistant_suggested_task`, both hard-delete-only — see docs/DATA_MODEL.md),
  `V008__project_plan.sql` (widens `assistant_run.kind` to add
  `PROJECT_GENERATION`; adds `assistant_suggested_project`, M8.5),
  `V009__project_category_rank.sql` (`project.category_rank` — unrelated to
  the assistant module; a projects-feature change merged to `main` while this
  milestone was being planned, which is why M10's own migration below is
  `V010`, not the `V009` its own plan document assumed), `V010__assistant_batch.sql`
  (`assistant_batch` + `assistant_run.batch_id`, M10's Batch API migration for
  M9's scheduled summary sweep — see docs/DATA_MODEL.md).
  M6 adds no migration; there is no `V006`. M9 and M10's own `JOURNAL_REFLECTION`
  feature likewise add no migration — M8's `V007` deliberately over-specified
  `assistant_run.kind`/`output_markdown`/`period_start`/`period_end` for
  exactly M9's and M10's use.
- `web/` — React SPA. Auth lives under `src/features/auth/`; the day view under
  `src/features/todo/` (`DayView` + `DateNav`/`DaySummary`/`QuickAdd`/`TodoList`/
  `TodoRow`, rollover in `RolloverPrompt`/`RolloverPickerDialog`/`useRollover`,
  hooks + `todoKeys`); the journal under `src/features/journal/`
  (`JournalDayView` + `JournalDateNav` (calendar popover with has-entry dots)/
  `EntryList`/`EntryCard`/`EntryEditor` (Tiptap WYSIWYG)/`MoodPicker`,
  `JournalSearchPage`, hooks in `useJournal.ts`/`useJournalSearch.ts` +
  `journalKeys`); projects under `src/features/projects/` (`ProjectListPage`
  (category sections + `ProjectPriorityList` for "Sort by: Priority", plus a
  "New project from description" button (M8.5) opening
  `assistant/GenerateProjectDialog`)/
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
  `client.ts` since it's public and outside `/api/v1`); the Today landing
  screen (M6) under `src/features/today/` (`TodayPage` — no date-nav, always
  "today", reuses `RolloverPrompt`/`useAutoRollover`/`useRolloverPreview` from
  `features/todo`/`TodayTasks` (today's interactive todo list, reusing
  `DaySummary`/`QuickAdd`/`TodoList` and the `features/todo` hooks bound to
  today's date)/`DueTasksPanel` (due/overdue project tasks with a one-click
  "Add to today" that flips to "Added" once a matching todo is in the
  `useTodos` cache)/`JournalPrompt` (inline quick-add, or a "Continue in
  Journal →" link once today has an entry), hooks in `useToday.ts` +
  `planningKeys`); the assistant under `src/features/assistant/` (M8 —
  `SuggestTodosButton` (a two-step dialog: horizon choice, then
  `SuggestedTaskList` review-and-accept, used on both `TodayPage` and
  `DayView`)/`SuggestedTaskList`/`AssistantSettings` (embedded in
  `AccountPage`; M10 brings it to four real toggles — todo suggestions,
  project generation, execution summaries, and journal reflection — plus a
  tone `Select` (encouraging/balanced/direct) and model override. The
  journal-reflection checkbox is the one exception to this page's own
  "checkbox PATCHes immediately" pattern: turning it **on** opens a
  `Dialog`-based confirmation (reusing the existing component, not a new
  `alert-dialog` package) naming that full journal text is sent to Anthropic,
  and only commits the `PATCH` once confirmed there; turning it off needs no
  confirmation, same as the other three); M8.5 adds
  `GenerateProjectDialog` (a two-step
  dialog on `ProjectListPage`: description+dates form, then
  `ProjectPlanReview` in the same dialog once a plan comes back — no field
  editing, only per-task exclude checkboxes that cascade to children, plus
  (added for this same feature, post-launch) a read-only category badge —
  "Home" for a matched existing category, "New category: Home" for one the
  model proposed with no match, nothing if uncategorized; "Create project"
  navigates to the new project's normal detail page)/
  `ProjectPlanReview`; M9 adds `SummariesPage` (a standalone top-level screen,
  a deliberate exception to M8/M8.5's "embed a button in an existing screen"
  pattern — history list, "Generate weekly/monthly summary" buttons; a row
  click opens `SummaryDetail`)/`SummaryDetail` (a dialog rendering one run's
  `outputMarkdown` — narrative + the deterministic stats table — via
  `react-markdown`/`remark-gfm`, this app's first read-only markdown/GFM-table
  render; while the run is `PENDING`/`RUNNING`, polls instead and shows a
  spinner); M10 adds `JOURNAL_REFLECTION` as a third kind on both
  `SummariesPage` (a "Generate weekly reflection" button, shown only when
  `me.preferences.assistant.journalReflection.enabled`, targeting "this week"
  with no date picker — same simplicity as M9's own summary buttons) and
  `SummaryDetail` (a `KIND_LABEL` entry; rendering itself needed no change,
  already kind-agnostic), hooks in `useAssistant.ts`/`useAssistantRuns.ts` (M9
  — the first `refetchInterval`-based polling hook in this app; M10 adds
  `useRequestJournalReflection`, same shape as `useRequestSummary`) +
  `assistantKeys` (gained `runs.list(filter)`).
  `src/components/AppLayout.tsx` is the
  top-nav shell wrapping the protected routes, "Today" first in the nav order
  and the app's default landing route (M6); M9 adds a "Summaries" top-level
  nav entry after "Projects" (`/summaries`), the same deliberate exception to
  M8/M8.5's own nav pattern. API access is hand-written
  types in `src/lib/api/types.ts` plus `todo.ts`/`journal.ts`/`projects.ts`/
  `auth.ts`/`about.ts`/`planning.ts`/`assistant.ts` over `client.ts` (the
  single-flight 401→refresh→retry fetch wrapper). Tests: Vitest + MSW
  (`src/test/msw/`); Playwright happy paths in `web/e2e/` (`npm run
  test:e2e`, needs a running full stack).
- `deploy/`, `docker/` — Helm chart and image from M0; M1 adds a `KAIRON_JWT_SECRET`
  app Secret wired into the Deployment. The About page adds `KAIRON_IMAGE_REF` /
  `KAIRON_DEPLOYED_AT` env vars (rendered at `helm upgrade` time — so an upgrade
  always rolls the Deployment, even with no other change) and a `GIT_COMMIT`
  Docker build arg (wired from `Taskfile.yml`'s `image`/`image-push` tasks).
  M7 (see `docs/milestones/M7-hardening-prod.md`) adds: a new
  `docker/migrator.Dockerfile` (Flyway CLI + this repo's SQL migrations) built
  and pushed alongside the app image, run by the chart's new
  `templates/migration-job.yaml` (a `pre-install,pre-upgrade` Helm hook) in
  every k8s environment — the app itself now always starts with
  `SPRING_FLYWAY_ENABLED=false` in k8s; `templates/hpa.yaml` (off everywhere
  except the new `values-prod.yaml`, a still-unused overlay for a hypothetical
  future managed cluster — xbmc stays the real deployed environment);
  `templates/backup-pvc.yaml`/`backup-cronjob.yaml` (a `pg_dump` CronJob to a
  local PVC, enabled only in `values-xbmc.yaml`); and new
  `.github/workflows/deploy.yml` (build + Trivy-scan + push both images to GHCR,
  publish the OpenAPI spec, then `helm upgrade` against xbmc — auto-deploys on
  every merge to `main`, no approval gate yet). `SecurityConfig` gained an
  explicit `/actuator/**` deny (beyond `health`/`info`) and a public allow for
  `springdoc-openapi`'s `/v3/api-docs`/`/swagger-ui/**` (new
  `springdoc-openapi-starter-webmvc-ui` + `micrometer-registry-prometheus`
  dependencies — the latter present but not yet exposed/scraped). New
  `deploy/RUNBOOK.md` documents the manual secret-management pattern (no SOPS/
  sealed-secrets), backup restore, and JWT/DB secret rotation. M8 adds the
  `assistant.*` values block (`enabled: false` by default in every values
  file, `existingSecret` for an operator-created `ANTHROPIC_API_KEY` Secret —
  no Helm-generated fallback key, unlike the JWT secret), a conditional
  `ANTHROPIC_API_KEY` env var in `deployment.yaml`, `KAIRON_ASSISTANT_*`
  ConfigMap entries, and a `deploy/RUNBOOK.md` section on enabling/rotating
  the key.

Next milestone is **M11+ — Mobile & beyond** (see `docs/ROADMAP.md`) — no
milestone plan has been drafted for it yet.

## What Kairon is

A single-user-scale personal productivity system (multi-user accounts, but "personal"
data — no assignees/teams/permissions): **Daily Todo**, **Daily Journal**, small
**Projects** with a Gantt chart, a **Today** aggregation screen, and an opt-in,
off-by-default Anthropic-powered **assistant** (M8 ships todo suggestions;
weekly/monthly execution summaries and journal reflection follow in M9–M10).

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
`planning` (the "Today" aggregation), and `assistant` (M8). Rules enforced by **ArchUnit
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
PostgreSQL 18 · Flyway · Spring Security 7 + JWT · MapStruct · Bean Validation ·
springdoc-openapi · Bucket4j (rate limiting) · Testcontainers · ArchUnit ·
React 18 + TypeScript + Vite · React Router · TanStack Query · Zustand ·
React Hook Form + Zod · Tailwind + shadcn/ui · `gantt-task-react` ·
`react-markdown` + `remark-gfm` (M9, read-only) ·
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
| E2E | `task e2e` — spins up a throwaway `kairon_e2e` database + backend (local profile), runs Playwright, tears both down |
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
