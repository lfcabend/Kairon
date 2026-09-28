# M9 — Weekly & monthly execution summaries — implementation plan

Status: **Accepted — implemented.** Companion to [`../ROADMAP.md`](../ROADMAP.md) (M9 acceptance
criteria), [`../DESIGN.md`](../DESIGN.md) §13 (§13.3's "summaries run as
background jobs, `@Async`, plus the `@Scheduled` auto-trigger... and are
always polled" is the load-bearing sentence this whole plan implements),
[`../DATA_MODEL.md`](../DATA_MODEL.md), and
[`../adr/0002-ai-assistant-anthropic.md`](../adr/0002-ai-assistant-anthropic.md).
Builds on the `assistant` module M8 established (`AssistantRun` lifecycle,
`AnthropicClient`, budget/rate-limit/circuit-breaker plumbing, the
`executionSummariesEnabled` preference M8 already reserved) and M8.5's
context-builder/service split — reuses both unchanged except where noted.

Unlike M8 and M8.5, this milestone needs **no new migration and no new
table**. M8's `V007__assistant.sql` already widened `assistant_run.kind` to
include `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` (M8 D2, written specifically so
this milestone wouldn't need to), `output_markdown`/`period_start`/
`period_end` already exist as columns nothing has written to yet, and
`app_user.preferences.assistant.executionSummaries.enabled` already exists
as a preference key nothing has read yet. This is the first assistant
milestone that is pure application code against schema already in place.

It is, however, the first genuinely **asynchronous** feature in this
codebase: the first `@Async` method, the first `@Scheduled` job with real
work in it (`@EnableScheduling` has sat on `KaironApplication` unused since
M0), and the first frontend polling loop.

---

## 1. Scope

### In

- SQL-aggregated period statistics, computed via new narrow port methods on
  `todo`/`projects` (not raw row dumps counted in `assistant`): todos
  created/completed/rolled over + completion rate (`TodoApi.periodStats`),
  per-project completed-task counts + estimate-vs-actual hours
  (`ProjectsApi.projectPeriodStats`), a per-project overdue-risk grouping,
  and a per-category "share of attention" breakdown (D15) so the narrative
  can call out which categories are getting worked on versus neglected —
  see D4/D5 for exactly what "task-status deltas" can and can't mean
  without an event-history table.
- `WEEKLY_SUMMARY` / `MONTHLY_SUMMARY` runs: the model narrates the
  precomputed numbers and proposes efficiency suggestions; output is
  markdown, not structured JSON (D6) — the first non-structured-output
  Anthropic call in this module. `output_markdown` also carries a
  deterministic markdown stats table, rendered by our own code and
  appended after the call returns — never authored by the model, and
  explicitly **not** via Anthropic tool-use, which was considered and
  rejected (D16 — the report's data needs are fully fixed upfront, the one
  case tool-use doesn't help with).
- `POST /assistant/summaries` (`{period: WEEK|MONTH, date}`) — creates a
  `PENDING` run and returns immediately; the actual call runs `@Async` and
  the client polls `GET /assistant/runs/{id}` (D2), same endpoint M8 already
  built for exactly this "poll a past run" purpose.
- A `@Scheduled` job, two triggers (weekly Monday AM, monthly on the 1st),
  that runs the same generation path for every user opted into
  `executionSummaries`, skipping (not failing the whole sweep on) any user
  who's disabled, opted out, over budget, or already has a run in flight
  (D8–D10).
- `GET /assistant/runs?kind=&from=&to=&page=&pageSize=` — paginated run
  history, any kind (not just summaries), for the new summaries screen and
  useful generally.
- Web: a summaries screen — a history list, a "Generate weekly/monthly
  summary" action, and a markdown-rendered detail view that polls while a
  run is `PENDING`/`RUNNING`.
- Tests: unit tests for the new aggregate queries and the async dispatch
  split, a `@SpringBootTest` flow test covering both on-demand and the
  scheduled sweep, Vitest for the polling hook and screen, a Playwright
  happy path.

### Out (deferred, with what would pick it up)

- **True task-status-change history** (e.g. "3 tasks moved to In Progress, 2
  moved to Done" as an actual transition log, not an `updated_at` proxy) —
  would need a new append-only event/audit table nothing in this codebase
  has today. D5 documents exactly what the `updated_at`-based approximation
  gets right and wrong; a real fix is a standalone future milestone, not a
  tweak to this one.
- **Journal reflection** — M10, unchanged from the roadmap's own ordering.
  Summaries read only `todo`/`projects`; no journal grounding (matches
  `DESIGN.md` §13.4, which ties journal content to reflection, not
  summaries).
- **Retrying or resuming a `FAILED`/stuck `RUNNING` run automatically** — the
  user (for on-demand) or the next scheduled fire (for auto-generated
  summaries) is the retry path, same as every other assistant feature. No
  job-recovery infrastructure exists anywhere in this codebase yet (see Q3).
- **Per-project or per-category summary scoping** — a summary always covers
  the whole account for the period, matching the roadmap's flat `{period,
  date}` request shape. Scoping to one project is a plausible follow-up once
  usage shows the account-wide view is too coarse for someone running many
  concurrent projects.
- **Bring-your-own scheduling** (e.g. a user picking their own auto-generate
  day/time) — the cron trigger is one instance-wide, config-driven schedule
  for every opted-in user (D11), not a per-user setting.

---

## 2. Decisions locked for this milestone

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **No migration.** `assistant_run.kind` already permits `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` (`V007`, M8 D2); `output_markdown`, `period_start`, `period_end` are existing unused columns; `executionSummariesEnabled` is an existing unread preference field (M8 D14's `AssistantPreferencesView`). | M8 deliberately over-specified its schema for exactly this reason (M8 D2's rationale: "avoids a near-certain M9 migration"). Paid off. |
| D2 | **Summary runs are asynchronous and always polled**, unlike M8/M8.5's synchronous in-request calls (M8 D7). `POST /assistant/summaries` creates the `PENDING` row, commits it, and only then hands the run id to a `@Async` method on a **separate** bean (`SummaryGenerationService`) — never a same-class self-invocation, which Spring's proxy-based `@Async`/`@Transactional` would silently no-op. `KaironApplication` gains `@EnableAsync` alongside its existing (previously unused) `@EnableScheduling`. No custom `TaskExecutor` is configured — Spring Boot auto-configures `applicationTaskExecutor` as a virtual-thread `SimpleAsyncTaskExecutor` because `spring.threads.virtual.enabled=true` is already set instance-wide, and `@Async` picks that bean up with no executor name given. | `DESIGN.md` §13.3 states this explicitly ("summaries and reflection run as background jobs... and are always polled") — not a new decision, an existing spec this milestone finally implements. The two-method split mirrors the same "a proxy can't intercept a self-call" lesson M8.5's own D3 correction already recorded for `@Lazy`/constructor cycles — same underlying Spring AOP limitation, different symptom. Reusing the already-enabled virtual-thread executor instead of hand-rolling a `ThreadPoolTaskExecutor` is the simplest viable option this stack already paid for. |
| D3 | **At most one `PENDING`/`RUNNING` run across `WEEKLY_SUMMARY`+`MONTHLY_SUMMARY` per user at a time** — `POST /assistant/summaries` (and the scheduled sweep, D10) checks `AssistantRunRepository.existsByUserIdAndKindInAndStatusIn(...)` first and throws `409` (on-demand) or skips-and-logs (scheduled) if violated. | Summary generation is no longer request-scoped (D2), so nothing else stops a user double-clicking "Generate" or an on-demand call racing the scheduled trigger into two concurrent, separately-billed calls for the same account. A single in-flight-run guard is the cheapest fix that doesn't need a distributed lock — this is a single-instance app. |
| D4 | **New narrow SQL-aggregate port methods, one per module, each backed by exactly one `GROUP BY`/`SUM` query** — not a raw-row fetch counted in Java (the M8 D9a precedent for todo-suggestion context, which was fine for a 7-day window of a few items, doesn't scale to a month of every todo and every project task). `TodoApi.periodStats(UserId, from, to)` → `TodoApi.PeriodStats(long created, long completed, long rolledOver)` — one query, `CASE WHEN` sums over `TodoItem`. `ProjectsApi.projectPeriodStats(UserId, from, to)` → `List<ProjectsApi.ProjectPeriodStats(projectId, projectName, tasksCompleted, estimateHoursCompleted, actualHoursCompleted)>` — one query, grouped by project, scoped to tasks completed in the window (D5). "Currently still open" context for the narrative reuses the existing `ProjectsApi.openTasksInActiveProjects` unchanged — no new method needed for that half. | Matches `DESIGN.md` §13.4 literally: "computed in SQL first... the model only narrates; it does not count." At month-scale row counts, doing the counting in the database is both the more literal reading of that sentence and materially cheaper than pulling every row across the wire to sum in Java. |
| D5 | **"Completed" for a project task is approximated as `status = DONE AND updated_at ∈ [from, to)`** — there is no `completed_at` column on `project_task` (only `todo_item` has one) and no task-history/event table anywhere in this schema. This is a known, accepted approximation: it undercounts a task truly completed in-period but edited again afterward (rare), and overcounts one completed earlier but merely touched again in-period (e.g. a note added to an already-done task) — see the "Out" section above for the real fix. Estimate-vs-actual hours are summed only over tasks counted as "completed" by this same definition, so both numbers use one consistent (if imperfect) boundary. Todos don't have this problem — `todo_item.completed_at` is a real, dedicated timestamp set exactly once by `TodoItem.complete()`. | Same tolerant-degradation posture the project has used throughout the assistant module (M8.5 D7/D11) — an approximate signal computed transparently beats blocking the whole feature on a new audit-log table, and the `input_snapshot` (D2's existing audit mechanism, unchanged) lets a user see precisely which numbers were computed which way. |
| D6 | **Output is plain markdown from a normal (non-structured) `sdk.messages().create(...)` call** — `AnthropicClient.generateSummary(SummaryRequest) -> SummaryResult(String markdown, model, inputTokens, outputTokens)`, no `outputConfig`/`StructuredMessageCreateParams`. `AssistantRun` gains a `succeed(String outputMarkdown, long inputTokens, long outputTokens)` overload (delegates to the existing `succeed(long, long)` after setting the field) rather than changing the existing signature — M8/M8.5's two call sites are untouched. `AssistantRunView` gains an `outputMarkdown` field, populated only for `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` runs — the same "one view, only the field matching the run's own kind is populated" shape M8.5 already established for `suggestedProject`. | `DESIGN.md` §13.4: summary "output is markdown" — a narrative with headers/lists genuinely doesn't fit the `TodoSuggestionsPayload`/`ProjectPlanPayload` structured-list shape those two features use; forcing it into a JSON schema would just mean parsing markdown back out of a JSON string field for no benefit. |
| D7 | **A new cross-user query, scoped narrowly to this one scheduler's own use** — `UserAccountApi.usersOptedIntoExecutionSummaries()` (native query on `app_user.preferences`: `(preferences->'assistant'->'executionSummaries'->>'enabled')::boolean = true`). This is *not* the general admin cross-user pattern M15 will formalize — it's an unauthenticated, no-`@CurrentUser` internal call the `@Scheduled` job makes to itself, never exposed on any controller. | Every other query in this codebase is scoped `WHERE ... = @CurrentUser`; a background job has no request and no current user, so it genuinely needs to ask "which users, plural" — a real, narrow exception to the rule, called out explicitly so a future reader doesn't mistake it for precedent to route a user-facing endpoint the same way before M15 does that properly. |
| D8 | **Both cron triggers resolve their period from `date = LocalDate.now(clock).minusDays(1)`** ("yesterday") — `clock` is the app's existing shared `Clock.systemUTC()` bean (`CommonConfig`), the same one M8 D8 already uses for the budget window, so this reads as UTC calendar days. One rule that's correct for both: fired Monday morning, yesterday is Sunday, and `WEEK.resolve(Sunday)` (Monday–Sunday ISO week containing it) lands on the week that just ended; fired the 1st, yesterday is the last day of the prior month, and `MONTH.resolve(...)` lands on the month that just ended. This only stays correct as long as the cron trigger's own `zone` is also UTC (D9). | Avoids two separate, easier-to-get-wrong date-arithmetic rules (`minusWeeks(1)` vs. "the previous calendar month," which isn't simply `minusMonths(1)` from an arbitrary day) for what's really one idea: "summarize the period that just finished." |
| D9 | **Cron schedule and timezone are instance-level config, not per-user** — `kairon.assistant.summary.weekly-cron` (default `0 0 7 * * MON`), `monthly-cron` (default `0 0 7 1 * *`), `zone` (default **`UTC`**, overridable). `zone` **must stay UTC unless `SummaryScheduler` is also given a zoned `Clock`** — the app's only `Clock` bean (`CommonConfig`, `Clock.systemUTC()`) is what D8's "yesterday" is computed from, so a `zone` set to anything else would fire the cron trigger in local time while still resolving the period in UTC calendar days. *(Correction found while reviewing this plan — the original draft defaulted `zone` to "the JVM/instance default" without checking this.)* | Same simplification precedent as M8 D8's UTC-calendar-month budget window — this is a personal/family-scale single instance, not a service with users spread across timezones who each need their own 7am. A config value (not a hardcoded cron string) still leaves it adjustable without a code change, matching every other `kairon.assistant.*` default. Pinning the default to UTC (matching the one `Clock` bean this app has) removes the mismatch by construction rather than documenting it as a footgun and hoping an operator reads the warning — a non-UTC `zone` can be revisited if per-zone scheduling ever becomes a real request, at which point `SummaryScheduler` would need its own zoned `Clock`, not the shared UTC one. |
| D10 | **The scheduled sweep catches per-user failures and continues** — budget-exceeded, opted-out-since-last-run, an in-flight run already present (D3), or an `AssistantUpstreamException` from one user's call are all caught, logged (`info` for the expected skip cases, `warn` for the upstream failure), and the loop moves to the next user. Nothing about one user's failure marks any other user's run, and nothing about the sweep itself is transactional across users — each user's `requestSummary` call is its own unit of work. | An unattended nightly-ish job must not let one bad account (over budget, a transient Anthropic 503) silently skip every other opted-in user behind it in iteration order — the same "one bad edge shouldn't block 39 good tasks" tolerance M8.5 D7 already established, applied to a batch of users instead of a batch of dependency edges. |
| D11 | **Everything else reuses M8 unchanged**: the same `AnthropicClient` bean/circuit breaker, the same pre-flight monthly token budget (D8 in M8 — this feature draws from the same pool), the existing `executionSummariesEnabled` preference (no new opt-in flag needed, unlike M8.5's new one — M8 already reserved this key), the same `/assistant/**`-adjacent `RateLimitFilter` (add `/api/v1/assistant/summaries` to its literal `paths` list — M8.5's own §11 correction already flagged that this list is literal, not a wildcard), and the same 403/502/503 error-mapping chain. | No reason to re-derive infrastructure that's already generic. The only genuinely new decisions this milestone makes are D1–D10 and D12–D18. |
| D12 | **`GET /assistant/runs` is a new paginated list method**, ordered `createdAt` **descending** (most recent first — a history screen's natural default), `AssistantRunRepository` gaining a derived query filtered by optional `kind` (repeatable — `IN`) and a `createdAt` date range, returned as a new `AssistantRunPage(List<AssistantRunView> content, int page, long totalElements)` — the same shape `ProjectPage` already established, so the web layer doesn't need to learn a second paging convention. Rows in the list omit `suggestions`/`suggestedProject`/`outputMarkdown` bodies (a lighter `AssistantRunSummaryView`, or the same `AssistantRunView` with those fields left empty/null — decided at implementation time by whichever is less code) — the detail view still calls the existing `GET /assistant/runs/{id}` for the full body. | Every other list endpoint in this app (`GET /projects`, `GET /projects/{id}/tasks`) already follows exactly this "list is light, detail is full" split; no reason for `assistant` to invent a different one. |
| D13 | **`SummaryContextBuilder` follows the existing split** (system prompt: stable house rules, cacheable, identical for `WEEK` and `MONTH` — the two kinds differ only in the user content's period bounds and wording, not in any rule; user content: the period bounds + every computed number, including per-project estimate-vs-actual **variance precomputed in Java**, plus overdue tasks **pre-grouped by project** — capped by `kairon.assistant.summary.max-highlight-items`, default 8, on both lists). Full content spec in §4.5. | Matches `DESIGN.md` §13.4's split precisely: computed numbers (including the variance arithmetic and the overdue grouping) first, model narrates — never asked to count, group, or subtract. Grouping overdue tasks by project (rather than handing the model a flat task list to summarize itself) makes "which projects are at risk" — the thing worth flagging in a narrative — an explicit, reliable fact instead of something the model has to infer from raw rows. One system-prompt variant for both kinds (vs. M8's two `horizon` variants) since nothing here structurally differs between a week and a month. |
| D14 | **`tone` is *not* read for M9's summaries**, despite `DESIGN.md` §13.4 line "tone is configurable" appearing in the same paragraph as summaries. *(User decision, confirmed while drafting this plan.)* `AssistantSettings.tsx` has no tone picker today — only a model-override select — so `assistant.tone` is a stored default (`"balanced"`) nothing lets the user actually change yet; reading it here would be a no-op that looks configurable but isn't. `DESIGN.md` §13.4's own text ties tone explicitly to M10 journal reflection ("Tone is configurable... " appears in that bullet, not the summary one) — M9's system prompt is tone-neutral, matching that literal reading. | Confirmed with the user over the alternative (read `tone` now and add the picker as part of M9's own scope) — reading a preference with no UI to set it is a half-wired feature; M10 is where tone gets a real picker and a feature that actually needs range in tone (a reflective response vs. a factual summary has much more room for the "encouraging" vs. "blunt" distinction to matter). Revisit if M10's picker ships first and a "should summaries pick it up too" question comes back naturally at that point. |
| D15 | **`ProjectsApi.ProjectPeriodStats` gains `categoryId`/`categoryName`** (nullable — uncategorized, or a deleted category via the existing `ON DELETE SET NULL` FK), denormalized via a `LEFT JOIN project_category` in the same query (§4.1) — no second port method. `SummaryContextBuilder` groups the **full, uncapped** result of `projectPeriodStats` (not the top-N list §4.5 item 2 already caps for display) by `categoryId` (a null `categoryId` groups into a synthetic "Uncategorized" bucket) to produce a **category attention breakdown**: each category's share of the period's total completed project tasks, as a percentage, sorted descending and capped at `max-highlight-items` categories. *(User-requested addition.)* | The user asked for a way to see which categories are getting more attention than others, i.e. a comparison, not just a per-category count in isolation — a percentage share needs the true total across every project with completed work, so it has to be computed from the uncapped list even though the per-project display (item 2) is capped; computing the share from the already-truncated top-N list would silently understate the total and skew every percentage once an account has more than `max-highlight-items` active projects. Task-completion count (not hours) is the ranking signal because `actualHours` is optional and frequently unset — a count is always available and avoids a share metric that's really "which categories happen to log time," a different (and noisier) question than "which categories got worked on." |
| D16 | **`output_markdown` is the model's narrative plus a deterministic stats table, concatenated after the call returns — never a table the model is asked to author.** `SummaryContextBuilder.Context` gains `renderStatsTable()`, a pure function over the same structured values (`TodoApi.PeriodStats`, the per-project list, the overdue-by-project grouping, the category breakdown) already rendered into `userContent` — so the table and the narrative are guaranteed to describe the exact same snapshot, no second query, no risk of the two disagreeing. **Tool-use (Anthropic function-calling) was considered and rejected** for computing these numbers — *(user decision, confirmed while drafting this plan)*. | A model-authored table risks silent transcription errors even though it's given the right numbers (an LLM reformatting digits into table cells is exactly the kind of low-stakes-looking task that occasionally drops or duplicates a row); rendering it in Java is strictly more reliable and costs no output tokens. Tool-use was rejected because this feature's data need is **fully fixed and known upfront** every single time (the same handful of aggregate queries, always) — nothing here benefits from letting the model decide at runtime what to fetch, the one thing tool-use is actually for. Adopting it would add the first multi-turn tool-calling loop in this codebase, more latency and token cost per report, new failure modes (malformed/looping tool calls), and would cut directly against `DESIGN.md` §13.4's existing principle for this whole module — "computed in SQL first... the model only narrates; it does not count." Revisit only if a genuinely open-ended, conversational assistant feature (not a periodic fixed-format report) ever gets scoped — that's the shape tool-use is actually built for. |
| D17 | **New frontend dependency: `react-markdown` + `remark-gfm`**, used only by `SummaryDetail.tsx` to render `outputMarkdown` (narrative + D16's table) read-only. Explicitly pinned like every other third-party addition in this codebase (M8 D15's precedent). `tiptap-markdown` (existing) is **not** reused — it serializes *to/from* Tiptap's editable document model for the journal's WYSIWYG editor (`CLAUDE.md`: "no raw markdown mode"); this is the opposite need, read-only rendering of an already-final markdown string including GFM tables Tiptap's own default extension set doesn't include. | This is the first screen in the app that renders LLM-authored markdown as read-only content — a small, standard, purpose-fit library is a better match than stretching an editing-oriented dependency to a rendering-only job it wasn't built for. `remark-gfm` specifically is required for pipe-table syntax — plain CommonMark (`react-markdown`'s default) doesn't parse tables, and D16's whole point is a table that actually renders. `react-markdown` never renders raw HTML by default (no `rehype-raw` plugin is added here), so a model response containing an HTML/script snippet renders as inert text, not markup — the standard XSS-safe default, not something this plan has to build. `outputMarkdown` is also always this account's own AI-narrated content over its own data, never another user's — there's no cross-user content path into it to defend against beyond that default. |
| D18 | **`AppLayout.tsx` gets a new top-level "Summaries" nav entry** (route `/summaries`, placed after "Projects"), a deliberate exception to M8/M8.5's established "no new nav entry — embed a button in an existing screen" pattern. *(User decision, confirmed while drafting this plan.)* | Todo-suggestions and project-generation are actions layered onto an existing screen's context (`SuggestTodosButton` on Today/Day view; "New project from description" on the project list) — each has an obvious host screen. Summaries don't: they're a standalone, browsable history the user comes back to read, with no existing screen whose context they'd naturally attach to — structurally closer to Journal or Projects (each its own page) than to a dialog. Confirmed with the user over tucking it into the Account page (which would keep the nav bar unchanged but bury a report history inside a settings screen). |

---

## 3. Data model

**No migration.** For reference, the columns this milestone finally writes to
(all already exist, `V007__assistant.sql`, M8):

| Column | Written as |
| --- | --- |
| `assistant_run.kind` | `WEEKLY_SUMMARY` \| `MONTHLY_SUMMARY` |
| `assistant_run.period_start` / `period_end` | the resolved week (Mon–Sun) or calendar month |
| `assistant_run.output_markdown` | the model's narrative |
| `assistant_run.input_snapshot` | the rendered system+user content — same audit contract as M8/M8.5 |
| `app_user.preferences.assistant.executionSummaries.enabled` | read, not written, by `UserAccountApi.usersOptedIntoExecutionSummaries()` (D7) and the existing `assistantPreferences()` |

---

## 4. Backend

### 4.1 New port methods

```java
// com.kairon.todo.api.TodoApi — new method + DTO
/**
 * Created/completed/rolled-over counts for the user's items with {@code day}
 * in {@code [from, to]}. Added for M9's execution-summary aggregation
 * (docs/milestones/M9-execution-summaries.md D4).
 */
PeriodStats periodStats(UserId userId, LocalDate from, LocalDate to);

record PeriodStats(long created, long completed, long rolledOver) {
    public double completionRate() {
        long onDocket = created + rolledOver;
        return onDocket == 0 ? 0.0 : (double) completed / onDocket;
    }
}
```

`created` counts items with `rolledOverFromId IS NULL` (originally entered
for a day in range, not carried in); `rolledOver` counts items with
`rolledOverFromId IS NOT NULL` (carried into a day in range); `completed`
counts `status = DONE AND completedAt` falling in the corresponding UTC
instant range for `[from, to]` (same day→instant convention as M8 D8's
budget window). `completionRate` treats "on the docket" as created +
carried-in, consistent with D4/D5's framing.

```java
// com.kairon.projects.api.ProjectsApi — new method + DTO
/**
 * Per-project task-completion stats for tasks marked DONE with {@code
 * updatedAt} in {@code [from, to]} — an approximation, not a true
 * completed-at timestamp (D5). Added for M9 (docs/milestones/
 * M9-execution-summaries.md D4).
 */
List<ProjectPeriodStats> projectPeriodStats(UserId userId, LocalDate from, LocalDate to);

record ProjectPeriodStats(
        UUID projectId, String projectName, UUID categoryId, String categoryName,
        long tasksCompleted, BigDecimal estimateHoursCompleted, BigDecimal actualHoursCompleted) {
}
```

`categoryId`/`categoryName` (both nullable — an uncategorized project, or one
whose category was since deleted, which nulls `project.category_id` via its
existing `ON DELETE SET NULL` FK) are denormalized onto this row via a
`LEFT JOIN project_category` in the same query — the same "carry the
human-readable name so a cross-module consumer doesn't need a second
lookup" precedent `ProjectTaskView` already set for `projectName`/
`projectColor` (D15).

Repository queries (illustrative — exact JPQL constructor-expression syntax
confirmed at implementation time):

```java
// TodoItemRepository
@Query("""
        SELECT new com.kairon.todo.api.TodoApi$PeriodStats(
            SUM(CASE WHEN t.rolledOverFromId IS NULL THEN 1L ELSE 0L END),
            SUM(CASE WHEN t.status = com.kairon.todo.domain.TodoStatus.DONE
                          AND t.completedAt >= :fromInstant AND t.completedAt < :toInstantExclusive
                     THEN 1L ELSE 0L END),
            SUM(CASE WHEN t.rolledOverFromId IS NOT NULL THEN 1L ELSE 0L END))
        FROM TodoItem t
        WHERE t.userId = :userId AND t.deletedAt IS NULL AND t.day BETWEEN :from AND :to
        """)
TodoApi.PeriodStats periodStats(@Param("userId") UUID userId, @Param("from") LocalDate from,
        @Param("to") LocalDate to, @Param("fromInstant") Instant fromInstant,
        @Param("toInstantExclusive") Instant toInstantExclusive);
```

```java
// ProjectTaskRepository
@Query("""
        SELECT new com.kairon.projects.api.ProjectsApi$ProjectPeriodStats(
            p.id, p.name, c.id, c.name, COUNT(t.id),
            COALESCE(SUM(t.estimateHours), 0), COALESCE(SUM(t.actualHours), 0))
        FROM ProjectTask t JOIN Project p ON t.projectId = p.id
             LEFT JOIN ProjectCategory c ON p.categoryId = c.id
        WHERE p.userId = :userId AND p.deletedAt IS NULL AND t.deletedAt IS NULL
          AND t.status = com.kairon.projects.domain.ProjectTaskStatus.DONE
          AND t.updatedAt >= :fromInstant AND t.updatedAt < :toInstantExclusive
        GROUP BY p.id, p.name, c.id, c.name
        """)
List<ProjectsApi.ProjectPeriodStats> projectPeriodStats(@Param("userId") UUID userId,
        @Param("fromInstant") Instant fromInstant, @Param("toInstantExclusive") Instant toInstantExclusive);
```

```java
// com.kairon.identity.api.UserAccountApi — new method (D7)
/** Every user opted into execution summaries — the {@code @Scheduled} sweep's
 * own use only, never called from a request (no {@code @CurrentUser}). */
List<UserId> usersOptedIntoExecutionSummaries();
```
Native query on `AppUserRepository`: `SELECT id FROM app_user WHERE
(preferences->'assistant'->'executionSummaries'->>'enabled')::boolean =
true`.

### 4.2 Package layout additions — `com.kairon.assistant`

```
assistant
├── app/  SummaryPeriod (enum WEEK|MONTH; .resolve(LocalDate) -> Range;
│         .kind() -> AssistantRunKind, WEEK -> WEEKLY_SUMMARY / MONTH ->
│         MONTHLY_SUMMARY — the one place that mapping lives),
│         SummaryContextBuilder, SummaryGenerationService (the @Async runner),
│         SummaryScheduler (@Scheduled)
├── llm/  (AnthropicClient gains generateSummary; no new payload records —
│         plain String markdown)
└── web/  (AssistantRunController gains the summaries endpoints; AssistantDtos
          gains the request/list DTOs)
```

### 4.3 `AnthropicClient` addition

```java
SummaryResult generateSummary(SummaryRequest request);

record SummaryRequest(String systemPrompt, String userContent, String model, String effort) {
}

record SummaryResult(String markdown, String model, long inputTokens, long outputTokens) {
}
```

`AnthropicClientImpl.generateSummary` — same `@CircuitBreaker(name =
"anthropic")`/fallback shape as the two structured-output methods, but a
plain `sdk.messages().create(MessageCreateParams...)` call (no
`.outputConfig(...)`), reading the first text block of `response.content()`
as `markdown` directly instead of parsing a typed payload out of it. Reuses
the same empty-response guard `suggestTodos`/`generateProjectPlan` already
have (M8 §4.3) — no text block in the response throws, same as those two.

### 4.4 `AssistantRunService` additions

```java
@Transactional
public AssistantRunView requestSummary(UserId userId, SummaryPeriod period, LocalDate date) {
    requireExecutionSummariesAvailable(userId);   // mirrors requireAvailable/requireProjectGenerationAvailable
    requireWithinBudget(userId);
    requireNoRunInFlight(userId);                 // D3 — 409 otherwise

    SummaryPeriod.Range range = period.resolve(date);
    String model = resolveModel(accounts.assistantPreferences(userId));
    AssistantRun run = AssistantRun.pending(userId.value(), period.kind(), range.start(), range.end(), model);
    runs.save(run);
    log.info("Queued {} run {} userId={} period=[{},{}]",
            period.kind(), run.getId(), userId.value(), range.start(), range.end());
    return AssistantMapper.toRunView(run, List.of());
}
```

Note this method is `@Transactional` and returns before any Anthropic call —
the actual generation is dispatched by the **caller** (the controller) only
after this method returns, i.e. only after the transaction wrapping it has
committed (D2). `SummaryGenerationService.generate(UUID runId)` — a separate
`@Service`, `@Async` on its one public method — does the context-build +
Anthropic-call + `succeed`/`fail` work `AssistantRunService`'s existing two
methods (§4.4 of M8's plan) already model, reusing the identical
try/catch/`AssistantUpstreamException` shape:

```java
@Async
@Transactional
public void generate(UUID runId) {
    AssistantRun run = runs.findById(runId).orElseThrow(); // internal — no userId scoping needed, id is a UUIDv7
    SummaryContextBuilder.Context context = contextBuilder.build(
            new UserId(run.getUserId()), run.getKind(), run.getPeriodStart(), run.getPeriodEnd());
    run.start(context.inputSnapshot());
    runs.save(run);
    try {
        AnthropicClient.SummaryResult result = anthropicClient.generateSummary(
                new AnthropicClient.SummaryRequest(context.systemPrompt(), context.userContent(),
                        run.getModel(), properties.summary().effort()));
        String output = result.markdown() + "\n\n" + context.renderStatsTable(); // D16 — deterministic, appended, never model-authored
        run.succeed(output, result.inputTokens(), result.outputTokens());
        log.info("Summary run {} succeeded userId={} tokens={}+{}",
                run.getId(), run.getUserId(), result.inputTokens(), result.outputTokens());
    } catch (AssistantUpstreamException e) {
        run.fail(e.sanitizedDetail());
        log.warn("Summary run {} failed userId={} retryable={}", run.getId(), run.getUserId(), e.retryable());
    } finally {
        runs.save(run);
    }
}
```

`requireNoRunInFlight` throws `ApiException.conflict(...)` off
`runs.existsByUserIdAndKindInAndStatusIn(userId.value(),
List.of(WEEKLY_SUMMARY, MONTHLY_SUMMARY), List.of(PENDING, RUNNING))` (D3).

### 4.5 `SummaryContextBuilder`

Five data sources, nothing else — no journal (out of scope, D-none; this is
a `todo`/`projects` feature only), no tone (D14), no raw item dumps beyond
the bounded "notable" lists below:

1. **`TodoApi.periodStats(userId, periodStart, periodEnd)`** — `created`,
   `completed`, `rolledOver`, `completionRate()` (D4).
2. **`ProjectsApi.projectPeriodStats(userId, periodStart, periodEnd)`** —
   per project: tasks completed, estimate hours, actual hours, and now
   `categoryId`/`categoryName` (D15). This same call backs both item 2's
   display list below and item 5's category breakdown — fetched once, used
   two ways.
   - **Display list**: sorted by `tasksCompleted` descending, capped at
     `kairon.assistant.summary.max-highlight-items` (default 8) — for an
     account with many active projects, the narrative talks about the
     busiest ones, not every project that had a single task touched.
3. **`ProjectsApi.openTasksInActiveProjects(userId)`**, filtered in Java to
   just the tasks overdue as of `periodEnd` (`plannedEnd < periodEnd`), then
   **grouped by project** — not a flat task list. Per project: the overdue
   count, and the single most-overdue task's name + days overdue as a named
   example. Projects sorted by overdue count descending (ties broken by the
   worst single day-count), capped at the same `max-highlight-items` — a
   **project-level risk/delay signal**, the thing worth flagging in a
   narrative, not a raw dump of individual late tasks. (Still reuses the
   existing M8 port method as-is; the grouping is new logic in
   `SummaryContextBuilder`, not a new query — the list this aggregates over
   is already bounded to one user's active/on-hold projects, the same scale
   M8 D9 already judged small enough for Java-side aggregation.)
4. **The period bounds themselves** — `kind`, `periodStart`, `periodEnd`,
   rendered as a human date range ("Sep 21–27, 2026") or month name
   ("September 2026").
5. **Category attention breakdown (D15)** — item 2's *full, uncapped* result
   grouped by `categoryId` (an unset `categoryId` groups into
   "Uncategorized"), summed to each category's tasks-completed count and
   that count's **share of the period's total** completed project tasks
   across every category. Sorted by share descending, capped at
   `max-highlight-items` categories. A category that had zero completed
   tasks this period is simply absent from the list — there's no "0%" row
   to explain away. Each share is rounded independently to the nearest
   whole percent (no forced normalization) — the displayed percentages can
   therefore sum to 99% or 101% rather than exactly 100% on some splits, an
   accepted minor cosmetic drift rather than the added complexity of a
   largest-remainder rounding scheme for a number that's illustrative, not
   load-bearing.

**Three things are computed once, in Java, never left for the model**: (a)
estimate-vs-actual variance per project (`actualHoursCompleted -
estimateHoursCompleted`), rendered as a third number alongside the two sums;
(b) the overdue grouping in point 3; and (c) the category shares in point 5
— the model is handed "which projects/categories, how much, and the worst
example," never asked to count, group, or divide raw rows itself (the "the
model narrates, it does not count" principle §13.4 states, extended to
grouping and simple arithmetic, not just counting).

**System prompt** (constant, `kind`-independent — one cacheable prefix for
both `WEEK` and `MONTH`, unlike M8's two `horizon` variants, since nothing
here structurally differs between a week and a month):

```
You are Kairon's execution-summary assistant. Write a short, honest
narrative about how the stated period of work went, based only on the
numbers and item lists in the user message below. Never invent a task,
project, or number that isn't given to you, and never recompute or
second-guess a number you're handed — treat every count and every hours
figure as already correct.

Structure your response as markdown with these sections:
- A one-line headline verdict.
- "What got done" — reference the completion rate, briefly the busiest
  project(s) by tasks completed, and which category or categories got the
  most attention this period using the given share percentages (e.g. "most
  of this week's work was in the Home category"). If one category's share
  is much larger than the others, say so plainly — that imbalance is itself
  worth naming, not just the raw numbers.
- "Where things slipped" — name the specific **projects** carrying overdue
  work (their overdue count and the named worst example given to you) and
  any project whose actual hours ran notably over estimate. Flag risk at
  the project level, not as a list of individual late task titles. If both
  lists are empty or minor, say plainly that nothing slipped — don't invent
  a problem to fill this section.
- "Suggestions" — 1 to 3 concrete, specific observations grounded in the
  numbers above (never generic productivity advice unconnected to this
  period's actual data). A category that's been getting little or no
  attention while carrying overdue projects is exactly the kind of thing
  worth calling out here.

Keep the whole response under about 300 words. Plain, direct language — you
are describing what already happened, not motivating the user to do
something next.
```

**User content** (rendered per request; exact wording confirmed at
implementation time, but every one of these facts is present):

```
Weekly execution summary for Sep 21–27, 2026.

Todos: 9 created, 11 completed, 3 rolled over from a previous day.
Completion rate: 92% (11 of 12 items on the docket this week).

Projects with completed work this week:
- Kitchen remodel (Home): 4 tasks completed, 18h estimated, 22h actual (4h over).
- Garage cleanout (Home): 2 tasks completed, 4h estimated, 3h actual (1h under).
- Website redesign (Work): 2 tasks completed, 6h estimated, 5h actual (1h under).
- Backyard deck (Work): 2 tasks completed, 5h estimated, 6h actual (1h over).
- Fix bike (Uncategorized): 1 task completed, no hours logged.

Projects with overdue work as of Sep 27:
- Kitchen remodel: 3 tasks overdue (worst: "Order cabinet hardware", planned end Sep 24, 3 days overdue).
- Garage cleanout: 1 task overdue (worst: "Donate old tools", planned end Sep 20, 7 days overdue).

Categories by share of completed project work this week:
- Home: 6 of 11 tasks (55%).
- Work: 4 of 11 tasks (36%).
- Uncategorized: 1 of 11 tasks (9%).

[If a list is empty, the sentence introducing it is replaced with a plain
"No projects had completed work this week." / "No projects have overdue
work as of Sep 27." / "No completed project work to attribute to a category
this week." — never an empty bullet list with no explanatory line.]
```

**Example rendering** (illustrative only — the actual call is a narrative,
not structured output, so real wording varies run to run; this is one
plausible response to the exact context above):

> ## This week: mostly on track, one project falling behind
>
> **What got done.** You completed 11 of 12 items on your todo list this
> week (92%), with 3 items rolled over from earlier days. On the project
> side, 11 tasks were finished — most of that work (55%) landed in the
> **Home** category, led by Kitchen remodel (4 tasks) and Garage cleanout
> (2 tasks). **Work** projects picked up another 36%, split between Website
> redesign and the Backyard deck. One uncategorized task rounded out the
> rest.
>
> **Where things slipped.** Kitchen remodel is carrying 3 overdue tasks,
> the oldest being "Order cabinet hardware," now 3 days late. Garage
> cleanout has a single overdue item, "Donate old tools," which has been
> sitting 7 days past its planned date — longer than anything else this
> week. Kitchen remodel also ran 4 hours over estimate on the work it did
> finish (22h actual vs. 18h estimated).
>
> **Suggestions.**
> - "Donate old tools" (Garage cleanout) has been open longer than anything
>   else tracked this week — worth finishing it or explicitly deciding it's
>   not a priority, rather than letting it linger.
> - Kitchen remodel is both your busiest project this week and the one
>   running over on hours — if that pattern holds, its remaining estimates
>   may need revising upward.
> - Work-category projects got over a third of this week's attention with
>   no overdue items, a healthier pace than Home carried this week.

**`Context.renderStatsTable()` (D16)** — a pure function, no LLM call
involved, over the same four structured values already rendered into
`userContent` above. This is appended to the model's narrative verbatim to
form the full `output_markdown`:

> ## Stats
>
> | Metric | Value |
> | --- | --- |
> | Todos created | 9 |
> | Todos completed | 11 |
> | Rolled over | 3 |
> | Completion rate | 92% |
>
> ### By project
>
> | Project | Category | Completed | Est. hours | Actual hours | Variance |
> | --- | --- | --- | --- | --- | --- |
> | Kitchen remodel | Home | 4 | 18.0 | 22.0 | +4.0 |
> | Garage cleanout | Home | 2 | 4.0 | 3.0 | −1.0 |
> | Website redesign | Work | 2 | 6.0 | 5.0 | −1.0 |
> | Backyard deck | Work | 2 | 5.0 | 6.0 | +1.0 |
> | Fix bike | Uncategorized | 1 | — | — | — |
>
> ### Overdue
>
> | Project | Overdue tasks | Worst example | Days overdue |
> | --- | --- | --- | --- |
> | Kitchen remodel | 3 | Order cabinet hardware | 3 |
> | Garage cleanout | 1 | Donate old tools | 7 |
>
> ### By category
>
> | Category | Completed tasks | Share |
> | --- | --- | --- |
> | Home | 6 | 55% |
> | Work | 4 | 36% |
> | Uncategorized | 1 | 9% |

Every cell above is a value the context already computed for the prompt —
`renderStatsTable()` only formats, it never (re)computes. An empty section
(no overdue projects, say) omits that whole table rather than rendering one
with a single "—" row.

(`MONTH` renders identically, substituting "Monthly execution summary for
September 2026." and the month's own date bounds — no other content
difference.)

`context.inputSnapshot()` persists this exact rendered content (D2's
existing audit contract, unchanged) — a user reviewing a past summary run
sees precisely the numbers the model was given, the same guarantee M8/M8.5
already provide for their own runs.

### 4.6 `AssistantProperties.Summary` and `SummaryScheduler`

A fifth nested config record on the existing `AssistantProperties`
(alongside `TodoSuggestions`/`ProjectPlan`, M8/M8.5's own precedent for
per-feature config groups):

```java
public record Summary(String weeklyCron, String monthlyCron, String zone,
        int maxHighlightItems, String effort) {

    public Summary {
        if (weeklyCron == null || weeklyCron.isBlank()) {
            weeklyCron = "0 0 7 * * MON";
        }
        if (monthlyCron == null || monthlyCron.isBlank()) {
            monthlyCron = "0 0 7 1 * *";
        }
        if (zone == null || zone.isBlank()) {
            zone = "UTC"; // D9 — must match the app's Clock.systemUTC() bean
        }
        if (maxHighlightItems <= 0) {
            maxHighlightItems = 8;
        }
        if (effort == null || effort.isBlank()) {
            effort = "HIGH"; // a synthesis/narrative task, given more room than the bounded extraction M8's todo-suggestions effort (MEDIUM) targets
        }
    }
}
```

```java
@Component
class SummaryScheduler {

    @Scheduled(cron = "#{@assistantProperties.summary().weeklyCron()}", zone = "#{@assistantProperties.summary().zone()}")
    void weekly() {
        run(SummaryPeriod.WEEK);
    }

    @Scheduled(cron = "#{@assistantProperties.summary().monthlyCron()}", zone = "#{@assistantProperties.summary().zone()}")
    void monthly() {
        run(SummaryPeriod.MONTH);
    }

    private void run(SummaryPeriod period) {
        if (!properties.available()) {
            log.debug("Skipping {} auto-summary sweep — assistant module not available instance-wide", period);
            return; // dark means dark — no DB query, no per-user 403 churn, when the master switch/key is off
        }
        LocalDate yesterday = LocalDate.now(clock).minusDays(1); // D8
        for (UserId userId : accounts.usersOptedIntoExecutionSummaries()) {
            try {
                AssistantRunView view = assistantRuns.requestSummary(userId, period, yesterday);
                dispatcher.generate(view.id()); // same post-commit dispatch as the controller (D2)
            } catch (ApiException e) {
                log.info("Skipped {} auto-summary userId={}: {}", period, userId.value(), e.getMessage());
            } catch (Exception e) {
                log.warn("Auto-summary generation failed to queue userId={}", userId.value(), e);
            }
        }
    }
}
```

(D10 — every per-user failure is caught and logged; the loop always reaches
the next user. The instance-wide availability check is a small addition
found while reviewing this plan — without it, a disabled module still runs
a native query and N wasted `403`s through the log every single week/month,
instead of being genuinely dark per `ADR 0002`.)

### 4.7 Web layer

**`AssistantRunController`** gains:

```java
@PostMapping("/assistant/summaries")
@ResponseStatus(HttpStatus.CREATED)
public AssistantRunView requestSummary(@CurrentUser UserId userId, @Valid @RequestBody SummaryRequest req) {
    log.debug("POST /assistant/summaries userId={} period={} date={}", userId.value(), req.period(), req.date());
    AssistantRunView view = assistantRuns.requestSummary(userId, req.period(), req.date());
    summaryDispatcher.generate(view.id()); // dispatched only now — after requestSummary's transaction committed
    return view;
}

@GetMapping("/assistant/runs")
public AssistantRunPage listRuns(@CurrentUser UserId userId,
        @RequestParam(required = false) List<String> kind,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
    log.debug("GET /assistant/runs userId={} kind={} from={} to={} page={} pageSize={}",
            userId.value(), kind, from, to, page, pageSize);
    return assistantRuns.list(userId, kind, from, to, page, pageSize);
}
```

`GET /assistant/runs/{id}` (existing, unchanged) is still the poll target —
`AssistantRunView.outputMarkdown` (new field, D6) is populated once a
`WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` run reaches `SUCCEEDED`.

### 4.8 ArchUnit

No new rule — `onlyAssistantImportsTheAnthropicSdk` and
`assistantInternalsArePrivate` (M8 §4.7) already cover every new class here;
all of it lives inside the existing `assistant.app`/`assistant.llm`/
`assistant.web` packages plus the two narrow port extensions on
`todo.api`/`projects.api`/`identity.api`.

### 4.9 Authorization & errors

`POST /assistant/summaries`: `403` disabled/opted-out/budget (existing
chain, D11), `409` a run already in flight (D3), `502`/`503` upstream
(existing chain, unchanged). `GET /assistant/runs`/`GET /assistant/runs/{id}`:
plain `@CurrentUser`-scoped queries, `404` on a missing/foreign single run —
no change from M8's existing discipline.

---

## 5. API contract

Base path `/api/v1`. Bearer access token on every call.

| Method & path | Body | Response |
| --- | --- | --- |
| `POST /assistant/summaries` | `{ period: "WEEK"\|"MONTH", date }` | `201` + `AssistantRunView` (**`PENDING`** — poll `GET /assistant/runs/{id}` for completion); `403` disabled/opted-out/budget; `409` a summary run is already in flight. |
| `GET /assistant/runs?kind=&from=&to=&page=&pageSize=` | — | `AssistantRunPage`; every filter optional. |
| `GET /assistant/runs/{id}` | — | (existing, unchanged) `AssistantRunView`, now with `outputMarkdown` populated for summary kinds once `SUCCEEDED`; `404`. |

### `POST /assistant/summaries` response shape (immediately, before completion)

```json
{
  "id": "018f…", "status": "PENDING", "kind": "WEEKLY_SUMMARY",
  "model": "claude-sonnet-5", "periodStart": "2026-09-21", "periodEnd": "2026-09-27",
  "inputTokens": null, "outputTokens": null, "outputMarkdown": null,
  "createdAt": "2026-09-28T07:00:01Z", "suggestions": [], "suggestedProject": null
}
```

### `GET /assistant/runs/{id}` once `SUCCEEDED`

```json
{
  "id": "018f…", "status": "SUCCEEDED", "kind": "WEEKLY_SUMMARY",
  "model": "claude-sonnet-5", "periodStart": "2026-09-21", "periodEnd": "2026-09-27",
  "inputTokens": 2100, "outputTokens": 640,
  "outputMarkdown": "## This week\n\nYou completed 11 of 14 todos (79%)...",
  "createdAt": "2026-09-28T07:00:01Z"
}
```

---

## 6. Web

### 6.1 Feature folder additions — `web/src/features/assistant/`

```
features/assistant/
├── SummariesPage.tsx        history list (kind/date filters) + "Generate
│                               weekly summary now" / "...monthly..." buttons;
│                               row click opens SummaryDetail
├── SummaryDetail.tsx         renders outputMarkdown (narrative + D16's stats
│                               table) via react-markdown + remark-gfm (D17,
│                               the first read-only markdown/GFM-table render
│                               in this app); while PENDING/RUNNING, a spinner
│                               + polling state instead
├── useAssistantRuns.ts       useAssistantRunsList (paginated), useRequestSummary
│                               (mutation), useAssistantRun(id, { poll }) — the
│                               first refetchInterval-based polling hook in web/
└── assistantKeys.ts          (existing) gains runs.list(filters)
```

`SummariesPage.tsx` calls `GET /assistant/runs` with `kind=WEEKLY_SUMMARY&kind=MONTHLY_SUMMARY`
by default — the endpoint itself is general-purpose (D12, any run kind), but
this screen is specifically the summaries history, not a view over every
`TODO_SUGGESTION`/`PROJECT_GENERATION` run too.

`AppLayout.tsx` gains a new top-level "Summaries" `NavLink`, placed after
"Projects" and before "Account"/"About" (D18) — a deliberate departure from
M8/M8.5's "no new nav entry, embed a button in an existing screen" pattern
(`SuggestTodosButton` on Today/Day view, "New project from description" on
the project list). Not gated behind the feature flag in the nav itself
(same as every other screen — the flag gates the *action*, a disabled state
explains why, per the existing `SuggestedTaskList` 403 precedent, M8 §7 web
tests); route `/summaries`, matching `/projects`' plural-noun shape.

### 6.2 Data layer

- `web/src/lib/api/assistant.ts` gains `requestSummary(body)`,
  `listRuns(filters)`.
- `useAssistantRun(id, { poll })` sets TanStack Query's `refetchInterval` to
  a short fixed value (e.g. 2s) while `status` is `PENDING`/`RUNNING`, and
  `false` once `SUCCEEDED`/`FAILED` — the standard TanStack polling pattern,
  new to this codebase but not novel engineering.
- `useRequestSummary` invalidates `assistantKeys.runs.list()` on success so
  the new `PENDING` row shows up in the history list immediately.

### 6.3 No `AccountPage.tsx` change

`executionSummariesEnabled`'s toggle already exists (M8's four-toggle
`AssistantSettings.tsx` shipped it ahead of the feature it gates, per M8
§6.3/ROADMAP's own note) — nothing new to add there.

---

## 7. Testing

### Backend

| Test | Type | Covers |
| --- | --- | --- |
| `TodoItemRepositoryTest` (extend) | `@DataJpaTest` + Testcontainers | `periodStats` counts created/completed/rolled-over correctly across a seeded window, including a rollover-cancelled source item not double-counted. |
| `ProjectTaskRepositoryTest` (extend) | `@DataJpaTest` + Testcontainers | `projectPeriodStats` groups by project, sums hours only for tasks `DONE` with `updatedAt` in range; a task done outside the window is excluded. |
| `AssistantRunServiceTest` (extend) | plain JUnit 5 + AssertJ, Mockito | `requestSummary` throws before touching `AnthropicClient` when disabled/opted-out/budget-exceeded/run-in-flight; a successful `SummaryGenerationService.generate` persists `SUCCEEDED` with `outputMarkdown` set to the model's narrative **plus** `renderStatsTable()`'s output appended (D16), never just the narrative alone; an `AssistantUpstreamException` persists `FAILED` with no table appended (nothing to render — the run never reached a result). |
| `SummaryPeriodTest` | plain JUnit 5 + AssertJ | `WEEK.resolve`/`MONTH.resolve` against known dates, including the D8 "yesterday" edge cases (Monday firing, 1st-of-month firing) and a leap-February month boundary. |
| `SummaryContextBuilderTest` | plain JUnit 5 + AssertJ, Mockito for `TodoApi`/`ProjectsApi` | assembles the expected system/user content from mocked port responses; variance is precomputed correctly (D13); overdue tasks from `openTasksInActiveProjects` are correctly grouped by project with the right overdue count and worst-example task (D13); the `max-highlight-items` cap is respected on the per-project completed-work list, the per-project overdue list, and the category-breakdown list independently; a project with no overdue tasks is excluded from the overdue grouping entirely; category shares (D15) are computed from `projectPeriodStats`' **full** result, not the already-capped display list, so shares stay correct once more than `max-highlight-items` projects have completed work; a `null categoryId` groups into "Uncategorized"; a category with zero completed tasks this period is absent from the list, not shown at 0%; an empty overdue-projects or category list renders the plain "nothing to show" sentence, not an empty bullet list; `tone` is never read (D14); **`renderStatsTable()`** (D16) produces the exact markdown table for a known `Context` — every cell traces to a value also present in `userContent`, so a test asserting on both catches any drift between what the model was told and what the table says; a `Context` with no overdue projects omits that whole sub-table rather than rendering an empty one. |
| `SummarySchedulerTest` | plain JUnit 5 + AssertJ, Mockito for `UserAccountApi`/`AssistantRunService` | one user's `403`/`409` doesn't stop the sweep from reaching the next user (D10); an empty opted-in list is a no-op. |
| `AssistantRunControllerTest` (extend) | `@WebMvcTest` | `POST /assistant/summaries` happy path returns `201` `PENDING`; `409` passthrough; `GET /assistant/runs` filters/pages correctly. |
| `AssistantSummaryFlowIntegrationTest` | `@SpringBootTest` + Testcontainers, `AnthropicClient` faked | enable the feature, seed todo/project-task history spanning a week, `POST /assistant/summaries` → `PENDING` returned immediately, poll `GET /assistant/runs/{id}` until `SUCCEEDED` with `outputMarkdown` set and token counts recorded; a second concurrent `POST` while the first is in flight gets `409`. |

### Web

| Test | Covers |
| --- | --- |
| MSW handlers | `POST /api/v1/assistant/summaries`, `GET /api/v1/assistant/runs` (paginated), `GET /api/v1/assistant/runs/{id}` returning `PENDING` then `SUCCEEDED` on successive calls (to exercise polling). |
| `useAssistantRun.test.ts` | polling stops once status leaves `PENDING`/`RUNNING`. |
| `SummariesPage.test.tsx` | renders history; "Generate" triggers the mutation and the new row appears; clicking a row opens the detail view. |
| `SummaryDetail.test.tsx` | given an `outputMarkdown` string containing a GFM pipe table (D16/D17), renders an actual `<table>` with the right rows — not raw pipe-syntax text, which is what a plain CommonMark renderer would do without `remark-gfm`. |
| `assistant-summaries.spec.ts` (Playwright, new) | enable the feature → generate a weekly summary → detail view shows the canned `FakeAnthropicClient` narrative **and** the deterministic stats table rendered as an actual table, once polling resolves it to `SUCCEEDED`. `FakeAnthropicClient` gains a `generateSummary` stub returning fixed narrative markdown; the appended table (D16) comes from real `SummaryContextBuilder`/`renderStatsTable()` code, not the fake — so this is also the one end-to-end check that the table-rendering path itself works, not just the LLM stub. |

---

## 8. Task breakdown (suggested order)

1. `KaironApplication`: add `@EnableAsync`; confirm the auto-configured
   virtual-thread executor is picked up by a throwaway `@Async` smoke test
   (D2 — cheap to verify before building on it).
2. `TodoApi.periodStats` + repo query + test; `ProjectsApi.projectPeriodStats`
   + repo query + test; `UserAccountApi.usersOptedIntoExecutionSummaries` +
   native query + test.
3. `SummaryPeriod` enum + `.resolve` + test.
4. `AnthropicClient.generateSummary` + `AnthropicClientImpl` implementation
   (extend `FakeAnthropicClient` too); `AssistantRun.succeed(markdown, ...)`
   overload; `AssistantRunView.outputMarkdown` + `AssistantMapper` update.
5. `SummaryContextBuilder` + `Context.renderStatsTable()` (D16) + test.
6. `AssistantRunService.requestSummary` (+ `requireNoRunInFlight`) +
   `SummaryGenerationService.generate` + tests.
7. `AssistantProperties.Summary` (§4.6) + `kairon.assistant.summary.*`
   defaults in `application.yml` (`zone: UTC` — D9); `SummaryScheduler` +
   test (incl. the instance-availability short-circuit); add
   `/api/v1/assistant/summaries` to the rate-limit `paths` list (M8.5 §11's
   correction — literal list, not a wildcard).
8. `AssistantRunRepository.existsByUserIdAndKindInAndStatusIn` +
   list/pagination query; `AssistantRunPage`.
9. `AssistantRunController`/`AssistantDtos` additions + controller tests.
10. `AssistantSummaryFlowIntegrationTest`.
11. Web: `assistant.ts`/`types.ts` additions, `useAssistantRuns.ts`
    (including the polling hook), MSW handlers.
12. Web: add `react-markdown` + `remark-gfm` (D17); `SummariesPage.tsx`,
    `SummaryDetail.tsx`; nav entry.
13. Web tests (incl. `SummaryDetail.test.tsx`'s GFM-table assertion) +
    Playwright happy path.
14. Docs: tick `ROADMAP.md`'s M9 boxes; `CLAUDE.md` "Current state" —
    extend the `assistant`/`todo`/`projects`/`identity` module bullets and
    the web bullet, note `@EnableAsync` alongside the existing
    `@EnableScheduling` mention; mark this plan `Accepted`.

---

## 9. Open questions / risks

| # | Question | Notes |
| --- | --- | --- |
| Q1 | Is a fixed 2s poll interval (§6.2) right, or should it back off (2s → 5s → 10s) for a call that can legitimately take a minute or more (cf. M8.5's observed ~90s project-plan latency)? | Start fixed at a short interval — simplest viable option — and revisit only if real usage shows it's wasteful. |
| Q2 | `kairon.assistant.summary.max-highlight-items` (D13) — is 8 the right count for the narrative's "notable items" list? | Unmeasured guess, same posture as every other assistant context-size default (M8 §9 Q2, M8.5 §9 Q1). |
| Q3 | A crashed app mid-`RUNNING` (before `@Async`'s try/finally completes) leaves a run permanently `RUNNING`, which then permanently blocks that user via D3's in-flight guard with no timeout-based recovery. | Accepted risk for now — this codebase has no job-recovery infrastructure anywhere yet (no dead-run sweeper, no lease/heartbeat pattern). A `DELETE /assistant/runs/{id}` already exists (M8) as a manual escape hatch the user can reach for. A real fix (a startup sweep that fails any `RUNNING` row older than the request timeout) is a candidate for its own small follow-up if this actually bites someone. |
| Q4 | Should `GET /assistant/runs` support filtering by `status` too, not just `kind`/date range? | Not in the roadmap's literal `kind=&from=&to=` shape — left out; trivial to add later if the summaries screen turns out to want a "failed only" filter. |
| Q5 | **D3's in-flight guard has a check-then-act race**: `requireNoRunInFlight`'s `exists` query and `AssistantRun.pending(...)`'s save aren't atomic, so two requests arriving close together (a user double-clicking "Generate," or an on-demand call landing at the exact moment the scheduled sweep processes the same user) could both pass the check before either commits, producing two concurrent runs instead of the intended `409`. Found while reviewing this plan — not caught earlier. | Accepted as a small, low-likelihood risk rather than adding a partial unique index + migration (`CREATE UNIQUE INDEX ... ON assistant_run(user_id) WHERE kind IN (...) AND status IN ('PENDING','RUNNING')`), which would undercut D1's "no migration" property for a race that needs near-simultaneous requests to trigger and whose worst case is "one extra billed run," not data corruption — same size of risk this plan already accepts elsewhere (Q3). Worth the migration only if real usage shows it actually happens. |
| Q6 | **The scheduled sweep can fire several users' Anthropic calls close together**, and every one of them shares M8's single `anthropic` circuit breaker instance (M8 D6) — a burst of transient failures early in a sweep could open the breaker and fail later users' calls in the same sweep for a reason that has nothing to do with their own account. | Not addressed in this plan — no per-sweep throttling or staggering is proposed. Likely fine at this app's personal/family scale (a handful of opted-in accounts, not hundreds), but worth watching once there's a sweep with enough users to make this observable; a simple fix later would be a small delay between users in `SummaryScheduler.run()`, not a redesign. |

---

## 10. Corrections found during implementation

- **D9's `@Scheduled` cron expressions use `${...}` property placeholders,
  not the plan's sketched `#{@assistantProperties.summary().weeklyCron()}`
  SpEL bean reference.** `@ConfigurationProperties` beans registered via
  `@EnableConfigurationProperties` don't reliably get a predictable bean name
  to reference from SpEL, and this codebase already has a working precedent
  for exactly this need — `RefreshTokenService`'s purge job uses `@Scheduled(cron
  = "${kairon.security.refresh-token.purge-cron:0 30 3 * * *}")`. `SummaryScheduler`
  follows that same property-placeholder-with-a-default pattern instead, for
  both `weekly-cron`/`monthly-cron` and `zone` — functionally identical, just
  a plainer mechanism already proven in this repo.
- **`GET /assistant/runs`'s light row shape is a dedicated `AssistantMapper.toRunListView`**,
  not the existing `toRunView` reused with empty/null fields as §4.7 left as
  an implementation-time choice — a small, explicit second mapping method
  reads more clearly than a call site passing `List.of()`/`null` into the
  full mapper and relying on a reader to know those particular arguments mean
  "this is the light shape."
- **`AssistantRunRepository.findPage`'s optional `kind`/`from`/`to` filters
  needed a different shape than the `(:param IS NULL OR ...)` JPQL first
  tried.** Against real Postgres (Testcontainers), that idiom fails at
  runtime — `ERROR: could not determine data type of parameter $N` — because
  a bind parameter used *only* in an `x IS NULL` check, with no other typed
  comparison in the same disjunct, gives Postgres nothing to infer its type
  from over the JDBC extended protocol. Fixed by passing explicit
  `hasKinds`/`hasFrom`/`hasTo` boolean flags alongside always-non-null
  placeholder values (an empty list, `Instant.EPOCH`, `Instant.now(clock)`)
  and writing the guard as `(:hasFrom = false OR r.createdAt >= :from)` —
  every parameter now also appears in an unambiguous typed comparison, so
  Postgres can always infer its type even when the `OR` short-circuits it
  logically. Caught by `AssistantSummaryFlowIntegrationTest`'s real-Postgres
  run, not by compilation or by a mocked-repository unit test.

## 11. Doc updates this milestone produces

- `ROADMAP.md` — M9 boxes ticked once implemented; a "See
  `milestones/M9-execution-summaries.md`" line added to the M9 section now
  (this plan).
- `DESIGN.md` §13.1/§13.3 — status line for the "Weekly / monthly execution
  summary" row moves from planned to shipped; no content correction expected
  (§13.3's async/polled framing is exactly what this plan implements, not
  something it revises).
- `DATA_MODEL.md` — no change (D1 — no migration); possibly a note that
  `output_markdown`/`period_start`/`period_end` are now actually written to,
  if the existing text implies otherwise.
- `CLAUDE.md` "Current state" — deferred until implementation, matching M8/
  M8.5's own precedent (new module bullets added only once built).
- This file — `Status: Draft` until implementation, then `Accepted —
  implemented` with any corrections found along the way, following M8/M8.5's
  own honesty-over-editing-history precedent for §11-style corrections.
