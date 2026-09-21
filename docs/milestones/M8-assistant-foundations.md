# M8 — Assistant foundations & todo suggestions — implementation plan

Status: **Accepted — implemented.** Companion to [`../ROADMAP.md`](../ROADMAP.md) (M8 acceptance
criteria), [`../DESIGN.md`](../DESIGN.md) §13, [`../DATA_MODEL.md`](../DATA_MODEL.md)
(`assistant_run`/`assistant_suggested_task`, already specified there), and
[`../adr/0002-ai-assistant-anthropic.md`](../adr/0002-ai-assistant-anthropic.md).
This is the detailed plan for the milestone; the roadmap checkboxes are the
acceptance test. Follows the structure and conventions of
[`M6-today.md`](M6-today.md) / [`M7-hardening-prod.md`](M7-hardening-prod.md).

M8 adds the **`assistant` module** — Kairon's first outbound dependency on a
third party — and its first feature, todo suggestions. Unlike M6 (no schema, no
new dependency), this milestone is the opposite shape: a new Flyway migration, a
new external SDK contained by an ArchUnit rule, new cross-module port methods on
three existing `api`s, a circuit breaker, a per-user spend cap, and a settings
surface. M9 (summaries) and M10 (journal reflection) build on the `assistant_run`
lifecycle and `AnthropicClient` wrapper this milestone establishes; nothing here
is thrown away by them.

---

## 1. Scope

### In

- New module `com.kairon.assistant` (`domain`, `repo`, `app`, `web`, `config`,
  `llm`) — no `api` sub-package (nothing depends on `assistant` yet; see D1).
- `V007__assistant.sql`: `assistant_run` + `assistant_suggested_task`, exactly as
  specified in `DATA_MODEL.md` — including the full `kind` check constraint
  (`TODO_SUGGESTION | WEEKLY_SUMMARY | MONTHLY_SUMMARY | JOURNAL_REFLECTION`)
  even though only `TODO_SUGGESTION` is produced this milestone (D2).
- Four narrow extensions to existing module ports: `TodoApi.range`,
  `JournalApi.range`, `ProjectsApi.openTasksInActiveProjects`,
  `UserAccountApi.assistantPreferences` (D3).
- `assistant.llm.AnthropicClient` — a thin wrapper over
  `com.anthropic:anthropic-java`, structured-output todo suggestions, a
  Resilience4j circuit breaker, an SDK-level request timeout (D4–D6).
- Run lifecycle: `POST /assistant/todo-suggestions` (synchronous — D7),
  `GET /assistant/runs/{id}`, `DELETE /assistant/runs/{id}`.
- `POST /assistant/suggested-tasks/{id}:accept` / `:dismiss`.
- Per-user monthly token budget enforced pre-flight (D8); a dedicated Bucket4j
  rate limit on `/assistant/**` reusing the existing filter class (D11); a
  Micrometer token counter; RFC 7807 failure mapping for every upstream failure
  mode (D12).
- `kairon.assistant.*` config, `ANTHROPIC_API_KEY` wired through
  `configmap.yaml`/`secret.yaml` and every values file, off by default
  everywhere (D16).
- Web: a settings section (opt-in toggles + data-sharing notice), a "Suggest
  todos" entry point on Today and the Day view, and a review-and-accept list
  (D14).
- Tests: MockMvc with `AnthropicClient` faked, service unit tests, an
  `@SpringBootTest` flow test, ArchUnit tests for the SDK-containment rule and
  the new module's internals, Vitest component tests, a Playwright happy path.

### Out (deferred, with the milestone/backlog item that picks it up)

- **Weekly/monthly summaries and journal reflection** — M9/M10. This milestone
  only produces `TODO_SUGGESTION` runs; the other three `kind` values exist in
  the schema (D2) but nothing writes them yet.
- **`@Async` execution and `@Scheduled` auto-generation** — not needed until M9;
  M8's one run kind is synchronous (D7).
- **Bring-your-own-key per user** — ROADMAP backlog; `ADR 0002` explicitly
  defers it.
- **Per-feature (rather than per-user) token budgets** — `DESIGN.md` §14 lists
  this as an open question beyond M8; ship the simpler per-user budget now.
- **A public-facing "why was this suggested" explanation UI beyond the stored
  `rationale` string** — the field exists (`DATA_MODEL.md`) and is shown as
  plain text in the review list; no dedicated UX beyond that.
- **Prometheus dashboards for the new token counter** — M12 wires up scraping;
  M8 only emits the Micrometer meter.

---

