# M10 — Weekly journal reflection — implementation plan

Status: **Accepted — implemented.** (Note: every `V009` reference below was
written while this plan assumed that version number was free; by the time
this milestone was built, `V009` had already been taken by an unrelated
project-category-ranking migration merged to `main`, so the actual migration
shipped as `V010__assistant_batch.sql`. Left uncorrected in the body text
below as the historical record of what was planned; `CLAUDE.md`'s "Current
state" and `docs/DATA_MODEL.md` carry the accurate `V010` references.)
Companion to [`../ROADMAP.md`](../ROADMAP.md) (M10 acceptance
criteria), [`../DESIGN.md`](../DESIGN.md) §13 (§13.1/§13.3/§13.4/§13.5 — the
"journal reflection" row, the "summaries and reflection run as background
jobs... and are always polled" sentence, the reflection prompt-content bullet,
and the "journal-reflection opt-in is deliberately separate" privacy rule are
all load-bearing for this plan), [`../DATA_MODEL.md`](../DATA_MODEL.md), and
[`../adr/0002-ai-assistant-anthropic.md`](../adr/0002-ai-assistant-anthropic.md).
Builds on the `assistant` module M8 established and M9's async
(`@Async`/separate-bean dispatch) pattern, reused unchanged except where noted.

Like M9, this milestone needs **no new migration and no new table**. M8's
`V007__assistant.sql` already includes `JOURNAL_REFLECTION` in
`assistant_run.kind`'s check constraint (M8 D2, written specifically so this
milestone wouldn't need to widen it), and `app_user.preferences.assistant`
already carries `journalReflection.enabled` and `tone` keys (M8 D3/D14) that
nothing has read until now. Unlike M9, this milestone also needs **no new
port method on any other module** — `JournalApi.range` (M8), `TodoApi
.periodStats` (M9), and `ProjectsApi.projectPeriodStats` (M9) already return
exactly the data this feature needs. This is the most purely-application-code
assistant milestone yet: every new line of code lives inside `com.kairon
.assistant` plus a handful of frontend files.

It is also the feature that sends full journal text off the instance — the
last one ships for exactly that reason (`DESIGN.md` §13.1) — so the opt-in UX
gets more friction than M8/M8.5/M9's plain checkboxes (D13), and this plan
deliberately does **not** give it M9's `@Scheduled` auto-trigger (D4).

**A second, mostly-orthogonal unit of work rides along in this milestone**
(user request, folded in here rather than given its own M9.5-style slot):
migrating M9's `@Scheduled` weekly/monthly execution-summary sweep from N
individual synchronous Anthropic calls to the **Anthropic Message Batches
API**, resolving the `ROADMAP.md` backlog item "Batch API for scheduled
summaries (50% cost)." This only touches the already-shipped M9 scheduler —
it shares no code path with the journal-reflection feature above — but is
planned and built in the same pass since both are backend `assistant`-module
work being picked up together. Verified directly against the pinned
`anthropic-java-core:2.34.0` jar already in this repo's Gradle cache (not a
beta/unstable surface — see D18): a non-beta `sdk.messages().batches()`
service exists with exactly the `create`/`retrieve`/`resultsStreaming` shape
this migration needs.

---

## 1. Scope

### In

- `JOURNAL_REFLECTION` runs: the week's `journal_entry` content **in full**
  (not summarized, not excerpted) plus light `todo`/`projects` grounding for
  context, narrated by the model into a reflective markdown response —
  patterns, blind spots, questions, encouragement (`DESIGN.md` §13.4) — with
  tone shaped by the user's `assistant.tone` preference, read for the first
  time by any feature (D9).
- `POST /assistant/journal-reflection` (`{weekOf: date}`) — creates a
  `PENDING` run and returns immediately, same async/poll shape M9 built for
  summaries (D3); the client polls the existing `GET /assistant/runs/{id}`.
  No scheduled auto-trigger (D4) — on demand only.
- Its own opt-in (`assistant.journalReflection.enabled`, already reserved by
  M8), gated behind a confirmation step stronger than the other three
  features' plain checkboxes (D13) — the user must explicitly acknowledge
  that full journal text is sent to Anthropic before the toggle takes effect.
- A new tone picker in `AssistantSettings.tsx` (`encouraging` / `balanced` /
  `direct`, D9) — the first UI for a preference every prior milestone's plan
  left stored-but-unread.
- Web: `JOURNAL_REFLECTION` added as a third kind on the existing Summaries
  screen (D14) — a "Generate weekly reflection" button (shown only when
  opted in) alongside M9's weekly/monthly summary buttons, reusing
  `SummariesPage`/`SummaryDetail`/the polling hook unchanged beyond a new
  `KIND_LABEL` entry.
- Tests: context-builder and service unit tests, MockMvc, an
  `@SpringBootTest` flow test (opt-in → generate → poll to `SUCCEEDED`;
  empty-week refusal; in-flight conflict independent of a concurrent
  summary run), Vitest for the settings confirmation flow and the extended
  Summaries screen, a Playwright happy path.
- **Batch API migration for M9's scheduled summary sweep** (D17–D24):
  `SummaryScheduler.weekly()`/`.monthly()` build every opted-in user's
  context and submit all of them as **one Anthropic batch per firing**
  instead of dispatching N individual `@Async` calls; a new
  `SummaryBatchPollingScheduler` (fixed-delay `@Scheduled`) checks
  in-flight batches and writes each result back onto its `assistant_run`
  once the batch ends. New migration `V009__assistant_batch.sql`
  (`assistant_batch` table + `assistant_run.batch_id`). On-demand
  `POST /assistant/summaries` and this milestone's own
  `POST /assistant/journal-reflection` are **unaffected** — both keep
  today's per-run dispatch; only the unattended scheduled sweep moves to
  batching. No API contract change, no web change.

### Out (deferred, with what would pick it up)

- **Scheduled weekly auto-generation for journal reflection** — confirmed
  out for this milestone (D4); M9's `SummaryScheduler` pattern is the
  template if this is revisited later, but reflection stays an explicit
  per-request action for now. Since it has no scheduled trigger at all, the
  batch migration below has nothing to apply to for this kind either — it's
  scoped to `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` only (D17).
- **Batching for on-demand calls or for `TODO_SUGGESTION`/`PROJECT_GENERATION`
  runs** — out of scope; those are all requests a user is actively waiting
  on in the UI, and the Batch API's "within 24 hours" completion window is
  the wrong shape for anything with a spinner in front of it (D17).
- **Chunking one firing's requests across multiple batches** for an account
  count large enough to hit Anthropic's per-batch request-count limit — not
  needed at this instance's scale; flagged as Q4 rather than built
  speculatively.
- **A dedicated "Reflection" nav entry/page separate from Summaries** —
  considered and explicitly declined in favor of extending the existing
  Summaries screen (D14); revisit only if that screen's mixed kind list
  proves confusing in practice.
