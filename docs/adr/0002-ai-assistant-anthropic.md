# ADR 0002 — AI assistant via the Anthropic API

- Status: **Accepted**
- Date: 2026-09-09
- Deciders: project owner
- Relates to: [0001](0001-architecture-and-stack.md)

## Context

Kairon (see [0001](0001-architecture-and-stack.md)) is a single-deployable
personal productivity tool where the backend is the source of truth and all data
lives in the owner's PostgreSQL. The owner wants optional AI help:

1. suggest a todo list from active/ongoing projects, recent todos, and the
   journal;
2. weekly / monthly summaries of how well projects and todos are being executed,
   with efficiency suggestions;
3. a weekly reflection on the journal to aid self-reflection — **delivered
   last**, after the other features have proven the integration.

Kairon is multi-user (family/friends share one instance) and is maintained by
one developer. The design bias is the simplest thing that works, with heavier
choices justified.

## Decision

### A contained `assistant` module, not a service

A new package-by-feature module `assistant` (`com.kairon.assistant`), peer to
`identity` / `todo` / `journal` / `projects` / `planning`. It reads the other
modules only through their public `api` packages, owns its tables
(`assistant_run`, `assistant_suggested_task`) and Flyway migration `V007`, and
holds the single `assistant.llm.AnthropicClient` wrapper over the official
**Anthropic Java SDK** (`com.anthropic:anthropic-java`). An **ArchUnit** rule
asserts that no other module imports the SDK. All calls are server-side.

### One instance key, per-user / per-feature opt-in

A single `ANTHROPIC_API_KEY` in the Kubernetes `Secret` serves all users; the
operator pays. Each of the three features is opted into **independently** per
user in `app_user.preferences`, defaulting off; the journal-reflection opt-in is
deliberately separate. `kairon.assistant.enabled` is a master switch and the
feature is inert with no key present. A **per-user monthly token budget** caps
shared spend. Bring-your-own-key per user is backlog.

### Suggest-only

The assistant never writes a `todo_item`, `journal_entry`, or `project`. It
proposes; the user accepts. Accepting a suggested task creates the `todo_item`
as an explicit action. This matches 0001's "the user stays in control" stance
for scheduling.

### Persisted runs

Every call is an `assistant_run` (`PENDING → RUNNING → SUCCEEDED | FAILED`) that
stores the `input_snapshot` (what was sent), `output_markdown`, `model`, and
token counts. The client polls the run. Summaries and reflection run as
background jobs; a `@Scheduled` job auto-generates summaries for opted-in users
(weekly / monthly). Deleting a run purges its stored excerpts.

### Model and prompt choices

Default `claude-sonnet-5`, overridable per instance and per user
(`claude-opus-5` for deeper reflection, `claude-haiku-4-5` for cheap steps).
**Prompt caching** on stable prefixes. **Structured outputs** for todo
suggestions. For summaries, the numbers are computed in SQL first and the model
only narrates and interprets them.

### Privacy

For opted-in users the relevant content — and, for reflection, full journal
text — is sent to the Anthropic API; this is the only path off the instance.
The UI shows a per-feature data-sharing notice, the `input_snapshot` is
user-auditable, and Anthropic's default 30-day API retention (zero retention for
qualifying orgs) is documented in `deploy/`.

## Alternatives considered

| Area | Rejected option | Why |
| --- | --- | --- |
| Placement | A separate AI microservice | No scaling or team reason; adds a deployable and an operational surface for a personal tool. The module seam already exists if it is ever needed. |
| Retrieval | Vector DB / RAG framework (pgvector, LangChain4j retrieval) | At personal scale a week of journal entries plus a handful of projects fit in one prompt. Revisit only if long-horizon history retrieval becomes a feature. |
| Model host | Local / self-hosted LLM (Ollama) | Weaker results for reflection, and another stateful workload to run. Kept in the backlog for privacy-maximising self-hosters. |
| Key model | Bring-your-own-key per user from day one | Needs encrypted per-user key storage, a validation flow, and settings UI. The instance key is simpler and fits a shared family instance; the schema leaves room to add BYO later without migration. |
| Autonomy | Let the assistant create todos/entries directly | Breaks the "user stays in control" principle; a wrong suggestion silently becomes real data. |
| Provider | Other LLM providers / a provider-neutral abstraction | Out of scope — the owner chose Anthropic; a premature abstraction adds indirection for no current benefit. |
| Trigger | Journal reflection in the first AI milestone | The owner asked for it last: it is the highest-trust feature (full journal text leaves the instance), so it follows the features that prove the plumbing. |

## Consequences

- For opted-in users, Kairon data (including journal text for reflection) is
  transmitted to a third party. Retention is documented; runs and their stored
  excerpts are user-deletable; opt-in is per feature and off by default.
- The backend gains an outbound dependency on a third-party API. It needs a
  timeout, a circuit breaker, and graceful failure (`application/problem+json`,
  including on a safety `refusal`); the rest of the app must run with the
  assistant disabled.
- A new cost dimension (LLM tokens): per-user monthly budgets, a Micrometer
  counter, and token columns on every run.
- `assistant` is the only module allowed to import the Anthropic SDK, enforced
  by ArchUnit, keeping the dependency contained and a future swap cheap.
- Three new milestones (M8–M10) push native mobile to M11+.