## 2. Decisions locked for this milestone

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **`com.kairon.assistant` has no `api` sub-package.** It gets `domain`, `repo`, `app`, `web`, `config`, and a fifth package `llm` (the SDK wrapper — ArchUnit's containment target, not a cross-module port). `ArchitectureTest` still gains `assistantInternalsArePrivate` (mirroring `todoInternalsArePrivate` etc.) for when a future module does need to read assistant data, even though nothing does yet. | No other module reads `assistant` — dependency direction is one-way (`assistant → todo/journal/projects/identity`). Adding an unused `api` package now would be speculative; the private-internals rule costs nothing to add pre-emptively and matches every other module's shape. |
| D2 | **`V007__assistant.sql` defines the full `kind` and `status` check constraints from `DATA_MODEL.md` up front** (`WEEKLY_SUMMARY`/`MONTHLY_SUMMARY`/`JOURNAL_REFLECTION` included), even though M8 only ever inserts `TODO_SUGGESTION` rows. | Avoids a near-certain M9 migration (`ALTER TABLE ... DROP CONSTRAINT ... ADD CONSTRAINT ...`) just to widen an enum that `DATA_MODEL.md` already fully specifies today — the same reasoning M4's `project_task` schema used for columns later milestones grew into. |
| D3 | **Four new port methods, each sized to exactly what the context-builder needs**, following the `ProjectsApi.requireTask` precedent (M6 D2): `TodoApi.range(UserId, LocalDate from, LocalDate to)` (exposes `TodoService.range`, which already exists internally for `GET /todo?from=&to=`), `JournalApi.range(UserId, LocalDate from, LocalDate to)` (same — exposes existing `JournalService.range`), `ProjectsApi.openTasksInActiveProjects(UserId)` (new repo query — see §4.1), `UserAccountApi.assistantPreferences(UserId)` (new — a typed projection of `app_user.preferences.assistant`, not the raw `Map<String,Object>`). | `assistant` cannot reach `todo.domain`/`journal.domain`/`projects.domain`/`identity.domain` (ArchUnit), and per §13.4 needs todo history, recent journal entries, and open project-task context to build a prompt, plus the user's opt-in flags/model override/tone to decide whether to run at all. Returning a typed `AssistantPreferencesView` instead of the raw preferences map keeps the jsonb-parsing logic in `identity` (which already owns `AppUser.preferences`) rather than duplicating it in `assistant`. |
| D4 | **`assistant.llm.AnthropicClient` (our interface) is always constructed as a Spring bean**, wrapping the SDK's `com.anthropic.client.AnthropicClient` (`AnthropicOkHttpClient.builder().apiKey(...).timeout(Duration...).build()`), even when `ANTHROPIC_API_KEY` is blank. Availability is gated once, in `AssistantRunService`, before any call reaches the wrapper: `kairon.assistant.enabled=true` **and** a non-blank key **and** the specific per-user feature flag (`assistantPreferences(...).todoSuggestionsEnabled`) — any failing check throws `ApiException.forbidden(...)` (D12) and the SDK is never touched. | Avoids `@ConditionalOnMissingBean`/profile-conditional bean wiring for a check that's simpler and more testable as a plain service-layer gate. Matches `ADR 0002`'s "dark unless configured" without adding Spring conditional-bean complexity for a single-instance, personal-scale app. |
| D5 | **Structured outputs via the SDK's class-based `StructuredMessageCreateParams<T>`** (`.outputConfig(TodoSuggestionsPayload.class)`, non-beta), not a hand-written JSON schema or the beta tool-runner. `TodoSuggestionsPayload`/`SuggestedTaskPayload` are plain records shaped to map 1:1 onto `assistant_suggested_task` columns (`title`, `notes`, `rationale`, `suggestedForDay`, `estimateMinutes`, `sourceProjectTaskId`), with `@JsonPropertyDescription` (`com.fasterxml.jackson.annotation`, the SDK's own bundled Jackson 2, not the app's Jackson 3) on each field carrying the per-field guidance that would otherwise have to live in prose in the system prompt. **Correction from the original draft** (confirmed during implementation by inspecting `anthropic-java-core-2.34.0.jar`): no `@ArraySchema` annotation exists in this SDK version, so the suggestion-count cap is **not** structural — `AssistantRunService` truncates `TodoSuggestionsPayload.suggestions()` to `kairon.assistant.todo-suggestions.max-suggestions` (default 5) after the response comes back, logging a `warn` if the model exceeded it. Still a hard cap the model cannot get around; just enforced in Java, not JSON Schema. | The Java SDK auto-derives the schema from the record and hands back a typed result — no manual schema maintenance, no beta header, no JSON parsing/validation code to write or test. Exactly the shape `DESIGN.md` §13.4 asks for ("structured output... maps directly to `assistant_suggested_task` rows"). The cap exists because this is suggest-only (ADR 0002) — a run that over-suggests should mean a longer review list, never an unbounded one; where exactly it's enforced (schema vs. application code) doesn't change that guarantee for the user. |
| D6 | **Resilience4j (`resilience4j-spring-boot3`), `@CircuitBreaker(name = "anthropic")` on `AnthropicClientImpl`'s call method, no `@TimeLimiter`.** The SDK request itself carries its own timeout (`.timeout(Duration.ofSeconds(...))` at client-build time, per `kairon.assistant.request-timeout`) — Resilience4j only tracks failure rate and opens the breaker after a configured threshold, avoiding the async/`CompletableFuture` plumbing `@TimeLimiter` needs for a call site that's a plain blocking method. *(User decision.)* | Confirmed with the user over the hand-rolled-counter alternative. Keeps the failure-tracking logic itself out of `assistant`'s own code (battle-tested library, declarative config) while not paying for the reactive wrapping a `TimeLimiter` would add for zero benefit on a synchronous call. |
| D7 | **`POST /assistant/todo-suggestions` runs synchronously in-request**: the controller call creates the `assistant_run` row `PENDING`, flips it `RUNNING`, calls the Anthropic client inline, and returns the finished run (`SUCCEEDED` or `FAILED`, with its suggestions) in the same HTTP response. `GET /assistant/runs/{id}` still exists (for re-fetching a past run's audit snapshot, and so the same endpoint shape already works once M9 adds an async kind). *(User decision.)* | Matches `DESIGN.md` §13.3 literally ("todo suggestions are fast and the create call may return the finished run directly"). Confirmed with the user over always-async-with-polling — that shape is real work M9 actually needs (background summary generation), not M8, and building the polling UI/tests now for a call that completes in a few seconds is pure ceremony. |
| D8 | **Token budget is a pre-flight check only**: before starting a run, sum `input_tokens + output_tokens` over all of the user's `assistant_run` rows with `created_at` in the current UTC calendar month; if the sum is already `>= kairon.assistant.monthly-token-budget-per-user`, refuse with `ApiException.forbidden(...)` before ever calling the SDK. No attempt to pre-estimate the *new* call's cost (the model's own token-counting endpoint could bound the input side, but output tokens are unknowable before the call completes) — the budget is a monthly ceiling that can be crossed by at most one run's cost, not a hard per-call cap. | Matches `DESIGN.md` §13.6 ("computed by summing input_tokens + output_tokens over the calendar month; a run that would exceed it is refused") — "would exceed" is read as "the account is already at or over budget," which is the only version of the check that doesn't require guessing an unmade call's output size. UTC calendar month (not the user's own timezone) is a deliberate simplification — a day's slop at the month boundary doesn't matter at this cost scale, and per-user-timezone billing months would be real complexity for no product value. |
| D9 | **Context defaults**: last 7 days of todo history ending on the request's `day` (`TodoApi.range`, includes completed/carried/cancelled — a capacity signal per §13.4) plus that day's own list (`TodoApi.forDay`); due/overdue project tasks for `day` (existing `ProjectsApi.dueOrOverdue` — reused as-is, this is the "Today" grounding §13.4 asks for, read directly rather than through `planning`, which has no `api` port to depend on, M6 D1); all open tasks in active/on-hold projects with their dates (new `ProjectsApi.openTasksInActiveProjects`); the last 5 journal entries (`JournalApi.range` over a 21-day window, trimmed to the 5 most recent in `assistant` — a dedicated "last N" port method wasn't worth adding for a slice the caller can trim itself). *(User decision: "Light" over "Wider".)* | Confirmed with the user. Keeps the prompt small and cheap at personal-instance scale; every number is a `kairon.assistant.todo-suggestions.*` config default, not a hardcoded constant, so it's adjustable without a code change once real usage shows whether it's too little or too much. |
| D9a | **Two numbers are computed from D9's raw sources, not left for the model to derive**: `openToday` (`todos.forDay(day).size()`, count of items already `OPEN`) and `avgCompletedPerDay` (mean count of `DONE`-status items per day across the 7-day history window). Both are rendered as explicit sentences in the user content (§4.5) rather than left implicit in a raw item dump. | The system prompt originally said "don't overload a day that's already full" with nothing in the context actually defining "full" — a real hallucination risk, since the model had no grounded signal to reason from. Matches `DESIGN.md` §13.4's own principle for summaries ("computed in SQL first... the model only narrates") applied one level down: hand the model a fact, don't make it infer one from a list. The *count* of suggestions to return is still left to the model's judgment (bounded by D5's hard `maxItems = 5`, not a derived formula) — a formula like `avgCompletedPerDay − openToday` would suppress real signal (e.g. three genuinely overdue unblocked tasks are worth surfacing even on a day that's nominally "full"), and the suggest-only review step (D13/ADR 0002) is the actual backstop against over-suggesting, not prompt precision. |
| D10 | **`horizon` is `DAY \| WEEK`** on `POST /assistant/todo-suggestions`. `DAY`: all suggestions' `suggestedForDay` must equal the request's `day`. `WEEK`: the model may spread suggestions across `[day, day+6]`; the prompt states that range explicitly, and the server clamps any `suggestedForDay` the model returns outside it to the nearest bound (logged at `warn` — the model ignoring an explicit instruction is a signal worth having in logs, not a hard failure of the whole run). `assistant_run.period_start`/`period_end` are set to `day`/`day` (DAY) or `day`/`day+6` (WEEK). *(User decision.)* | Confirmed with the user — implements the field now rather than stubbing it, per the roadmap's literal `{day, horizon}` request shape. Clamp-and-log (not reject-the-run) matches the project's general tolerance for imperfect LLM output over hard failures — one out-of-range suggestion shouldn't discard five good ones. |
| D11 | **A second instance of the existing `RateLimitFilter`/`RateLimitProperties` pair**, configured under `kairon.assistant.rate-limit.*` and matched against `/assistant/**`, registered alongside (not replacing) the existing auth-endpoint instance. Independent `capacity`/`refillPeriod` — a much tighter default (e.g. `capacity: 5`, `refillPeriod: 10m`) than auth's `10/1m`, since this limiter's job is bounding LLM spend per client, not just brute-force protection. | `RateLimitFilter` was already written generically (per-IP-and-path, configurable path patterns) — `DESIGN.md` §13.6 says "the existing Bucket4j setup," and reusing the class as a second bean is zero new filter code. A single shared bucket across both `/auth/**` and `/assistant/**` wouldn't fit either use case's right capacity, so two independently-configured instances of the same class is simpler than teaching one filter multiple named limit groups for a two-consumer app. |
| D12 | **New `ApiException.forbidden(String detail)` factory (403, type `forbidden`)** for every "you may not run this" case: master switch off, no key configured, user not opted into this feature, budget exceeded. **Upstream Anthropic failures map to 503** (`RateLimitException`, `InternalServerException`/5xx, `AnthropicIoException`/connection failure, an open circuit breaker — all retryable) **or 502** (a non-retryable `AnthropicServiceException`, including a `refusal` stop reason) via a new `@ExceptionHandler` in `GlobalExceptionHandler`; never a raw 500 that could leak an SDK exception message. The `assistant_run` row is still persisted `FAILED` in every case, with a short, sanitized `error` string (the mapped problem type, not the raw exception message — never a stack trace, per `CLAUDE.md`'s "never log... full request/response bodies" spirit extended to stored errors). | Matches `DESIGN.md` §13.6 ("failures as `application/problem+json`... never blocks the rest of the app") and the error-handling chain the Anthropic SDK docs recommend (retryable vs. non-retryable, most-specific-exception-first). Reuses `GlobalExceptionHandler`'s single-translation-point pattern rather than ad-hoc try/catch in the controller. |
| D13 | **Accept creates a `todo_item` via the existing `TodoApi.create`**, exactly the shape `PlanningService.promote` already established (M6 §4.3): `TodoApi.NewTodo(suggestedForDay, title, notes, priority=default, estimateMinutes, sourceProjectTaskId)`, then `assistant_suggested_task.status = ACCEPTED` + `accepted_todo_item_id` set. Dismiss is a pure status flip, no cross-module call. | `DATA_MODEL.md` already specifies this exact flow ("create a `todo_item`... then set `status = ACCEPTED`"). Reusing `TodoApi.create` rather than inventing a second todo-creation path keeps exactly one place that mints a `todo_item` from cross-module code. |
| D14 | **Settings live on the existing `AccountPage`** (a new "Assistant" section, same read-modify-write `preferences` PATCH pattern `AccountPage.tsx` already uses for `todo` preferences — spread the existing map, override the `assistant` sub-key), not a new route. "Suggest todos" is a button on both `TodayPage` and `DayView` opening a shared review list (`SuggestedTaskList` — accept/dismiss per row, optimistic removal on either action) rather than two separate implementations. | No new preferences-write endpoint needed — `PATCH /me` already does a full-object replace and the client already does read-modify-write for exactly this reason (M1). One settings page and one shared review-list component avoids duplicating the accept/dismiss UI on two screens. |
| D15 | **`anthropic-java` and `resilience4j-spring-boot3` are new version-catalog entries**, pinned explicitly (not BOM-managed — neither ships in the Spring Boot 4 BOM). Spring Boot 4 / Framework 7 compatibility for `resilience4j-spring-boot3` is **unverified** at plan time — flagged in §9 as a task-0 spike, same posture `DESIGN.md` §14 already holds for MapStruct's annotation processor and the Bucket4j starter. | Every other third-party dependency in this codebase went through the same explicit-pin treatment (`bucket4j`, `bouncycastle`, `springdoc`) because Spring Boot 4 is new enough that ecosystem readiness isn't assumed — this dependency gets the same scrutiny before real implementation starts. |
| D16 | **`kairon.assistant.enabled: false` in every values file** (`values.yaml`, `values-local.yaml`, `values-xbmc.yaml`, `values-prod.yaml`) and no `ANTHROPIC_API_KEY` in any committed secret template — an operator must both flip the flag and create the key manually (same `kubectl create secret` pattern `deploy/RUNBOOK.md` already documents for the DB/JWT secrets) for the feature to ever leave "dark." | `ADR 0002`: "dark entirely unless an operator has configured an Anthropic API key." Defaulting to off everywhere means a `helm upgrade` never silently turns on a feature that starts billing a third party. |

---

## 3. Data model

`V007__assistant.sql`, per `DATA_MODEL.md` (reproduced here for the columns that
drive service logic; see that doc for the authoritative column-by-column table):

```sql
CREATE TABLE assistant_run (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES app_user(id),
    kind              VARCHAR(30) NOT NULL
                        CHECK (kind IN ('TODO_SUGGESTION', 'WEEKLY_SUMMARY',
                                         'MONTHLY_SUMMARY', 'JOURNAL_REFLECTION')),
    status            VARCHAR(20) NOT NULL
                        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    period_start      DATE,
    period_end        DATE,
    model             VARCHAR(60) NOT NULL,
    input_snapshot    JSONB NOT NULL,
    output_markdown   TEXT,
    input_tokens      INT,
    output_tokens     INT,
    error             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_assistant_run_user_kind_created
    ON assistant_run(user_id, kind, created_at);

CREATE TABLE assistant_suggested_task (
    id                      UUID PRIMARY KEY,
    run_id                  UUID NOT NULL REFERENCES assistant_run(id) ON DELETE CASCADE,
    user_id                 UUID NOT NULL REFERENCES app_user(id),
    title                   VARCHAR(500) NOT NULL,
    notes                   TEXT,
    rationale               TEXT,
    suggested_for_day       DATE,
    estimate_minutes        INT,
    source_project_task_id  UUID REFERENCES project_task(id),
    status                  VARCHAR(20) NOT NULL
                              CHECK (status IN ('PROPOSED', 'ACCEPTED', 'DISMISSED')),
    accepted_todo_item_id   UUID REFERENCES todo_item(id),
    position                INT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_assistant_suggested_task_run ON assistant_suggested_task(run_id);
CREATE INDEX idx_assistant_suggested_task_user_status
    ON assistant_suggested_task(user_id, status);
```

No soft delete on either table (D2/`DATA_MODEL.md`) — `DELETE
/assistant/runs/{id}` hard-deletes the run and, via `ON DELETE CASCADE`, its
suggested tasks. No FK from `assistant_run`/`assistant_suggested_task` back into
`todo_item`/`project_task` triggers a cascade *into* those tables — the
reference is one-directional (an accepted suggestion points at the todo it
created; deleting that todo later does not delete the suggestion row — the FK
should be `ON DELETE SET NULL` for `accepted_todo_item_id` and
`source_project_task_id` so a later, unrelated delete elsewhere never fails on
this table).

`app_user.preferences` gains an `assistant` sub-object (no migration — it's
`jsonb`, already schema-less), per `DATA_MODEL.md`:

```json
"assistant": {
  "todoSuggestions":    { "enabled": false },
  "executionSummaries": { "enabled": false },
  "journalReflection":  { "enabled": false },
  "modelOverride": null,
  "tone": "balanced"
}
```

---

## 4. Backend

### 4.1 Port extensions on existing modules

```java
// com.kairon.todo.api.TodoApi — new method
List<TodoItemView> range(UserId userId, LocalDate from, LocalDate to);
```
`TodoService` already implements the logic (`range`, backing `GET
/todo?from=&to=`) — just add `@Override` and the interface method.

```java
// com.kairon.journal.api.JournalApi — new method
List<JournalEntryView> range(UserId userId, LocalDate from, LocalDate to);
```
Same — `JournalService.range` already exists.

```java
// com.kairon.projects.api.ProjectsApi — new method
/** Open (non-DONE) tasks in the user's ACTIVE/ON_HOLD projects, with dates. */
List<ProjectTaskView> openTasksInActiveProjects(UserId userId);
```
New repo query on `ProjectTaskRepository`, joining `Project` for the
`ACTIVE`/`ON_HOLD` filter exactly like `findDueOrOverdue` already joins it for
the user-id scope:

```java
@Query("""
        SELECT t FROM ProjectTask t JOIN Project p ON t.projectId = p.id
        WHERE p.userId = :userId AND p.deletedAt IS NULL AND t.deletedAt IS NULL
          AND p.status IN (com.kairon.projects.domain.ProjectStatus.ACTIVE,
                            com.kairon.projects.domain.ProjectStatus.ON_HOLD)
          AND t.status <> com.kairon.projects.domain.ProjectTaskStatus.DONE
        ORDER BY p.name ASC, t.plannedEnd ASC NULLS LAST
        """)
List<ProjectTask> findOpenInActiveProjects(@Param("userId") UUID userId);
```

```java
// com.kairon.identity.api.UserAccountApi — new method + new DTO
AssistantPreferencesView assistantPreferences(UserId userId);
```
```java
// com.kairon.identity.api — new record
public record AssistantPreferencesView(
        boolean todoSuggestionsEnabled,
        boolean executionSummariesEnabled,
        boolean journalReflectionEnabled,
        String modelOverride,
        String tone) {
}
```
`UserProfileService` (which already owns `AppUser.preferences`) parses the
`assistant` sub-map defensively — every field defaults (`false`/`null`/
`"balanced"`) if the user has never touched settings, so a brand-new account
with an empty `preferences` map doesn't need a migration-time backfill.

### 4.2 Package layout — new module `com.kairon.assistant`

```
com.kairon.assistant
├── domain/   AssistantRun, AssistantRunKind, AssistantRunStatus,
│             AssistantSuggestedTask, AssistantSuggestedTaskStatus
├── repo/     AssistantRunRepository, AssistantSuggestedTaskRepository
├── llm/      AnthropicClient (interface), AnthropicClientImpl,
│             TodoSuggestionsPayload, SuggestedTaskPayload (structured-output records)
├── app/      AssistantRunService, TodoSuggestionContextBuilder,
│             AssistantProperties, AssistantMapper
├── config/   AssistantConfig
└── web/      AssistantRunController, SuggestedTaskController, AssistantDtos
```

### 4.3 `assistant.llm.AnthropicClient`

```java
public interface AnthropicClient {
    TodoSuggestionsResult suggestTodos(TodoSuggestionRequest request);

    record TodoSuggestionRequest(
            String systemPrompt, String userContent, String model) {
    }

    record TodoSuggestionsResult(
            List<SuggestedTaskPayload> suggestions,
            String model, long inputTokens, long outputTokens) {
    }
}
```

The structured-output payload the SDK derives a schema from (D5) — one field's
`@JsonPropertyDescription` carries what would otherwise be prose in the system
prompt. The suggestion-count cap referenced throughout §4.5 is **not** on this
record (no `@ArraySchema` annotation exists in `anthropic-java` 2.34.0 —
confirmed during implementation); `AssistantRunService` truncates the list
after the response comes back instead (D5 correction):

```java
record TodoSuggestionsPayload(
        @JsonPropertyDescription("Proposed todo items for the requested window. "
                + "Return an empty list if nothing in the context warrants a suggestion — "
                + "never pad the list to reach a target count.")
        List<SuggestedTaskPayload> suggestions) {
}

record SuggestedTaskPayload(
        @JsonPropertyDescription("A short, concrete action completable in one sitting, "
                + "e.g. 'Order cabinet hardware', never a vague goal like 'Make progress on X'.")
        String title,
        @JsonPropertyDescription("Optional supporting detail. Omit if the title is self-explanatory.")
        String notes,
        @JsonPropertyDescription("One sentence naming the specific project, task, or journal "
                + "entry that prompted this suggestion — never a generic reason.")
        String rationale,
        @JsonPropertyDescription("Must fall within the date range stated in the user message.")
        LocalDate suggestedForDay,
        @JsonPropertyDescription("Rough estimate in minutes, only when reasonably inferable from context.")
        Integer estimateMinutes,
        @JsonPropertyDescription("Set only when this suggestion maps directly to an existing "
                + "open project task named in the context; omit otherwise.")
        UUID sourceProjectTaskId) {
}
```

```java
@Component
class AnthropicClientImpl implements AnthropicClient {

    private final com.anthropic.client.AnthropicClient sdk;

    AnthropicClientImpl(AssistantProperties properties) {
        this.sdk = AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.requestTimeout())
                .build();
    }

    @Override
    @CircuitBreaker(name = "anthropic", fallbackMethod = "suggestTodosFallback")
    public TodoSuggestionsResult suggestTodos(TodoSuggestionRequest request) {
        StructuredMessageCreateParams<TodoSuggestionsPayload> params = MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(4096L)
                .system(request.systemPrompt())
                .outputConfig(TodoSuggestionsPayload.class)
                .addUserMessage(request.userContent())
                .build();

        StructuredMessage<TodoSuggestionsPayload> response = sdk.messages().create(params);
        TodoSuggestionsPayload payload = response.content().stream()
                .flatMap(cb -> cb.text().stream())
                .findFirst()
                .map(TypedText::text)
                .orElseThrow(() -> new AssistantRunFailedException("Empty response"));

        return new TodoSuggestionsResult(payload.suggestions(), request.model(),
                response.usage().inputTokens(), response.usage().outputTokens());
    }

    // Resilience4j fallback — the open-circuit / any-exception path.
    private TodoSuggestionsResult suggestTodosFallback(TodoSuggestionRequest request, Throwable t) {
        throw new AssistantUpstreamException(t);
    }
}
```

`AssistantUpstreamException` (a new small `RuntimeException` in `assistant.app`)
carries whether the cause was retryable (429/5xx/connection/open-circuit) or not
(a non-retryable `AnthropicServiceException`, including `stop_reason ==
"refusal"` — checked before unwrapping content, per the SDK docs' stop-details
guidance) so `AssistantRunService` can persist the right `error` string and the
controller can map to 503 vs. 502 (D12).

`resilience4j.circuitbreaker.instances.anthropic.*` config: a conservative
failure-rate threshold and wait-duration-in-open-state, tuned once real traffic
exists — starting values documented in `application.yml` with a comment citing
this plan.

### 4.4 `AssistantRunService` (`@Service`)

```java
@Transactional
public AssistantRunView requestTodoSuggestions(UserId userId, LocalDate day, Horizon horizon) {
    AssistantPreferencesView prefs = requireAssistantAvailable(userId, Feature.TODO_SUGGESTIONS);
    requireWithinBudget(userId);

    LocalDate periodEnd = horizon == Horizon.WEEK ? day.plusDays(6) : day;
    AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.TODO_SUGGESTION,
            day, periodEnd, resolveModel(prefs));
    runs.save(run);

    TodoSuggestionContext context = contextBuilder.build(userId, day, horizon);
    run.start(context.asInputSnapshot());
    runs.save(run);

    try {
        AnthropicClient.TodoSuggestionsResult result = anthropicClient.suggestTodos(
                new AnthropicClient.TodoSuggestionRequest(
                        context.systemPrompt(), context.userContent(), run.getModel()));
        List<AssistantSuggestedTask> tasks = clampAndSave(run, result.suggestions(), day, periodEnd);
        run.succeed(result.inputTokens(), result.outputTokens());
        log.info("Todo-suggestion run {} succeeded userId={} suggestions={} tokens={}+{}",
                run.getId(), userId.value(), tasks.size(), result.inputTokens(), result.outputTokens());
        return mapper.toView(run, tasks);
    } catch (AssistantUpstreamException e) {
        run.fail(e.sanitizedDetail());
        log.warn("Todo-suggestion run {} failed userId={} retryable={}",
                run.getId(), userId.value(), e.retryable());
        throw e.toApiException();
    } finally {
        runs.save(run);
    }
}
```

`requireAssistantAvailable` checks, in order: `kairon.assistant.enabled`, a
non-blank `ANTHROPIC_API_KEY`, and `prefs.todoSuggestionsEnabled()` — the first
failing check throws `ApiException.forbidden(...)` (D4/D12).
`requireWithinBudget` sums the current month's tokens via a repository query
(`SUM(input_tokens + output_tokens) WHERE user_id = ? AND created_at >= ?`) and
throws `ApiException.forbidden(...)` if already at or over
`kairon.assistant.monthly-token-budget-per-user` (D8).

### 4.5 Context building (`TodoSuggestionContextBuilder`)

Pulls exactly the D9/D9a sources and splits them across a **system prompt**
(fixed policy, no per-request data) and a **user content** block (everything
that varies by request — the date window and the gathered data itself). That
split matters for prompt caching (below), not just readability.

**System prompt** (constant text, `horizon`-independent — see the caching note
below for why `horizon`'s actual date bounds are *not* in here):

```
You are Kairon's todo-planning assistant. You help the user plan concrete next
actions for their day(s) — you are not the user, and you never address them as
if you were staff or a project manager reporting up.

Base every suggestion only on the context given in the user message below
(their recent todo history, open project tasks, and recent journal notes). Do
not invent work that isn't grounded in that context.

Rules:
- Keep each item small and concrete — something completable in one sitting,
  not a vague goal. "Order cabinet hardware," not "Make progress on kitchen
  remodel."
- Never duplicate a todo already listed for the requested day.
- Never invent a deadline or urgency that isn't in the source data. If a task
  has no date, don't imply one.
- Prefer tasks that are due, overdue, blocked-then-unblocked, or stalled over
  routine or low-value ones.
- The user message tells you how many items they already have open today and
  their recent daily completion pace. Bias toward fewer, higher-value
  suggestions when today is already busy relative to that pace — but only
  suggest what genuinely clears the bar above; never pad the list to hit a
  target count, and never withhold a suggestion that clearly earns its place
  just because the day looks full.
- suggestedForDay must fall within the date range stated in the user message.
- Set sourceProjectTaskId when a suggestion maps directly to an existing open
  project task; leave it unset otherwise (e.g. something inferred only from a
  journal entry).
- If nothing in the context warrants a suggestion, return an empty list.

Example. Given: 2 items already open today, a 7-day pace of 2.3 items/day; an
overdue "Order cabinet hardware" task (Kitchen remodel); an open "Write
homepage copy" task due in three weeks (Website redesign); a journal entry
from two days ago: "need to text the tiler to confirm his week." A good
response suggests exactly two items: "Order cabinet hardware" (rationale:
overdue kitchen-remodel task, unblocked; sourceProjectTaskId set to that
task's id) and "Text the tiler to confirm his week" (rationale: mentioned in
your Sep 22 journal entry; sourceProjectTaskId unset — no matching project
task exists). It does NOT suggest "Write homepage copy": open, but not due,
overdue, or stalled, and today's already at pace — a task simply existing
isn't reason enough to surface it.
```

(Field-level format guidance — what `notes` vs. `rationale` means, the `title`
style example — lives in the `@JsonPropertyDescription` annotations on
`SuggestedTaskPayload` instead of being duplicated here; D5.)

**Why this one example, and not more.** It's placed in the system prompt
itself (not just this doc) because it's static — it carries no per-request
data, so it doesn't disturb the cacheable-prefix design below. It's built to
demonstrate the three judgment calls the rules above describe abstractly but
can't fully pin down in prose alone: (1) linking to an existing project task
via `sourceProjectTaskId` when one matches, (2) proposing a new item with no
project-task link when a suggestion is grounded only in the journal, and (3)
correctly *not* suggesting an open task that exists but hasn't earned
inclusion — directly exercising D9a's "don't pad, don't withhold" line. A
second or third example would mostly repeat these same three behaviors in a
different setting, at the cost of the anchoring/maintenance risk raised
earlier — not worth it until a real run shows this one isn't enough.

**User content** (rendered per request — exact shape TBD at implementation
time, but every one of these facts is present):

```
Suggest todos for 2026-09-24. Only suggestedForDay values between 2026-09-24
and 2026-09-24 are valid. [or "...and 2026-09-30" for horizon=WEEK]

You already have 2 open items today. Over the last 7 days you completed an
average of 2.3 items/day.

Today's open items:
- ...

Recent todo history (last 7 days):
- ...

Due or overdue project tasks:
- ...

Other open tasks in active/on-hold projects:
- ...

Recent journal entries:
- ...
```

The exact `input_snapshot` persisted on the run (D12) is this same rendered
content, not a paraphrase — a user auditing a run sees precisely what left the
instance (`DESIGN.md` §13.5).

**Caching, and why `tone` is dropped from this feature.** The system prompt
above is a constant per `horizon` value only (two variants: `DAY`, `WEEK`) —
the concrete date bounds that vary by request live in the user content
instead, so the system prompt's cacheable prefix (`cacheControl` on the system
`TextBlockParam`, still available under D5's `outputConfig` path via
`.systemOfTextBlockParams(...)`) never invalidates day-to-day. `tone`
(`assistant.tone` preference) is deliberately **not** read for todo
suggestions — `DESIGN.md` §13.4 only ties tone to M10's journal reflection,
where it shapes a narrative; a one-line factual `rationale` has little room
for tone to matter, and folding it in here would triple the number of cached
prefix variants (`horizon × tone`) for no real quality gain. Wire the
`cacheControl` call in task 4 of §8 once the plain call path is proven, rather
than optimizing before there's a working feature to measure.

### 4.6 Web layer

**`AssistantRunController`** (`@RestController @RequestMapping("/api/v1")`, same
class-level-path-for-`:action`-suffixes reasoning as `TodoController`/
`PlanningController`):

```java
@PostMapping("/assistant/todo-suggestions")
@ResponseStatus(HttpStatus.CREATED)
public TodoSuggestionRunResponse suggestTodos(@CurrentUser UserId userId,
        @Valid @RequestBody TodoSuggestionRequest req) {
    log.debug("POST /assistant/todo-suggestions userId={} day={} horizon={}",
            userId.value(), req.day(), req.horizon());
    return TodoSuggestionRunResponse.from(
            assistantRuns.requestTodoSuggestions(userId, req.day(), req.horizon()));
}

@GetMapping("/assistant/runs/{id}")
public AssistantRunResponse getRun(@CurrentUser UserId userId, @PathVariable UUID id) {
    log.debug("GET /assistant/runs/{} userId={}", id, userId.value());
    return AssistantRunResponse.from(assistantRuns.get(userId, id));
}

@DeleteMapping("/assistant/runs/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteRun(@CurrentUser UserId userId, @PathVariable UUID id) {
    log.debug("DELETE /assistant/runs/{} userId={}", id, userId.value());
    assistantRuns.delete(userId, id);
}
```

**`SuggestedTaskController`**:

```java
@PostMapping("/assistant/suggested-tasks/{id}:accept")
@ResponseStatus(HttpStatus.CREATED)
public TodoItemView accept(@CurrentUser UserId userId, @PathVariable UUID id) {
    log.debug("POST /assistant/suggested-tasks/{}:accept userId={}", id, userId.value());
    return suggestedTasks.accept(userId, id);
}

@PostMapping("/assistant/suggested-tasks/{id}:dismiss")
public SuggestedTaskResponse dismiss(@CurrentUser UserId userId, @PathVariable UUID id) {
    log.debug("POST /assistant/suggested-tasks/{}:dismiss userId={}", id, userId.value());
    return SuggestedTaskResponse.from(suggestedTasks.dismiss(userId, id));
}
```

### 4.7 ArchUnit additions

```java
@ArchTest
static final ArchRule assistantInternalsArePrivate = noClasses()
        .that().resideOutsideOfPackage("com.kairon.assistant..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.kairon.assistant.domain..", "com.kairon.assistant.repo..")
        .as("other modules may depend only on com.kairon.assistant.api (none exists yet — D1)");

@ArchTest
static final ArchRule onlyAssistantImportsTheAnthropicSdk = noClasses()
        .that().resideOutsideOfPackage("com.kairon.assistant..")
        .should().dependOnClassesThat().resideInAPackage("com.anthropic..")
        .as("assistant.llm.AnthropicClient is the only wrapper around the Anthropic SDK (ADR 0002)");
```

### 4.8 Authorization & errors

Every `assistant` endpoint 404s on a missing/foreign `run`/`suggested-task` id
(same discipline as every other module — `AssistantRunRepository`/
`AssistantSuggestedTaskRepository` queries are always scoped
`WHERE id = ? AND user_id = ?`). Disabled/opted-out/budget-exceeded → 403
(D12). Upstream Anthropic failures → 503 (retryable) or 502 (non-retryable,
including a safety refusal) — never a raw 500 (D12).

---

## 5. API contract

Base path `/api/v1`. Bearer access token on every call.

| Method & path | Body | Response |
| --- | --- | --- |
| `POST /assistant/todo-suggestions` | `{ day, horizon: "DAY"\|"WEEK" }` | `201` + `TodoSuggestionRunResponse` (run + its suggestions); `403` disabled/opted-out/budget; `502`/`503` upstream failure. |
| `GET /assistant/runs/{id}` | — | `AssistantRunResponse`; `404` missing/foreign. |
| `DELETE /assistant/runs/{id}` | — | `204`; `404` missing/foreign. |
| `POST /assistant/suggested-tasks/{id}:accept` | — | `201` + `TodoItemView` (D13); `404` missing/foreign; `409` if already accepted/dismissed. |
| `POST /assistant/suggested-tasks/{id}:dismiss` | — | `200` + `SuggestedTaskResponse`; `404`; `409` if already resolved. |

### `POST /assistant/todo-suggestions` response shape

```json
{
  "id": "018f…", "status": "SUCCEEDED", "kind": "TODO_SUGGESTION",
  "model": "claude-sonnet-5", "periodStart": "2026-09-21", "periodEnd": "2026-09-21",
  "inputTokens": 1840, "outputTokens": 310, "createdAt": "2026-09-21T…",
  "suggestions": [
    {
      "id": "018f…", "title": "Order cabinet hardware", "notes": null,
      "rationale": "Kitchen remodel task is overdue and unblocked",
      "suggestedForDay": "2026-09-21", "estimateMinutes": 20,
      "sourceProjectTaskId": "018f…", "status": "PROPOSED", "position": 0
    }
  ]
}
```

---

## 6. Web

### 6.1 Feature folder — `web/src/features/assistant/`

```
features/assistant/
├── SuggestTodosButton.tsx    opens a dialog: horizon (day/week) + day (defaults
│                               today), calls useSuggestTodos; used on TodayPage
│                               and DayView
├── SuggestedTaskList.tsx      review list: title/rationale/estimate per row,
│                               accept/dismiss buttons, optimistic removal
├── AssistantSettings.tsx      section embedded in AccountPage: three feature
│                               toggles + data-sharing notice + model override +
│                               tone select
├── useAssistant.ts            useSuggestTodos, useAcceptSuggestedTask,
│                               useDismissSuggestedTask mutations
└── assistantKeys.ts           { run: (id) => ["assistant", "run", id] }
```

### 6.2 Data layer

- `web/src/lib/api/assistant.ts` — `assistantApi.suggestTodos(body)`,
  `assistantApi.getRun(id)`, `assistantApi.acceptSuggestedTask(id)`,
  `assistantApi.dismissSuggestedTask(id)`.
- Types added to `web/src/lib/api/types.ts` mirroring the response shape in §5.
- `useSuggestTodos()` → `useMutation`, no query-cache entry to invalidate (the
  result is shown directly in `SuggestedTaskList`, not re-fetched); accept
  invalidates `todoKeys.day(suggestedForDay)` so the new item shows up on
  whichever list is open (same pattern as `usePromoteTask`, M6 §6.3).

### 6.3 `AccountPage.tsx` changes

New "Assistant" section using the exact read-modify-write pattern the existing
`todo` preferences section already demonstrates — spread `meQuery.data
?.preferences`, override the `assistant` key, `PATCH /me`. Each toggle shows a
one-line data-sharing notice ("Kairon sends your open project tasks, recent
todo history, and recent journal entries to Anthropic when you request
suggestions") before it can be turned on, per `DESIGN.md` §13.5.

---

## 7. Testing

### Backend

| Test | Type | Covers |
| --- | --- | --- |
| `AssistantRunServiceTest` | plain JUnit 5 + AssertJ, Mockito for `AnthropicClient`/`TodoApi`/`JournalApi`/`ProjectsApi`/`UserAccountApi` | disabled/opted-out/budget-exceeded all throw before the client is called; a successful call persists `SUCCEEDED` + suggested tasks with correct `position`; an `AssistantUpstreamException` persists `FAILED` with a sanitized error and rethrows the right `ApiException`; `WEEK` horizon clamps an out-of-range `suggestedForDay`. |
| `TodoSuggestionContextBuilderTest` | plain JUnit 5 + AssertJ, Mockito for the three ports | assembles the expected system/user content and `input_snapshot` from mocked port responses; respects the D9 windows (7 days todo history, 5 most-recent journal entries out of a wider fetched range). |
| `AnthropicClientImplTest` | plain JUnit 5 + a stubbed SDK client (or WireMock against the SDK's HTTP layer) | structured-output payload maps to `TodoSuggestionsResult`; a simulated 429/5xx trips the circuit breaker fallback; a `refusal` stop reason surfaces as non-retryable. |
| `AssistantRunControllerTest` / `SuggestedTaskControllerTest` | `@WebMvcTest` + `@MockitoBean AssistantRunService`/`SuggestedTaskService` | happy paths for all five endpoints; 403/404/409 passthrough; no token → 401. |
| `AssistantFlowIntegrationTest` | `@SpringBootTest` + Testcontainers, `AnthropicClient` faked via `@MockitoBean` (never a real network call in CI) | full flow: enable the feature flag via `PATCH /me`, seed a project task + journal entry, `POST /assistant/todo-suggestions` → suggestions persisted and returned; `:accept` creates a linked `todo_item` visible via `GET /todo?day=`; `:dismiss` flips status without creating one; a second run past the configured budget is refused with `403`. |
| `ArchitectureTest` (extend) | ArchUnit | `assistantInternalsArePrivate`, `onlyAssistantImportsTheAnthropicSdk` (§4.7). |

### Web

| Test | Covers |
| --- | --- |
| MSW handlers | add all five `/api/v1/assistant/**` routes, sharing the existing todo/journal/project-task in-memory stores for the accept flow's follow-up `GET /todo?day=`. |
| `SuggestedTaskList.test.tsx` | renders suggestions; accept calls the mutation and removes the row; dismiss likewise; a mocked 403 renders an inline "not enabled" message rather than throwing. |
| `AssistantSettings.test.tsx` | toggling a switch PATCHes `/me` with the other preference keys untouched (read-modify-write regression guard). |
| `assistant-todo-suggestions.spec.ts` (Playwright, new) | enable the feature in Settings → open Today → "Suggest todos" → accept one suggestion → it appears in the day's todo list. Needs a test double for the Anthropic call (the e2e backend runs with `kairon.assistant.enabled=true` but a fake/stub `AnthropicClient` profile bean — see §9 Q3). |

---

## 8. Task breakdown (suggested order)

1. **Spike (blocks everything else)**: confirm `resilience4j-spring-boot3`
   against Spring Boot 4.1.1 / Framework 7 (D15) — a throwaway
   `@CircuitBreaker`-annotated bean in a scratch branch is enough to prove
   annotation processing and auto-config both work before committing to it in
   the real module.
2. `V007__assistant.sql`; domain entities (`AssistantRun`, `AssistantSuggestedTask`
   + enums); repositories.
3. Port extensions: `TodoApi.range`, `JournalApi.range`,
   `ProjectsApi.openTasksInActiveProjects` (+ repo query), `UserAccountApi
   .assistantPreferences` (+ `AssistantPreferencesView`, preferences parsing in
   `UserProfileService`). Unit tests for each.
4. `assistant.llm.AnthropicClient` + `AnthropicClientImpl` + structured-output
   records; `AnthropicClientImplTest`.
5. `TodoSuggestionContextBuilder` + test.
6. `AssistantRunService` (budget check, availability check, run lifecycle) +
   `AssistantMapper` + test.
7. `AssistantRunController`/`SuggestedTaskController`/`AssistantDtos` +
   controller tests.
8. `GlobalExceptionHandler` additions for 502/503 mapping; `ApiException
   .forbidden`.
9. `AssistantFlowIntegrationTest`.
10. Config: `AssistantProperties`, `application.yml` defaults, second
    `RateLimitFilter` bean (D11), Micrometer token counter.
11. Helm: `ANTHROPIC_API_KEY` in `secret.yaml`/`values-*.yaml` (off/absent by
    default per D16), `kairon.assistant.*` in `configmap.yaml`;
    `deploy/RUNBOOK.md` gains the key-rotation/creation steps.
12. Web: `assistant.ts` + `types.ts`, `assistantKeys.ts`, `useAssistant.ts`, MSW
    handlers.
13. Web: `SuggestedTaskList.tsx`, `SuggestTodosButton.tsx`,
    `AssistantSettings.tsx` in `AccountPage.tsx`.
14. Web: wire `SuggestTodosButton` into `TodayPage`/`DayView`.
15. Web tests + Playwright happy path (needs the e2e stub decision — §9 Q3).
16. Docs: tick M8's `ROADMAP.md` boxes; `CLAUDE.md` "Current state" — new
    `assistant` module bullet, extend `todo`/`journal`/`projects`/`identity`
    bullets with the new port methods, extend the web bullet with
    `features/assistant/`; mark this plan `Accepted`.

---

## 9. Open questions / risks

All five resolved during implementation — kept here for the record rather than deleted.

| # | Question | Resolution |
| --- | --- | --- |
| Q1 | D15's Resilience4j/Spring-Boot-4 compatibility is unverified — what if the spike (task 1) fails? | **Resolved: it works.** The spike (adding both dependencies and running the full existing test suite, including every `@SpringBootTest` context load) passed clean against Spring Boot 4.1.1 — no hand-rolled fallback needed. |
| Q2 | D9's context sizes (7 days / 5 entries) are a guess, not measured against real prompt-token costs. | Shipped as-is (`kairon.assistant.todo-suggestions.*` config defaults). Still genuinely open — needs real usage to tune, not a pre-launch guess. |
| Q3 | Playwright e2e (`task e2e`) can't hit the real Anthropic API in CI. How does the stub get wired in? | **Resolved.** `assistant.llm.FakeAnthropicClient`, `@Profile("local")` + `@ConditionalOnProperty(kairon.assistant.fake-client=true)`, mutually exclusive with `AnthropicClientImpl` via the inverse condition. Wired on by default in `application-local.yml`. The new `assistant-todo-suggestions.spec.ts` passes end-to-end (real backend, real browser, zero network calls to Anthropic). |
| Q4 | Should `openTasksInActiveProjects` (D3) cap the number of tasks returned for a user with many active projects, to bound prompt size? | Shipped uncapped, per the original recommendation. Still open pending real usage. |
| Q5 | D6's Resilience4j failure-rate threshold and open-state wait duration are placeholder values pending real traffic. | Shipped as documented placeholders (`application.yml`, `resilience4j.circuitbreaker.instances.anthropic.*`). Still open pending production call volume. |

One thing the plan got wrong, caught during implementation (not a Q, a correction — see D5): no `@ArraySchema` annotation exists in `anthropic-java` 2.34.0, so the suggestion-count cap is enforced in `AssistantRunService` after the response comes back, not structurally in the JSON schema.

---

## 10. Doc updates this milestone produces

All done.

- `ROADMAP.md` — M8 checkboxes ticked.
- `DESIGN.md` §3.1/§13.2/§13.4 — corrected a stale claim that `assistant`
  reads `planning` through an `api` package (`planning` has none, per M6 D1 —
  `assistant` reads `todo`/`journal`/`projects` directly instead, D9); noted
  the port extensions (§4.1) as the concrete shape of "reads other modules
  only through their public `api` packages."
- `DATA_MODEL.md` — no change; V007 matches what was already specified there.
- `CLAUDE.md` "Current state" — `assistant` module bullet added in full
  (domain/repo/llm/app/config/web contents); `todo`/`journal`/`projects`/
  `identity` bullets extended with their new port methods; web bullet extended
  with `features/assistant/`; migration list extended with `V007`; "Next
  milestone" now points at M9.
- `deploy/RUNBOOK.md` — "Enable or rotate the Anthropic API key" section added,
  alongside the existing DB/JWT secret sections.
- This file — `Status: Draft` → `Accepted — implemented`; §9's five open
  questions resolved (four shipped as originally recommended, one — Resilience4j
  compatibility — confirmed working); the `@ArraySchema` correction recorded in
  D5.