- **Per-entry or per-mood-trend reflection** (e.g. "your mood dipped
  Wednesday") — out of scope; the model sees each entry's `mood` value as
  part of the full entry content (D10) and may reference it in its own
  narrative, but nothing in this milestone computes a trend or chart over
  mood values the way M9 computes category shares. A real mood-trend feature
  would be its own future milestone with its own aggregation logic.
- **Defaulting this kind's model to `claude-opus-5`** — considered (`DESIGN
  .md` §13.4 mentions opus for "deeper reflection") and declined; stays on
  the same instance-default-unless-overridden path every other kind uses
  (D12). A user who wants opus for their own reflections sets
  `modelOverride` themselves.
- **Bring-your-own tone per run** — `tone` is a stored per-user preference,
  not a per-request parameter on `POST /assistant/journal-reflection`, same
  "preference, not request field" shape `modelOverride` already has.

---

## 2. Decisions locked for this milestone

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **No migration.** `assistant_run.kind` already permits `JOURNAL_REFLECTION` (`V007`, M8 D2); `app_user.preferences.assistant.journalReflection.enabled` and `.tone` are existing unread fields (M8 D3/D14's `AssistantPreferencesView`/`AssistantPreferenceMapper`). | Same payoff M9 D1 already banked once — M8 deliberately over-specified both the schema and the preferences shape for exactly M9 and M10. |
| D2 | **No new table.** A `JOURNAL_REFLECTION` run's output is `assistant_run.output_markdown` only, the same shape M9's `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` already use — not `assistant_suggested_task`'s `PROPOSED → ACCEPTED \| DISMISSED` shape. | A reflection is a narrative response, not a list of proposed actions — there is nothing to accept or dismiss, so M8's suggestion-table shape genuinely doesn't apply (matches `DATA_MODEL.md`'s existing framing: only `TODO_SUGGESTION` and `PROJECT_GENERATION` produce a child row). |
| D3 | **Async and always polled, reusing M9's two-method-split pattern** — `AssistantRunService.requestJournalReflection(...)` creates the `PENDING` row and returns; a separate `@Service`, `@Async`-annotated `ReflectionGenerationService.generate(UUID runId)` does the context-build + Anthropic call + `succeed`/`fail`, dispatched by the caller only after the request method's transaction commits (identical shape to M9 D2, same reason: a same-class `@Async` self-call is a Spring AOP no-op). | Not actually a new decision — `DESIGN.md` §13.3 already states "summaries **and reflection** run as background jobs... and are always polled," written before M9 existed. This milestone just implements the half of that sentence M9 didn't. |
| D4 | **On-demand only — no `@Scheduled` auto-trigger for this kind**, unlike M9's weekly/monthly sweep. *(User decision, confirmed while drafting this plan.)* | `DESIGN.md` §13.3's parenthetical — "(`@Async`, plus the `@Scheduled` auto-trigger for summaries)" — scopes the auto-trigger to summaries specifically, not reflection; read literally rather than assumed to extend. Also the stronger practical reason: this is the one feature that sends full journal text off the instance (`DESIGN.md` §13.1/§13.5), and letting that happen only when the user actively asks — never silently on a weekly timer — is a meaningfully different privacy posture worth keeping, not an oversight to "complete" by symmetry with M9. |
| D5 | **Its own independent in-flight-run guard, not grouped with `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY`.** M9's `AssistantRunService.requireNoRunInFlight(UserId)` (hardcoded to `{WEEKLY_SUMMARY, MONTHLY_SUMMARY}`) is generalized to take a `Set<AssistantRunKind>` parameter; the summary call site passes the same two kinds as before (unchanged behavior), and `requestJournalReflection` passes `{JOURNAL_REFLECTION}`. | A weekly summary and a weekly reflection are different reports serving different purposes — a user reasonably wants both running at once, unlike M9's own grouping, which exists because `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` are two granularities of the *same* report a double-click could duplicate. Generalizing the one existing method's signature is simpler than writing a second, nearly-identical guard method. |
| D6 | **Reuses `SummaryPeriod.WEEK.resolve(LocalDate)` (M9) for the Monday–Sunday week math only** — never calling `SummaryPeriod.WEEK.kind()` (which maps to `WEEKLY_SUMMARY`, the wrong kind here). `requestJournalReflection` resolves `weekOf` to a range via that one static call, then builds its own `AssistantRun` with `AssistantRunKind.JOURNAL_REFLECTION` directly. | Avoids duplicating the "Monday–Sunday week containing this date" arithmetic a second time (the same lesson D-level reuse already follows elsewhere in this codebase) without coupling `JOURNAL_REFLECTION`'s kind selection to `SummaryPeriod`'s own enum-to-kind mapping, which only ever meant `WEEK → WEEKLY_SUMMARY` / `MONTH → MONTHLY_SUMMARY`. |
| D7 | **Zero new cross-module port methods.** `JournalApi.range(userId, from, to)` (M8), `TodoApi.periodStats(userId, from, to)` (M9), and `ProjectsApi.projectPeriodStats(userId, from, to)` (M9) are called as-is from the new `JournalReflectionContextBuilder`. | Every port method this feature needs already exists for a different feature's reasons; adding a parallel method would just be duplication. This is the clearest instance yet of M8/M9's over-specified-schema-and-ports strategy paying off. |
| D8 | **An empty week is refused up front, before any `assistant_run` row is created or any token spent** — `requestJournalReflection` calls `JournalApi.range(userId, weekStart, weekEnd)` first; an empty result throws `ApiException.unprocessable("No journal entries for that week — nothing to reflect on.")` (new 422 factory, alongside the existing `forbidden`/`conflict` ones). | This user's own journal/todo usage is irregular — days and sometimes whole weeks get skipped (not a corner case for this account, a routine one). Spending a budget-counted Anthropic call narrating silence would be a worse experience than a clear, immediate "nothing to reflect on" response, and a prompt with no journal content to reflect on is exactly the "don't invent" failure mode every other assistant feature's system prompt already guards against — better to never make the call. |
| D9 | **`tone` is `encouraging` \| `balanced` \| `direct`**, read for the first time by any feature. Stored as the existing lowercase `assistant.tone` string preference (no schema change — already `jsonb`); `"balanced"` stays the default so no existing stored value needs migrating. The system prompt has exactly three fixed variants selected by tone — the only axis of variation in this feature's system prompt (no `horizon`-style second axis). *(User-confirmed option set.)* | Spans the range `DESIGN.md` §13.4 gestures at ("questions... encouragement") without open-ended free text, matching every other per-feature setting in this app being a small fixed enum, not free text. Keeping `balanced` as the default and one of the three options means the field nothing has touched yet already has a sane, already-stored value — zero backfill. |
| D10 | **Light grounding, deliberately narrower than M9's `SummaryContextBuilder`**: `TodoApi.periodStats(userId, weekStart, weekEnd)` rendered as one sentence (created/completed/rolled-over counts + completion rate), plus the top `kairon.assistant.journal-reflection.max-projects` (default 5) projects by `tasksCompleted` from `ProjectsApi.projectPeriodStats`, named only (project name + count — no hours, no variance, no overdue grouping, no category breakdown). The week's journal entries themselves are rendered **in full** — day, title (if set), `mood`, and the complete `content` — ordered by day then position, capped at `kairon.assistant.journal-reflection.max-entries` (default 30, oldest-first truncation if exceeded, logged `warn`) as a pathological-case safety bound, not an expected-case limit. | `DESIGN.md` §13.4: "the week's journal entries in full, plus **light** todo/project context for grounding" — read literally as asymmetric: the journal content is the whole point and stays complete, while the practical-activity context is there only so the model can relate a journal entry to something concrete ("you mentioned the tiler on Tuesday, and did complete that project task") without re-deriving M9's full risk/attention analysis, which this feature doesn't need. |
| D11 | **No deterministic stats table appended to `output_markdown`** — unlike M9 D16, this run's entire output is the model's own reflective narrative, nothing computed-and-appended afterward. | M9's stats table exists because a summary's whole point is numbers the model must never be trusted to recompute. A reflection's whole point is the narrative itself — `DESIGN.md` §13.4 describes its output only as "patterns, blind spots, questions, encouragement," never numbers — so there's nothing here that needs the same never-model-authored guarantee a count or a sum needs. |
| D12 | **Model defaults the same way every other kind does** — `resolveModel(prefs)` (existing, unchanged), the instance-wide `kairon.assistant.model` unless the user's own `assistant.modelOverride` is set. *(User decision, confirmed while drafting this plan.)* | `DESIGN.md` §13.4's "`claude-opus-5` is available for deeper reflection" reads as "a user may reasonably choose opus here via the override already in the settings panel," not "this kind's default changes" — special-casing one kind's default model would be a second code path for every other feature's `resolveModel` call to stay consistent with, for a cost tradeoff (opus is pricier) better left to the user who'd be the one paying the budget cost. |
| D13 | **Enabling the checkbox opens a confirmation step before the preference is actually written**, built on the existing `Dialog` component (not a new `alert-dialog` Radix package) — explicit copy naming that full journal entry text, not a summary of it, is sent to Anthropic for this feature specifically, with an "Enable" button that commits the `PATCH /me` and a "Cancel" that reverts the checkbox with no network call. Turning the feature back off needs no confirmation. | `ROADMAP.md`'s M10 bullet and `DESIGN.md` §13.5 both call for "a prominent confirmation" here specifically, stronger than the one-line notices under M8/M8.5/M9's three checkboxes — this is the one opt-in whose data-sharing consequence (full journal text leaving the instance) is qualitatively different from "your task list" or "your project description." Reusing the existing `Dialog` primitive avoids a new dependency (`@radix-ui/react-alert-dialog`) for a single call site — `CLAUDE.md`'s "keep new dependencies minimal" preference — since a plain `Dialog` already does everything an alert-dialog would here (it doesn't need Radix's alert-dialog-specific focus/escape semantics for a two-button confirm). |
| D14 | **Extends the existing Summaries screen rather than adding a new nav entry or embedding in Journal.** `JOURNAL_REFLECTION` becomes a third kind on `SummariesPage`/`SummaryDetail` (both already kind-generic via a `KIND_LABEL` map) — a third "Generate weekly reflection" button, shown only when `journalReflectionEnabled`, and `GET /assistant/runs?kind=` simply includes `JOURNAL_REFLECTION` in its filter list when the page asks for it. *(User-confirmed choice over a new nav entry or embedding in the Journal feature.)* | Both existing components were already written kind-agnostically enough that adding a third kind is a handful of lines, not a new screen — and this is explicitly the cheaper option the user picked over repeating M9 D18's "new nav entry" exception a second time, or scattering assistant-run history across two different feature areas (Journal and Summaries) for what's still fundamentally "a past assistant run you come back to read." The page's own per-row kind badge/label already disambiguates a reflection row from a summary row, so "Summaries" containing a non-summary kind is accepted as a small, low-cost naming imperfection rather than something worth nav churn to fix now — revisit the label only if real use shows it's actually confusing. |
| D15 | **`/api/v1/assistant/journal-reflection` is added to the existing `/assistant/**` rate-limit filter's literal `paths` list.** | The same "this list is literal, not a wildcard" correction M8.5 §11 and M9 flagged for their own new endpoints — called out a third time here specifically so a future reader doesn't have to rediscover it. |
| D16 | **A hard cap on entries rendered into one prompt** (`max-entries`, default 30, D10) exists purely as a pathological-case guard — M3 allows multiple entries per day, so an unusually journal-heavy week is possible even though a normal week is nowhere near this bound. | Same posture as M8 D5's suggestion-count cap: a hard limit enforced in Java, not assumed away, even though the expected case never approaches it. |

### Batch API migration for M9's scheduled summary sweep (D17–D24)

| # | Decision | Rationale |
| --- | --- | --- |
| D17 | **Scope boundary: the Batch API applies only to M9's `@Scheduled` sweep** (`WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` auto-generated runs). `POST /assistant/summaries`, this milestone's own `POST /assistant/journal-reflection`, `POST /assistant/todo-suggestions`, and `POST /assistant/project-plan` all keep their existing per-run dispatch (synchronous in-request, or `@Async` immediately after commit). | Anthropic's Batch API targets non-interactive workloads and completes "within 24 hours" (typically much faster, but not latency-bounded) — exactly right for a nightly/weekly sweep nobody is watching, and exactly wrong for any call backing a UI spinner. Journal reflection has no scheduled trigger at all (D4), so there's nothing of this kind for the migration to touch. |
| D18 | **SDK support confirmed, not an unverified spike** (contrast M8 D15's Resilience4j uncertainty). Verified directly against the pinned `anthropic-java-core:2.34.0` jar already in this repo's Gradle cache: a non-beta `com.anthropic.services.blocking.messages.BatchService` (reached via `sdk.messages().batches()`) with `create(BatchCreateParams)`, `retrieve(String id)`, `resultsStreaming(String id)`; non-beta model classes `MessageBatch` (`id()`, `processingStatus()` — enum `IN_PROGRESS`/`CANCELING`/`ENDED`), `MessageBatchIndividualResponse` (`customId()` + `result()`), `MessageBatchResult` (sealed `succeeded()`/`errored()`/`canceled()`/`expired()`), and `MessageBatchSucceededResult.message()` returning the same `com.anthropic.models.messages.Message` type `generateSummary` already parses for its markdown/usage. No dependency bump; no beta header. | A past plan (M8) shipped a load-bearing dependency with an explicitly-flagged "unverified at plan time" risk (D15/Q1 there) that happened to resolve cleanly — this time the equivalent check was done up front, during planning, instead of being deferred to an implementation-time spike. |
| D19 | **`custom_id` = `assistant_run.id.toString()`.** Each batch request is one already-created run's rendered context (same `SummaryContextBuilder.Context` M9 builds today), submitted as a `BatchCreateParams.Request` instead of an immediate `sdk.messages().create(...)` call. The batch API's own `customId`-keyed results map straight back onto `assistant_run.id` — no separate correlation table needed. | `MessageBatchIndividualResponse.customId()` is exactly the primitive the SDK gives for this; `assistant_run.id` is already a stable UUID created before submission, so reusing it as the batch's `custom_id` costs nothing and needs no new identifier scheme. |
| D20 | **One Anthropic batch per scheduler firing, not one batch per run and not one shared batch across both schedules.** `SummaryScheduler.weekly()`/`.monthly()` each still loop over every opted-into-`executionSummaries` user (unchanged), but collect every user's request into a single `BatchCreateParams` with N `Request`s, submitted once per firing. | Submitting one request per user individually would defeat the point of batching (no cost/overhead win); merging the weekly and monthly schedules into one batch would couple two triggers that already fire independently (different days) for no benefit — there's no scenario where they coincide in the same loop iteration to merge. |
| D21 | **New table `assistant_batch`, plus a nullable `assistant_run.batch_id` FK — the first new migration since `V008` (M8.5).** `assistant_run`'s existing `PENDING → RUNNING → SUCCEEDED \| FAILED` state machine has no way to distinguish "an `@Async` method is actively calling Anthropic right now" from "submitted as part of a batch, waiting on Anthropic's own timeline" — both look identical today (`RUNNING`). `assistant_batch` tracks the Anthropic-side batch id, which kind it covers, its own processing status, and timestamps, independently of any one run; `batch_id` is null for every synchronously/`@Async`-dispatched run (everything except a scheduled-sweep summary) and sets exactly one `assistant_batch` row per scheduler firing (D20). See §3.2 for the DDL. | The run-level state machine stays unchanged and still means the same thing to every other caller (`GET /assistant/runs/{id}` doesn't care); the new table is additive, scoped to the one dispatch path that needs the extra bookkeeping, following the same "don't touch what works" instinct M9 D1 used when it chose zero schema change over bending an existing column to a new meaning. |
| D22 | **A new fixed-delay `@Scheduled` poller, `SummaryBatchPollingScheduler`, separate from `SummaryScheduler`** (`kairon.assistant.summary.batch-poll-interval`, default `PT15M`). It checks every `assistant_batch` row with `status <> ENDED` via `AnthropicClient.pollBatch(...)`; once a batch's `processingStatus` is `ENDED`, it streams results via `AnthropicClient.retrieveBatchResults(...)` and writes each one back onto its matching `assistant_run` (`succeed`/`fail`), then marks the `assistant_batch` row `ENDED`. Kept as its own small class rather than folded into `SummaryScheduler` (different trigger shape — fixed delay, not cron — and a genuinely different responsibility: writing results back, not creating runs) and **not** built as a kind-generic "batch poller" (only `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` are ever batched today — D17). | Matches this codebase's "no premature abstraction" bias (`CLAUDE.md`) — a generic multi-kind batch-polling framework would be speculative when exactly one pair of kinds uses batching; a small, purpose-built poller is simpler to read and to reason about. |
| D23 | **The deterministic stats table (M9 D16) is rendered and stored at *submission* time, not recomputed at *poll* time.** `SummaryBatchDispatcher` (new `@Service`, used only by `SummaryScheduler`) builds each run's `SummaryContextBuilder.Context` before submitting the batch — exactly like `SummaryGenerationService.generate` does today — calls `context.renderStatsTable()` immediately, and stores that rendered markdown string under a new key in the same `input_snapshot` jsonb map `run.start(...)` already persists (no new column). The polling job later reads it straight back out of `run.getInputSnapshot()` and concatenates it after the batch result's narrative — it never re-queries `todo`/`projects` at poll time. | Re-deriving the stats table at poll time (potentially hours after submission) would risk the table reflecting *newer* data than what the model actually narrated, breaking D16's "the table and the narrative describe the exact same snapshot" guarantee in the one dispatch path where submission and completion are furthest apart in time. Storing the already-rendered string is strictly more correct, not just more convenient — the input snapshot is already the audit record of exactly what was sent, so this is a natural extension of what it's for. |
| D24 | **Per-result failure handling mirrors M9 D10's per-user tolerance, now at the batch-result level.** An `errored`/`canceled`/`expired` result for one `customId` fails only that run (`run.fail(...)`, a short sanitized detail); every other result in the same batch is still written normally. Budget accounting is unchanged — each result still carries its own `inputTokens`/`outputTokens` (from `MessageBatchSucceededResult.message().usage()`), written onto its run exactly like a synchronous call's result, so the existing monthly-budget-sum query doesn't need to know or care whether a run's tokens arrived immediately or hours later via a batch. | Same "one bad edge doesn't block the others" tolerance this codebase has applied consistently since M8.5 D7, now extended to the unit a batch actually fails at (one item, not one user directly — though in practice here it's the same thing, since there's exactly one request per user per batch). |

---

## 3. Data model

### 3.1 Journal reflection — no migration

For reference, the columns/preference keys this feature finally writes to or
reads (all already exist):

| Column / key | Written/read as |
| --- | --- |
| `assistant_run.kind` | `JOURNAL_REFLECTION` |
| `assistant_run.period_start` / `period_end` | the resolved Monday–Sunday week |
| `assistant_run.output_markdown` | the model's reflective narrative (D11 — no appended table) |
| `assistant_run.input_snapshot` | the rendered system+user content — same audit contract as every other kind |
| `app_user.preferences.assistant.journalReflection.enabled` | read by the new availability check, written by `PATCH /me` (existing, unchanged endpoint) |
| `app_user.preferences.assistant.tone` | read for the first time, by `JournalReflectionContextBuilder` only |

### 3.2 Batch migration — new migration `V009__assistant_batch.sql`

```sql
CREATE TABLE assistant_batch (
    id                  UUID PRIMARY KEY,
    anthropic_batch_id  VARCHAR(100) NOT NULL UNIQUE,
    kind                VARCHAR(30) NOT NULL
                          CHECK (kind IN ('WEEKLY_SUMMARY', 'MONTHLY_SUMMARY')),
    status              VARCHAR(20) NOT NULL
                          CHECK (status IN ('IN_PROGRESS', 'CANCELING', 'ENDED')),
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE assistant_run ADD COLUMN batch_id UUID REFERENCES assistant_batch(id);
CREATE INDEX idx_assistant_run_batch ON assistant_run(batch_id) WHERE batch_id IS NOT NULL;
```

`batch_id` is null for every run dispatched synchronously or via a single
`@Async` call (every kind except a scheduled-sweep `WEEKLY_SUMMARY`/
`MONTHLY_SUMMARY` run, D17/D21). No soft delete on `assistant_batch`, matching
every other `assistant_*` table's hard-delete-only convention
(`DATA_MODEL.md`) — it carries no user-identifying content of its own, only
bookkeeping.

---

## 4. Backend

### 4.1 Port extensions

None (D7). `JournalApi.range`, `TodoApi.periodStats`, `ProjectsApi
.projectPeriodStats` are called unchanged.

### 4.2 Package layout additions — `com.kairon.assistant`

```
assistant
├── app/  JournalReflectionContextBuilder, ReflectionGenerationService
│         (the @Async runner, mirrors SummaryGenerationService)
│         — AssistantRunService gains requestJournalReflection and the
│         generalized requireNoRunInFlight(Set<AssistantRunKind>) (D5)
├── llm/  (AnthropicClient gains generateReflection; no new payload records
│         — plain String markdown, same shape as generateSummary)
└── web/  (AssistantRunController gains POST /assistant/journal-reflection;
          AssistantDtos gains the request DTO. GET /assistant/runs/{id} and
          GET /assistant/runs?kind= are already generic — no change needed
          beyond accepting the new kind value, which they already do.)
```

### 4.3 `AnthropicClient` addition

```java
ReflectionResult generateReflection(ReflectionRequest request);

record ReflectionRequest(String systemPrompt, String userContent, String model, String effort) {
}

record ReflectionResult(String markdown, String model, long inputTokens, long outputTokens) {
}
```

`AnthropicClientImpl.generateReflection` — identical shape to M9's
`generateSummary` (`@CircuitBreaker(name = "anthropic")`, a plain
`sdk.messages().create(...)` call, no `.outputConfig(...)`, the same
empty-response guard).

### 4.4 `AssistantRunService` additions

```java
@Transactional
public AssistantRunView requestJournalReflection(UserId userId, LocalDate weekOf) {
    requireJournalReflectionAvailable(userId);     // mirrors the other three requireXAvailable checks
    requireWithinBudget(userId);                   // same shared monthly pool as every other kind
    requireNoRunInFlight(userId, Set.of(AssistantRunKind.JOURNAL_REFLECTION)); // D5

    SummaryPeriod.Range week = SummaryPeriod.WEEK.resolve(weekOf); // D6 — date math only
    if (journal.range(userId, week.start(), week.end()).isEmpty()) {
        log.warn("Journal reflection refused: no entries userId={} week=[{},{}]",
                userId.value(), week.start(), week.end());
        throw ApiException.unprocessable("No journal entries for that week — nothing to reflect on."); // D8
    }

    String model = resolveModel(accounts.assistantPreferences(userId)); // D12 — no kind-specific default
    AssistantRun run = AssistantRun.pending(userId.value(), AssistantRunKind.JOURNAL_REFLECTION,
            week.start(), week.end(), model);
    runs.save(run);
    log.info("Queued JOURNAL_REFLECTION run {} userId={} week=[{},{}]",
            run.getId(), userId.value(), week.start(), week.end());
    return AssistantMapper.toRunView(run, List.of());
}
```

`requireNoRunInFlight` (M9, generalized per D5):

```java
private void requireNoRunInFlight(UserId userId, Set<AssistantRunKind> kinds) {
    boolean inFlight = runs.existsByUserIdAndKindInAndStatusIn(userId.value(), kinds,
            List.of(AssistantRunStatus.PENDING, AssistantRunStatus.RUNNING));
    if (inFlight) {
        log.warn("Run rejected: a {} run is already in flight userId={}", kinds, userId.value());
        throw ApiException.conflict("A run of this kind is already in progress. Wait for it to finish.");
    }
}
```

(The existing summary call site becomes
`requireNoRunInFlight(userId, Set.of(WEEKLY_SUMMARY, MONTHLY_SUMMARY))` — same
behavior, no functional change.)

`ReflectionGenerationService` — a separate `@Service`, `@Async` on its one
public method, identical control flow to M9's `SummaryGenerationService
.generate`:

```java
@Async
@Transactional
public void generate(UUID runId) {
    AssistantRun run = runs.findById(runId).orElseThrow();
    JournalReflectionContextBuilder.Context context = contextBuilder.build(
            new UserId(run.getUserId()), run.getPeriodStart(), run.getPeriodEnd());
    run.start(context.inputSnapshot());
    runs.save(run);
    try {
        AnthropicClient.ReflectionResult result = anthropicClient.generateReflection(
                new AnthropicClient.ReflectionRequest(context.systemPrompt(), context.userContent(),
                        run.getModel(), properties.journalReflection().effort()));
        run.succeed(result.markdown(), result.inputTokens(), result.outputTokens()); // D11 — no appended table
        log.info("Journal reflection run {} succeeded userId={} tokens={}+{}",
                run.getId(), run.getUserId(), result.inputTokens(), result.outputTokens());
    } catch (AssistantUpstreamException e) {
        run.fail(e.sanitizedDetail());
        log.warn("Journal reflection run {} failed userId={} retryable={}",
                run.getId(), run.getUserId(), e.retryable());
    } finally {
        runs.save(run);
    }
}
```

`requireJournalReflectionAvailable` follows the existing three-check chain
(`requireExecutionSummariesAvailable`'s precedent): master switch,
non-blank API key, then `prefs.journalReflectionEnabled()` — the first
failing check throws `ApiException.forbidden(...)`.

### 4.5 `JournalReflectionContextBuilder`

Three data sources (D10):

1. **`JournalApi.range(userId, weekStart, weekEnd)`** — every entry, in
   full, ordered by day then position, capped at
   `kairon.assistant.journal-reflection.max-entries` (default 30, D16).
2. **`TodoApi.periodStats(userId, weekStart, weekEnd)`** — one sentence:
   created/completed/rolled-over counts and completion rate (reusing M9's
   `PeriodStats.completionRate()`).
3. **`ProjectsApi.projectPeriodStats(userId, weekStart, weekEnd)`** — top
   `max-projects` (default 5) by `tasksCompleted`, named only (project name
   + count).

**System prompt** (three fixed variants, selected by `tone` — shown here is
the `balanced` variant; `encouraging` and `direct` vary only in the tone
paragraph marked below):

```
You are Kairon's journal-reflection assistant. You are given one week's
journal entries in full, plus a little context about what else happened that
week (todos and project work). Reflect on the week back to the user — you are
not summarizing it and not giving productivity advice; you are helping them
see their own week more clearly.

Base everything only on the journal entries and context given below. Never
invent an event, feeling, or detail that isn't in the text. If an entry is
short or sparse, don't pad your reflection to compensate — a quiet week can
get a short, honest reflection.

[TONE PARAGRAPH — balanced shown here:]
Write in a warm but plain voice — acknowledge what's genuinely going well and
name what's genuinely difficult, without overstating either. Avoid both empty
positivity and bluntness for its own sake.

Structure your response as markdown with these sections:
- A short, specific opening line naming the week's overall shape — don't
  open with a generic "this week you..." if the entries support something
  more specific.
- "Patterns" — anything that recurs across two or more entries (a recurring
  worry, a repeated subject, a mood that shows up more than once). Reference
  the mood values you're given directly when they support a point; don't
  invent a trend from entries that don't actually show one.
- "Worth noticing" — one or two things the entries suggest the user might not
  have fully named themselves — a blind spot, a tension, something glossed
  over. Frame as an observation or a question, never a directive.
- "A question to sit with" — exactly one open-ended question grounded in
  something specific from this week's entries, not a generic journaling
  prompt.

If the todo/project context given below genuinely connects to something in
the journal entries (e.g. an entry mentions a project also shown as having
completed work), you may mention the connection — but never force one, and
never treat the todo/project numbers as the main subject. The journal
entries are the subject; the numbers are only grounding.

Keep the whole response under about 350 words.
```

Tone paragraph variants (the only text that differs between the three
system-prompt variants — everything else above is shared):

- **`encouraging`**: "Write with warmth and genuine encouragement — notice
  effort and progress even where the outcome fell short, and frame
  difficulties gently. Stay honest; encouragement here means generous
  framing of what's true, never inventing progress that didn't happen."
- **`direct`**: "Write plainly and directly — name what the entries show
  without softening it, including anything uncomfortable. Skip
  reassurance and hedging; trust the user to sit with a direct observation."

**User content** (rendered per request; exact wording confirmed at
implementation time, but every one of these facts is present):

```
Weekly journal reflection for Sep 21–27, 2026.

This week you completed 11 of 12 todo items on your docket (92%), with 3
rolled over from an earlier day. Project work with completed tasks this
week: Kitchen remodel (4), Website redesign (2).

Journal entries this week:

[Sep 21] Mood: 4/5
<entry content>

[Sep 23] "Tiler call" — Mood: 2/5
<entry content>

[Sep 23] Mood: 3/5
<entry content>

[Sep 26] Mood: 3/5
<entry content>

[If periodStats shows zero todo activity and projectPeriodStats is empty,
the grounding paragraph is replaced with "No todo or project activity is
recorded for this week." — never an empty or fabricated grounding sentence.]
```

`context.inputSnapshot()` persists this exact rendered content (same audit
contract as every other kind) — a user reviewing a past reflection run sees
precisely what left the instance, which for this feature specifically
includes their own journal text verbatim.

### 4.6 `AssistantProperties.JournalReflection`

A new nested config record, following the `Summary`/`ProjectPlan` precedent:

```java
public record JournalReflection(int maxEntries, int maxProjects, String effort) {

    public JournalReflection {
        if (maxEntries <= 0) {
            maxEntries = 30; // D16 — pathological-case guard, not an expected-case limit
        }
        if (maxProjects <= 0) {
            maxProjects = 5;
        }
        if (effort == null || effort.isBlank()) {
            effort = "HIGH"; // a reflective-synthesis task, same rationale as M9's summary.effort
        }
    }
}
```

No cron/zone fields (D4 — on-demand only, no scheduler for this kind).

### 4.7 Web layer

**`AssistantRunController`** gains:

```java
@PostMapping("/assistant/journal-reflection")
@ResponseStatus(HttpStatus.CREATED)
public AssistantRunView requestJournalReflection(@CurrentUser UserId userId,
        @Valid @RequestBody JournalReflectionRequest req) {
    log.debug("POST /assistant/journal-reflection userId={} weekOf={}", userId.value(), req.weekOf());
    AssistantRunView view = assistantRuns.requestJournalReflection(userId, req.weekOf());
    reflectionDispatcher.generate(view.id()); // dispatched only after the request method's transaction committed
    return view;
}
```

`GET /assistant/runs/{id}` and `GET /assistant/runs?kind=` (existing,
unchanged) already handle `JOURNAL_REFLECTION` — neither has a kind-specific
branch to update.

### 4.8 ArchUnit

No new rule — M8's `onlyAssistantImportsTheAnthropicSdk` and
`assistantInternalsArePrivate` already cover every new class here.

### 4.9 Authorization & errors

`POST /assistant/journal-reflection`: `403` disabled/opted-out/budget
(existing chain), `409` a reflection run already in flight (D5), `422` an
empty week (D8, new `ApiException.unprocessable` factory), `502`/`503`
upstream (existing chain, unchanged).

### 4.10 Batch migration — package layout additions

```
assistant
├── domain/  AssistantBatch, AssistantBatchStatus (IN_PROGRESS|CANCELING|ENDED
│             — mirrors MessageBatch.ProcessingStatus.Known 1:1, D18)
├── repo/    AssistantBatchRepository (findByStatusNot(ENDED)); AssistantRunRepository
│             gains findByBatchId(UUID)
├── app/     SummaryBatchDispatcher (new @Service, used only by SummaryScheduler —
│             builds context + stores the stats table + submits one batch, D19/D20/D23),
│             SummaryBatchPollingScheduler (new, fixed-delay @Scheduled, D22);
│             SummaryScheduler.weekly()/.monthly() refactored to call
│             SummaryBatchDispatcher instead of dispatching per-user
└── llm/     AnthropicClient gains submitBatch/pollBatch/retrieveBatchResults;
             AnthropicClientImpl wraps sdk.messages().batches() (D18);
             FakeAnthropicClient (M8's e2e stub) gains a same-tick-"ended" fake
```

### 4.11 `AnthropicClient` batch additions

```java
BatchHandle submitBatch(List<BatchRequestItem> requests);

record BatchRequestItem(String customId, String systemPrompt, String userContent, String model) {
}

record BatchHandle(String anthropicBatchId) {
}

BatchPollResult pollBatch(String anthropicBatchId);

record BatchPollResult(boolean ended, String status) { // status: IN_PROGRESS | CANCELING | ENDED
}

List<BatchResultItem> retrieveBatchResults(String anthropicBatchId);

record BatchResultItem(
        String customId, boolean succeeded,
        String markdown, long inputTokens, long outputTokens, String errorDetail) {
}
```

`AnthropicClientImpl.submitBatch` builds one `BatchCreateParams` with one
`BatchCreateParams.Request` per item (`.customId(item.customId())`, a
`BatchCreateParams.Request.Params` built the same way `MessageCreateParams`
is today — `.model(...)`, `.maxTokens(...)`, `.system(...)`,
`.addUserMessage(...)`), calls `sdk.messages().batches().create(params)`, and
returns the resulting `MessageBatch.id()`. `pollBatch` calls
`sdk.messages().batches().retrieve(id)` and maps `processingStatus()` to
`BatchPollResult`. `retrieveBatchResults` calls
`sdk.messages().batches().resultsStreaming(id)`, and for each
`MessageBatchIndividualResponse` maps `result()`'s sealed type: `succeeded()`
→ reads the wrapped `Message`'s first text block + `usage()` (the exact same
extraction `generateSummary` already does); `errored()`/`canceled()`/
`expired()` → `succeeded = false` with a short sanitized `errorDetail`, same
spirit as `AssistantUpstreamException.sanitizedDetail()` elsewhere in this
class. Same `@CircuitBreaker(name = "anthropic")` as every other method here.

### 4.12 `SummaryBatchDispatcher` (D19/D20/D23)

```java
@Service
class SummaryBatchDispatcher {

    @Transactional
    void submitBatch(AssistantRunKind kind, List<UUID> runIds) {
        List<AnthropicClient.BatchRequestItem> items = new ArrayList<>();
        List<AssistantRun> staged = new ArrayList<>();
        for (UUID runId : runIds) {
            AssistantRun run = runs.findById(runId).orElseThrow();
            SummaryContextBuilder.Context context = contextBuilder.build(
                    new UserId(run.getUserId()), run.getKind(), run.getPeriodStart(), run.getPeriodEnd());
            Map<String, Object> snapshot = new HashMap<>(context.inputSnapshot());
            snapshot.put("statsTable", context.renderStatsTable()); // D23 — rendered now, read back at poll time
            run.start(snapshot);
            items.add(new AnthropicClient.BatchRequestItem(
                    run.getId().toString(), context.systemPrompt(), context.userContent(), run.getModel()));
            staged.add(run);
        }
        if (items.isEmpty()) {
            return;
        }
        AnthropicClient.BatchHandle handle = anthropicClient.submitBatch(items);
        AssistantBatch batch = AssistantBatch.pending(handle.anthropicBatchId(), kind);
        batches.save(batch);
        for (AssistantRun run : staged) {
            run.setBatchId(batch.getId());
            runs.save(run);
        }
        log.info("Submitted {} batch {} ({} runs)", kind, handle.anthropicBatchId(), staged.size());
    }
}
```

`SummaryScheduler.run(period)` changes from "loop → `requestSummary` →
`dispatcher.generate(id)` per user" to "loop → `requestSummary` per user,
collecting ids → one `SummaryBatchDispatcher.submitBatch(period.kind(),
ids)` call." On-demand `requestSummary` itself is untouched.

### 4.13 `SummaryBatchPollingScheduler` (D22/D24)

```java
@Component
class SummaryBatchPollingScheduler {

    @Scheduled(fixedDelayString = "${kairon.assistant.summary.batch-poll-interval:PT15M}")
    void poll() {
        for (AssistantBatch batch : batches.findByStatusNot(AssistantBatchStatus.ENDED)) {
            AnthropicClient.BatchPollResult poll = anthropicClient.pollBatch(batch.getAnthropicBatchId());
            if (!poll.ended()) {
                batch.updateStatus(poll.status());
                batches.save(batch);
                continue;
            }
            for (AnthropicClient.BatchResultItem item : anthropicClient.retrieveBatchResults(
                    batch.getAnthropicBatchId())) {
                AssistantRun run = runs.findById(UUID.fromString(item.customId())).orElse(null);
                if (run == null) {
                    log.warn("Batch {} result for unknown run {}", batch.getAnthropicBatchId(), item.customId());
                    continue;
                }
                if (item.succeeded()) {
                    String statsTable = (String) run.getInputSnapshot().get("statsTable"); // D23
                    run.succeed(item.markdown() + "\n\n" + statsTable, item.inputTokens(), item.outputTokens());
                } else {
                    run.fail(item.errorDetail()); // D24 — one bad result doesn't fail the rest of the batch
                }
                runs.save(run);
            }
            batch.end();
            batches.save(batch);
            log.info("Batch {} ended, results written back", batch.getAnthropicBatchId());
        }
    }
}
```

### 4.14 Authorization & errors (batch migration)

No change to any endpoint's contract (D17) — this is an internal dispatch
mechanism swap for the scheduled sweep only. A user polling
`GET /assistant/runs/{id}` for a batch-dispatched run sees exactly the same
`PENDING`/`RUNNING`/`SUCCEEDED`/`FAILED` lifecycle as before; they have no
way to tell, and don't need to, whether their summary was generated
synchronously or via a batch.

---

## 5. API contract

Base path `/api/v1`. Bearer access token on every call.

| Method & path | Body | Response |
| --- | --- | --- |
| `POST /assistant/journal-reflection` | `{ weekOf: date }` | `201` + `AssistantRunView` (**`PENDING`** — poll `GET /assistant/runs/{id}`); `403` disabled/opted-out/budget; `409` a reflection run already in flight; `422` no journal entries that week. |
| `GET /assistant/runs/{id}` | — | (existing, unchanged) `AssistantRunView`, now with `outputMarkdown` populated for `JOURNAL_REFLECTION` runs once `SUCCEEDED`; `404`. |
| `GET /assistant/runs?kind=JOURNAL_REFLECTION&...` | — | (existing, unchanged) `AssistantRunPage`. |

The batch migration (§4.10–4.14) adds **no new or changed endpoint** — it's
an internal dispatch-mechanism change to the existing scheduled sweep.

### `POST /assistant/journal-reflection` response shape (immediately, before completion)

```json
{
  "id": "018f…", "status": "PENDING", "kind": "JOURNAL_REFLECTION",
  "model": "claude-sonnet-5", "periodStart": "2026-09-21", "periodEnd": "2026-09-27",
  "inputTokens": null, "outputTokens": null, "outputMarkdown": null,
  "createdAt": "2026-09-28T09:00:00Z", "suggestions": [], "suggestedProject": null
}
```

---

## 6. Web

### 6.1 Feature folder changes — `web/src/features/assistant/`

```
features/assistant/
├── AssistantSettings.tsx     gains: the journal-reflection checkbox behind a
│                               Dialog-based confirmation step (D13), a tone
│                               Select (encouraging/balanced/direct, D9)
├── SummariesPage.tsx          gains: a third "Generate weekly reflection"
│                               button (shown only when journalReflectionEnabled),
│                               JOURNAL_REFLECTION added to its kind filter/KIND_LABEL
├── SummaryDetail.tsx          gains: a KIND_LABEL entry for JOURNAL_REFLECTION
│                               (rendering is already kind-agnostic otherwise)
└── useAssistantRuns.ts        gains: useRequestJournalReflection (mutation)
```

### 6.2 `AssistantSettings.tsx` — confirmation flow (D13)

The journal-reflection `Checkbox`'s `onCheckedChange` does not call
`mutation.mutate` directly when turning the feature **on**: it opens a
`Dialog` (reusing the existing component, not a new `alert-dialog` package)
with copy specific to this feature —

> Journal reflection sends your journal entries' **full text** for the
> selected week to Anthropic, not just a summary. This is different from
> Kairon's other assistant features, which only send task and project
> details.

— with "Cancel" (closes the dialog, checkbox stays unchecked, no network
call) and "Enable journal reflection" (closes the dialog and calls
`mutation.mutate({ journalReflection: { enabled: true } })`, same
read-modify-write `PATCH /me` pattern every other toggle on this page uses).
Turning the feature **off** calls `mutation.mutate` immediately, same as
today's three checkboxes — no confirmation needed to turn something off.

The tone `Select` sits below the four checkboxes, always visible (consistent
with the existing `Model` select, which is likewise shown regardless of
which features are enabled) — `encouraging` / `balanced` (default) /
`direct`, PATCHing `tone` the same way `modelOverride` already does.

### 6.3 `SummariesPage.tsx` / `SummaryDetail.tsx` changes (D14)

`SUMMARY_KINDS` becomes a function of the current user's preferences —
`JOURNAL_REFLECTION` is included in the `kind` filter and gets its own
"Generate weekly reflection" button only when
`me.preferences.assistant.journalReflection.enabled` is true (same
opted-in-button-visibility pattern `GenerateProjectDialog`'s entry point on
`ProjectListPage` already uses for project generation). The generate call
always targets "this week" (`weekOf = todayInZone("UTC")`, same simplicity
M9's summary buttons already use — no date picker). `KIND_LABEL` in both
files gains `JOURNAL_REFLECTION: "Weekly reflection"`.

### 6.4 Data layer

- `web/src/lib/api/assistant.ts` — `assistantApi.requestJournalReflection
  ({ weekOf })`.
- Types added to `web/src/lib/api/types.ts`: `JournalReflectionRequest`,
  `"JOURNAL_REFLECTION"` added to the `AssistantRunKind` union,
  `journalReflection`/`tone` already present on `AssistantPreferences`
  (M8) get their first real consumer.
- `useRequestJournalReflection()` → `useMutation`, same shape as
  `useRequestSummary`.

**The batch migration makes no web changes at all.** `SummariesPage`/
`SummaryDetail`/the polling hook only ever read `assistant_run.status`/
`outputMarkdown` — they have no idea, and don't need one, how a run was
dispatched on the backend.

---

## 7. Testing

### Backend

| Test | Type | Covers |
| --- | --- | --- |
| `AssistantRunServiceTest` (extend) | plain JUnit 5 + AssertJ, Mockito | disabled/opted-out/budget-exceeded/empty-week all throw before any client call; a successful request queues `PENDING`; `requireNoRunInFlight` generalization still rejects two concurrent `WEEKLY_SUMMARY`/`MONTHLY_SUMMARY` runs but **not** a reflection run alongside an in-flight summary run (D5). |
| `JournalReflectionContextBuilderTest` | plain JUnit 5 + AssertJ, Mockito for the three ports | assembles the expected system/user content from mocked port responses; all three tone variants render the right paragraph; respects `max-entries`/`max-projects` truncation (D16). |
| `ReflectionGenerationServiceTest` | plain JUnit 5 + AssertJ, Mockito | a successful call persists `SUCCEEDED` with the model's markdown **unmodified** (D11 — no appended table, unlike the equivalent M9 test); an `AssistantUpstreamException` persists `FAILED` with a sanitized error. |
| `AssistantRunControllerTest` (extend) | `@WebMvcTest` | `POST /assistant/journal-reflection` happy path, 403/409/422 passthrough. |
| `AssistantFlowIntegrationTest` (extend) | `@SpringBootTest` + Testcontainers, `AnthropicClient` faked | enable the feature via `PATCH /me`, seed a week of journal entries, `POST /assistant/journal-reflection` → `PENDING` then polled to `SUCCEEDED` with `outputMarkdown` populated; a week with zero entries returns `422` and creates no run; a second reflection request while one is in flight returns `409`, while a summary request at the same time still succeeds (D5). |
| `SummaryBatchDispatcherTest` | plain JUnit 5 + AssertJ, Mockito | submits one `BatchCreateParams`-shaped request per queued run; stores each run's rendered stats table under `input_snapshot["statsTable"]` before submission (D23); sets `batch_id` on every staged run; an empty run-id list submits nothing. |
| `SummaryBatchPollingSchedulerTest` | plain JUnit 5 + AssertJ, Mockito | a non-ended batch only updates its stored status; an ended batch writes `SUCCEEDED` (narrative + the stored stats table, verbatim) for a succeeded result and `FAILED` for an errored/canceled/expired one in the **same** batch, without one affecting the other (D24); the batch row itself flips to `ENDED` only after every result is written. |
| `AnthropicClientImplTest` (extend) | plain JUnit 5 + a stubbed SDK client | `submitBatch`/`pollBatch`/`retrieveBatchResults` map correctly onto `MessageBatch`/`MessageBatchIndividualResponse`/the sealed `MessageBatchResult` (D18) — same style as M8's existing structured-output mapping tests. |
| `SummarySchedulerTest` (extend) | plain JUnit 5 + AssertJ, Mockito | `weekly()`/`monthly()` now call `SummaryBatchDispatcher.submitBatch` once per firing with every opted-in user's run id, not `SummaryGenerationService.generate` per user; a user failing `requestSummary` (budget/opted-out) is excluded from the batch without aborting the others (D24's per-user tolerance, preserved from M9 D10). |
| `AssistantFlowIntegrationTest` (extend, batch) | `@SpringBootTest` + Testcontainers, `FakeAnthropicClient`'s batch methods resolving "ended" on first poll | the scheduled sweep for two opted-in users produces one `AssistantBatch` row and, after one `poll()` tick, both users' runs reach `SUCCEEDED` with their stats table intact. |

### Web

| Test | Covers |
| --- | --- |
| MSW handlers | add `POST /api/v1/assistant/journal-reflection`; extend the `GET /assistant/runs` handler to accept `kind=JOURNAL_REFLECTION`. |
| `AssistantSettings.test.tsx` (extend) | checking the journal-reflection box opens the confirmation dialog and does **not** PATCH until "Enable" is clicked; "Cancel" leaves the checkbox unchecked and preferences untouched; the tone select PATCHes `tone` with other keys untouched (read-modify-write regression guard, same pattern as the existing toggle tests). |
| `SummariesPage.test.tsx` (extend) | the reflection button is absent when `journalReflectionEnabled` is false and present (and wired to the right mutation) when true; a `JOURNAL_REFLECTION` row renders with the right `KIND_LABEL`. |
| `assistant-journal-reflection.spec.ts` (Playwright, new) | enable the feature in Settings (confirm the dialog), seed a journal entry via the Journal UI, open Summaries → "Generate weekly reflection" → poll to completion → markdown renders. Uses the same `FakeAnthropicClient` e2e stub M8 already wired (§9 Q3 of that plan). |

---

## 8. Task breakdown (suggested order)

1. `ApiException.unprocessable` (422 factory, D8) — small, reusable beyond
   this feature.
2. `AssistantRunService`: generalize `requireNoRunInFlight` to
   `Set<AssistantRunKind>` (D5), update the existing summary call site,
   add `requireJournalReflectionAvailable` + `requestJournalReflection`
   (D6/D8/D12) + tests.
3. `AssistantProperties.JournalReflection` config record (D16).
4. `AnthropicClient.generateReflection` + `AnthropicClientImpl` method
   + test (mirrors M9's `generateSummary` closely).
5. `JournalReflectionContextBuilder` (D9/D10/D11) + test — the three tone
   variants are the main thing worth testing carefully here.
6. `ReflectionGenerationService` (`@Async`, D3) + test.
7. `AssistantRunController` + `AssistantDtos` additions + controller test.
8. `AssistantFlowIntegrationTest` additions.
9. Rate limiter: add `/api/v1/assistant/journal-reflection` to the literal
   `paths` list (D15) — easy to forget, flagged a third time for a reason.
10. Web: `assistant.ts`/`types.ts`/`useAssistantRuns.ts` additions, MSW
    handlers.
11. Web: `AssistantSettings.tsx` — confirmation dialog (D13) + tone select
    (D9) + tests.
12. Web: `SummariesPage.tsx`/`SummaryDetail.tsx` third-kind wiring (D14)
    + tests.
13. Web tests + Playwright happy path.
14. Docs: tick M10's `ROADMAP.md` boxes; `CLAUDE.md` "Current state" —
    extend the `assistant` module bullet (new classes, `AssistantProperties
    .JournalReflection`), extend the web bullet (`SummariesPage`/
    `SummaryDetail`/`AssistantSettings` changes); mark this plan `Accepted`.

### Batch migration tasks (independent of 1–14 above; can be built in either order)

15. `V009__assistant_batch.sql`; `AssistantBatch`/`AssistantBatchStatus`
    domain classes; `AssistantBatchRepository`;
    `AssistantRunRepository.findByBatchId` (D21).
16. `AnthropicClient.submitBatch`/`pollBatch`/`retrieveBatchResults` +
    `AnthropicClientImpl` + `AnthropicClientImplTest` (D18).
17. `SummaryBatchDispatcher` (D19/D20/D23) + test.
18. Refactor `SummaryScheduler.weekly()`/`.monthly()` to call
    `SummaryBatchDispatcher` instead of per-user dispatch + updated
    `SummarySchedulerTest`.
19. `SummaryBatchPollingScheduler` (D22/D24) + test; new config key
    `kairon.assistant.summary.batch-poll-interval` (default `PT15M`).
20. `FakeAnthropicClient` (M8's e2e stub) gains batch methods that resolve
    `ENDED` immediately, so `task e2e` doesn't need real wall-clock delay.
21. `AssistantFlowIntegrationTest` additions covering the batch path
    end-to-end.
22. Docs: `ROADMAP.md` backlog — remove "Batch API for scheduled summaries
    (50% cost)" (resolved here, not deferred); `CLAUDE.md` "Current state" —
    extend the `assistant` module bullet with `AssistantBatch`/
    `SummaryBatchDispatcher`/`SummaryBatchPollingScheduler`; migration list
    extended with `V009`.

---

## 9. Open questions / risks

| # | Question | Notes |
| --- | --- | --- |
| Q1 | D10's grounding sizes (`max-entries: 30`, `max-projects: 5`) and the three tone-paragraph wordings are first-draft guesses, not measured against real reflections. | Same posture as every prior milestone's prompt-content guesses (M8 Q2, M9 §9) — shipped as config defaults, tuned once real usage exists. |
| Q2 | Should a `FAILED` or stuck `RUNNING` reflection run be retryable from the Summaries screen UI (a "Retry" button), rather than only via a fresh "Generate" click that creates a new run? | Out of scope for this plan — matches M9's own deferral of job-recovery infrastructure (M9 "Out" section) and the project's general "no retry/resume machinery exists yet" posture. A fresh generate click already works as the retry path. |
| Q3 | D14 accepts "Summaries" as the nav/page label even though it now also produces reflections — is that actually confusing once it's live? | Flagged explicitly in D14's rationale as a revisit-later call, not a settled non-issue. |
| Q4 | Anthropic's real per-batch request-count/size limits aren't checked against this instance's actual user count — at family/friends scale this is almost certainly a non-issue, but it's an assumption, not a verified bound. | Deferred (D17's "Out" note) — chunking a firing's requests across multiple batches is the fix if this instance's user count ever approaches whatever that limit turns out to be; not worth building speculatively. |
| Q5 | `batch-poll-interval`'s default (`PT15M`) is a first guess, not tuned against how quickly Anthropic batches actually tend to end in practice. | Shipped as a config default, same posture as every other first-guess timing/sizing value in this module (Q1 above, M9's own §9). |

---

## 10. Doc updates this milestone will produce

- `ROADMAP.md` — M10 checkboxes ticked; link to this file added (matching
  M8/M9's own "See `milestones/M10-journal-reflection.md`" pattern); the
  backlog's "Batch API for scheduled summaries (50% cost)" line removed
  (resolved here, not deferred further).
- `DESIGN.md` §13 — status line updated to "M8, M8.5, M9, and M10
  implemented."
- `DATA_MODEL.md` — gains `assistant_batch` (new table, §3.2) and
  `assistant_run.batch_id`; journal reflection itself needs no change
  (D1/D2 — nothing there isn't already specified).
- `CLAUDE.md` "Current state" — `assistant` module bullet extended with the
  new journal-reflection classes (§4.2) **and** the batch-migration classes
  (`AssistantBatch`/`AssistantBatchStatus`/`SummaryBatchDispatcher`/
  `SummaryBatchPollingScheduler`, §4.10–4.13); migration list extended with
  `V009`; web bullet extended with the Summaries-screen changes; "Next
  milestone" moves to whatever follows M10 in `ROADMAP.md`.
- This file — `Status: Draft` → `Accepted` once reviewed, then
  `Accepted — implemented` once built, following M8/M9's own convention.
