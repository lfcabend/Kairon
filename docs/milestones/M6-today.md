# M6 — Today — implementation plan

Status: **Accepted — implemented.** Companion to
[`../ROADMAP.md`](../ROADMAP.md) (M6 acceptance criteria), [`../DESIGN.md`](../DESIGN.md)
§2.4 / §3.1 / §5.1, and [`../DATA_MODEL.md`](../DATA_MODEL.md) (no changes — M6 adds
no schema). This is the detailed plan for the milestone; the roadmap checkboxes are
the acceptance test. Follows the structure and conventions of
[`M5-gantt-dependencies.md`](M5-gantt-dependencies.md).

M6 adds the **`planning` module** — a pure aggregation over `todo`, `projects`, and
`journal`, with no storage of its own — and the **Today** screen: a single-day
dashboard combining today's todo list, project tasks due or overdue, and a journal
prompt. It's the smallest backend milestone so far (no migration, no new entities)
because `docs/DESIGN.md` and the M2/M4/M5 plans already carved out exactly the
cross-module ports M6 needs: `TodoApi.forDay`/`create`, `ProjectsApi.dueOrOverdue`,
and `JournalApi.hasEntryForDay` all exist in the codebase today, each with a doc
comment naming M6 as their consumer. What's missing is the `planning` module itself,
one small extension to `ProjectsApi` (below), and the web screen.

---

## 1. Scope

### In

- New module `com.kairon.planning` — `app` (`PlanningService`) + `web`
  (`PlanningController`, `PlanningDtos`) only. **No `domain`, no `repo`, no
  migration** — per `DESIGN.md` §2.4, Today is "not its own storage — a query and
  a screen" (D1).
- One extension to `ProjectsApi`: `ProjectTaskView requireTask(UserId, UUID
  taskId)` — 404s a missing/foreign task (D2). Needed because promoting a task
  into today's todo list has to validate ownership before creating the link.
- `GET /planning/today?date=` → `{ date, todos, dueProjectTasks, journalPrompt }`
  (`DESIGN.md` §5.1).
- `POST /planning/today:promote` (`{ projectTaskId, day }`) → creates a linked
  `todo_item`, `201` + the created item.
- Web: the **Today** screen — becomes the app's default landing page (index
  route), with a nav entry ahead of Day/Journal/Projects. Shows today's
  interactive todo list (reusing the existing Day-view components/hooks), a
  due/overdue project-tasks panel with one-click "add to today", and an inline
  journal quick-entry.
- Tests: `PlanningServiceTest`, `PlanningControllerTest`, an extension to
  `ProjectTaskServiceTest` for `requireTask`, a `PlanningFlowIntegrationTest`,
  Vitest component tests, a Playwright happy path.

### Out (deferred, with the milestone/backlog item that picks it up)

- **Date navigation on the Today screen.** It's always "today" — `date=` exists
  on the endpoint for the same reason every other date-taking endpoint takes an
  explicit day (M5 D8: no backend module computes "today" itself), not to
  support browsing other days from this screen. Browsing any other day still
  goes through the existing `/day` and `/journal/:date` screens.
- **Dismiss/snooze a due project task** from the Today panel without promoting
  it. Not in `DESIGN.md` §2.4 or the roadmap; a plausible backlog item once
  there's real usage.
- **Server-side de-duplication of promotion** (clicking "add to today" twice
  for the same task/day). The UI disables the button once a matching todo is
  already in the cached list for that day (D9); no unique constraint or lock —
  same low-ceremony call as the rest of the app at this scale.
- **Push notifications / reminders** for the Today screen — `ROADMAP.md`
  backlog, unscheduled.
- Any assistant-suggested content on Today — that's M8's "Suggest todos"
  feature, layered on top of this screen later.

---

## 2. Decisions locked for this milestone

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **`planning` has no `domain`/`repo` package and no migration.** It's `app` + `web` only, reading the other three modules' `api` ports and composing a response. | Matches `DESIGN.md` §2.4 literally ("Not its own storage — a query and a screen") and §3.1's module description. No new ArchUnit "internals are private" rule is needed for it — it owns nothing to protect — but the existing per-module rules (`todoInternalsArePrivate`, `projectsInternalsArePrivate`, `journalInternalsArePrivate`) already forbid `planning` from reaching past those modules' `api` packages, and `featureSlicesAreFreeOfCycles` covers the three new dependency edges (`planning → todo`, `planning → projects`, `planning → journal`) without changes. |
| D2 | **`ProjectsApi` gains `ProjectTaskView requireTask(UserId userId, UUID taskId)`**, implemented in `ProjectTaskService` by reusing the existing `TaskResolution.requireTaskWithProject` helper (M5 D6) and mapping through `ProjectTaskMapper.toView`. 404 if the task is missing or belongs to another user's project. | `TodoApi.create`'s `sourceProjectTaskId` has never been ownership-validated — per `CLAUDE.md`'s current-state note, M2 left it "a bare `uuid`" with no FK, and `TodoService`'s `create`/`NewTodo` path stores whatever id it's given. Promotion is the first place that id actually needs to be trustworthy (it's how a project task becomes visible as "today's work"), so `PlanningService.promote` must resolve and authorize it itself before minting a todo item — the same 404-not-403 discipline every other cross-owner lookup in the app already follows. |
| D3 | **`POST /planning/today:promote` is a real server-side operation, not client sugar over the existing `POST /todo`.** Even though `CreateTodoBody` already accepts `sourceProjectTaskId` (M2), routing promotion through `planning` lets `PlanningService.promote` call `ProjectsApi.requireTask` (D2) first — 404s a foreign/missing task before any todo item is created — and supplies the todo's `title` from the task's own name server-side, so the client can't send a stale or wrong one. | Matches `ROADMAP.md`'s and `DESIGN.md` §5.1's explicit `:promote` endpoint (not a documented alternative); ties directly to D2 — the endpoint's entire reason to exist is the validation step, not the todo-creation call itself (which `TodoApi.create` already does). |
| D4 | **Today's web screen does not fetch its todo list from `GET /planning/today`'s `todos` field.** It reuses the existing `useTodos(date)` hook (React Query key `todoKeys.day(date)`, already backing `/day`) for the interactive list, and reads `GET /planning/today` only for `dueProjectTasks` and `journalPrompt`. The backend response still carries `todos` (per `DESIGN.md` §5.1) for API completeness and for future non-web clients that shouldn't need two round trips. | Todo/Day already ships a full set of optimistic mutation hooks (create/patch/complete/reorder/delete) keyed on `todoKeys.day(date)`. Also fetching the same data under a second key (`planningKeys.today(date)`) would mean every one of those mutations has to invalidate two caches to stay consistent, for no benefit — the two lists are byte-identical by construction (`todos.forDay(userId, date)` on both paths). Reusing the existing key avoids that duplication entirely. |
| D5 | **Today becomes the app's default landing page.** `App.tsx`'s index route redirects to `/today` instead of `/day`; `AppLayout`'s nav gets a "Today" entry first, ahead of Day/Journal/Projects/Account/About. `/day` (and its own date-nav) is unchanged and still reachable directly. | User call. Matches `ROADMAP.md`'s framing of it as "the 'Today' landing screen" — it's designed to be what the user sees first each session, aggregating the three areas of the app into one dashboard, with `/day`/`/journal` staying available for browsing other days or doing rollover/history work Today doesn't cover. |
| D6 | **The journal panel on Today is an inline one-line quick-add**, not a link-out-only prompt. If `journalPrompt.hasEntry` is `false`, a single-line input (mirrors Todo's `QuickAdd`) creates a minimal entry (`content` = the typed line, no title) via the existing `useCreateEntry(date)` hook on Enter; after success the panel switches to a "Continue in Journal →" link for the rest of the session (local component state, no refetch of `/planning/today` needed — see D7). If `hasEntry` is already `true` on load, the panel shows "You wrote in today's journal — continue →" directly, no inline input (M3 allows multiple entries per day, so "continue" always means "go to the day view," never "append to a specific entry" from here). | User call. Keeps journaling frictionless from the dashboard (matches the Todo `QuickAdd` interaction the rest of the app already trains the user on) without re-implementing any part of the Tiptap editor (`EntryEditor`) on the Today screen — formatting, mood, and multi-entry management stay the Journal day view's job. |
| D7 | **Creating a quick journal entry from Today does not invalidate/refetch `GET /planning/today`.** The panel's "has an entry now" state is tracked locally (a boolean set on the mutation's `onSuccess`) rather than by re-deriving it from a fresh `journalPrompt.hasEntry`. | The only consumer of `journalPrompt` is this one panel, and the mutation that changes it (`useCreateEntry`) already reports success/failure directly — refetching the whole aggregate endpoint just to re-read one boolean it already knows the answer to is unnecessary machinery. |
| D8 | **No date-nav UI on the Today screen** (see Out). `date` is always `todayInZone(user.timezone)`, computed client-side exactly like every other screen's "today" (M5 D8's precedent) and passed as the `date=` query param. | `DESIGN.md` §2.4 describes Today as a single fixed "today" dashboard, not a day browser; browsing other days already has a home on `/day`/`/journal/:date`. |
| D9 | **"Add to today" is disabled/shows "Added" once a due task already has a matching todo** (`sourceProjectTaskId === task.id`) **in the current day's cached todo list** — computed client-side from the same `useTodos(date)` cache D4 already uses, no new backend field. | Cheap, and reuses data already on the page instead of adding an `isPromoted` flag to `ProjectTaskView` or `TodoDependency`-style computed field (`violatesConstraint`-style) that only one screen would ever read. |
| D10 | **Rollover (`RolloverPrompt`/`useAutoRollover`/`useRolloverPreview`) is reused unchanged on the Today screen when its date is today**, exactly as `DayView` already does; `DayView` keeps its own copy too, since a user can still land on today's date directly via `/day`. | The rollover sweep is already idempotent by design (`docs/milestones/M2 §6.6`: sources go `CANCELLED` after a sweep, so a second mount's preview is empty) — mounting the same hook on two screens that can both show "today" is safe, not a double-apply risk. Building a shared "today shell" component to dedupe two thin wiring blocks isn't worth it for a plan this small. |
| D11 | **`journalPrompt` is a nested `{ hasEntry: boolean }` object**, not a flat top-level field on the response. | Matches `DESIGN.md` §5.1's field name (`journalPrompt`) literally, and leaves room to grow (e.g. an entry count or the day's first entry id) without a breaking response-shape change later — the same instinct as keeping `TaskDependency`'s `violatesConstraint` a named field rather than inlining booleans (M5 D13). |
| D12 | **The `PlanningController`/`PlanningDtos` response embeds `TodoItemView` and `ProjectTaskView` directly** (the other modules' own `api` DTOs), rather than re-wrapping them in `planning`-local response records. | Both are already stable, JSON-serializable public contracts of their owning modules; `planning` has no domain of its own to translate them into. Matches the minimal-abstraction instinct behind M5 D7/D12 — a wrapper record here would carry the exact same fields with no behavior. |

---

## 3. Data model

**No migration.** `planning` introduces no table. `DATA_MODEL.md` needs no change
this milestone — noted explicitly here so it isn't mistaken for an oversight.

---

## 4. Backend

### 4.1 `ProjectsApi` extension (`com.kairon.projects`)

```java
public interface ProjectsApi {

    List<ProjectTaskView> dueOrOverdue(UserId userId, LocalDate day);

    /** Resolves a task the caller owns (via its project); 404 if missing or foreign. */
    ProjectTaskView requireTask(UserId userId, UUID taskId);
}
```

`ProjectTaskService` (already `implements ProjectsApi`) adds:

```java
@Override
@Transactional(readOnly = true)
public ProjectTaskView requireTask(UserId userId, UUID taskId) {
    TaskResolution.TaskAndProject resolved =
            TaskResolution.requireTaskWithProject(tasks, projects, userId, taskId);
    return ProjectTaskMapper.toView(resolved.task(), resolved.project());
}
```

No other change to `projects` — `TaskResolution` (M5 D6) already exists exactly
for this "resolve + authorize a task with no project id in hand" shape.

### 4.2 Package layout — new module `com.kairon.planning`

```
com.kairon.planning
├── app/   PlanningService
└── web/   PlanningController, PlanningDtos
```

### 4.3 `PlanningService` (`@Service`, `@Transactional(readOnly = true)` at class level)

```java
public record TodayView(
        LocalDate date,
        List<TodoItemView> todos,
        List<ProjectTaskView> dueProjectTasks,
        boolean hasJournalEntry) {
}

@Service
public class PlanningService {

    private final TodoApi todos;
    private final ProjectsApi projects;
    private final JournalApi journal;

    public TodayView today(UserId userId, LocalDate date) {
        List<TodoItemView> todosForDay = todos.forDay(userId, date);
        List<ProjectTaskView> due = projects.dueOrOverdue(userId, date);
        boolean hasEntry = journal.hasEntryForDay(userId, date);
        log.debug("Assembled today view userId={} date={} todos={} dueTasks={} hasEntry={}",
                userId.value(), date, todosForDay.size(), due.size(), hasEntry);
        return new TodayView(date, todosForDay, due, hasEntry);
    }

    @Transactional
    public TodoItemView promote(UserId userId, UUID projectTaskId, LocalDate day) {
        ProjectTaskView task = projects.requireTask(userId, projectTaskId);
        TodoItemView created = todos.create(userId, new TodoApi.NewTodo(
                day, task.name(), null, 0, null, task.id()));
        log.info("Promoted task {} to todo {} userId={} day={}",
                task.id(), created.id(), userId.value(), day);
        return created;
    }
}
```

`requireTask`'s `ApiException.notFound` (D2) propagates unchanged through
`promote` — no extra try/catch, same as every other cross-service call in the
app (e.g. `TaskDependencyService`, M5 §4.4).

### 4.4 Web layer

**`PlanningController`** `@RestController @RequestMapping("/api/v1")` (class-level
`/api/v1`, not `/api/v1/planning`, so the AIP-style `:promote` suffix resolves —
same reasoning as `TodoController`, M2 §5):

```java
@GetMapping("/planning/today")
public TodayResponse today(@CurrentUser UserId userId,
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
    log.debug("GET /planning/today userId={} date={}", userId.value(), date);
    return TodayResponse.from(planning.today(userId, date));
}

@PostMapping("/planning/today:promote")
@ResponseStatus(HttpStatus.CREATED)
public TodoItemView promote(@CurrentUser UserId userId, @Valid @RequestBody PromoteRequest req) {
    log.debug("POST /planning/today:promote userId={} projectTaskId={} day={}",
            userId.value(), req.projectTaskId(), req.day());
    return planning.promote(userId, req.projectTaskId(), req.day());
}
```

`date` is a **required** `@RequestParam` (D8 — the client always supplies
"today," the same contract as `GET /todo?day=`'s `day` param when used alone).

**`PlanningDtos`** (package-private):

```java
record TodayResponse(
        LocalDate date,
        List<TodoItemView> todos,
        List<ProjectTaskView> dueProjectTasks,
        JournalPromptResponse journalPrompt) {

    static TodayResponse from(PlanningService.TodayView v) {
        return new TodayResponse(v.date(), v.todos(), v.dueProjectTasks(),
                new JournalPromptResponse(v.hasJournalEntry()));
    }
}

record JournalPromptResponse(boolean hasEntry) {
}

record PromoteRequest(@NotNull UUID projectTaskId, @NotNull LocalDate day) {
}
```

The `:promote` response is `TodoItemView` directly (D12) — no
`PlanningController`-local wrapper.

### 4.5 Authorization & errors

`today` never 404s (an empty result set is a valid answer — no todos, no due
tasks, no entry is just "a quiet day"). `promote` 404s via `ProjectsApi.requireTask`
(D2) exactly like any other foreign-task lookup elsewhere in `projects`.

---

## 5. API contract

Base path `/api/v1`. Bearer access token on every call.

### `GET /planning/today?date=` response shape

```json
{
  "date": "2026-09-18",
  "todos": [ { "id": "018f…", "day": "2026-09-18", "title": "…", "status": "OPEN", "…": "…" } ],
  "dueProjectTasks": [
    {
      "id": "018f…", "projectId": "018f…", "projectName": "Kitchen remodel", "projectColor": "#…",
      "name": "Order cabinets", "status": "TODO", "isMilestone": false,
      "plannedStart": "2026-09-15", "plannedEnd": "2026-09-17", "…": "…"
    }
  ],
  "journalPrompt": { "hasEntry": false }
}
```

| Method & path | Body | Response |
| --- | --- | --- |
| `GET /planning/today?date=` | — | `TodayResponse` (D11/D12). |
| `POST /planning/today:promote` | `{ projectTaskId, day }` | `201` + `TodoItemView`; `404` if `projectTaskId` is missing or belongs to another user's project (D2/D3). |

---

## 6. Web

### 6.1 Routing & app shell

- `App.tsx`: `index` route redirects to `/today` (D5); add `Route path="today"
  element={<TodayPage />}`.
- `AppLayout.tsx`: add a "Today" `NavLink` to `/today`, first in the nav order,
  ahead of Day/Journal/Projects/Account/About (D5).

### 6.2 Feature folder — `web/src/features/today/`

```
features/today/
├── TodayPage.tsx        route component: header (date only, no nav — D8), reuses
│                          RolloverPrompt/useAutoRollover/useRolloverPreview (D10)
│                          when date === today, then three panels:
├── TodayTasks.tsx        DaySummary + QuickAdd + TodoList, wired to the existing
│                          useTodos/useCreateTodo/usePatchTodo/useCompleteTodo/
│                          useReorderTodo/useDeleteTodo hooks from features/todo
│                          bound to date = today (D4) — same components DayView uses
├── DueTasksPanel.tsx      renders dueProjectTasks: project color dot + name, task
│                          name, milestone badge, plannedEnd (styled as overdue when
│                          < today and status !== DONE), an "Add to today" button
│                          (usePromoteTask) that becomes "Added" once a matching
│                          todo exists in the useTodos(date) cache (D9)
├── JournalPrompt.tsx      inline quick-add or "Continue" link per journalPrompt /
│                          local state (D6/D7), using the existing useCreateEntry
├── useToday.ts             GET /planning/today query + usePromoteTask mutation
└── planningKeys.ts         { today: (date) => ["planning", "today", date] }
```

### 6.3 Data layer

- **`web/src/lib/api/planning.ts`** — `planningApi.today(date)`,
  `planningApi.promote(body)`.
- **Types** (`web/src/lib/api/types.ts`):

```ts
export interface TodayResponse {
  date: string;
  todos: TodoItem[];
  dueProjectTasks: ProjectTask[];
  journalPrompt: { hasEntry: boolean };
}

export interface PromoteTaskBody {
  projectTaskId: string;
  day: string;
}
```

- **Hooks** (`features/today/useToday.ts`):
  - `useToday(date)` → `useQuery({ queryKey: planningKeys.today(date), queryFn:
    () => planningApi.today(date) })`. `TodayPage` reads only
    `.dueProjectTasks` and `.journalPrompt` from it (D4).
  - `usePromoteTask(date)` → `useMutation({ mutationFn: (projectTaskId) =>
    planningApi.promote({ projectTaskId, day: date }), onSuccess: () =>
    qc.invalidateQueries({ queryKey: todoKeys.day(date) }) })` — no optimistic
    append (D9's "Added" state already reads live from the refetched
    `useTodos(date)` cache; a one-click add on a small day-scale list doesn't
    need the extra machinery `useCreateTodo`'s optimistic path carries).

### 6.4 `DayView.tsx` / `AppLayout.tsx` changes

- `DayView.tsx`: unchanged (D10 — it keeps its own rollover wiring; nothing
  about its todo-list logic changes).
- `AppLayout.tsx`: one new `NavLink`, no structural change.

---

## 7. Testing

### Backend

| Test | Type | Covers |
| --- | --- | --- |
| `ProjectTaskServiceTest` (extend) | plain JUnit 5 + AssertJ, Mockito repo | `requireTask` returns a view for an owned task; 404 for a missing task; 404 for another user's task. |
| `PlanningServiceTest` | plain JUnit 5 + AssertJ, Mockito for `TodoApi`/`ProjectsApi`/`JournalApi` | `today` composes all three ports into `TodayView`; `promote` calls `requireTask` then `TodoApi.create` with `title = task.name()` and `sourceProjectTaskId = task.id()`; a `requireTask` 404 propagates from `promote` without calling `create`. |
| `PlanningControllerTest` | `@WebMvcTest` + `@Import({SecurityConfig, WebMvcConfig, CurrentUserArgumentResolver})`, `@MockitoBean PlanningService`/`JwtDecoder` | happy path for both endpoints; missing `date` → `400`; `promote` 404 passthrough; no token → `401`. |
| `PlanningFlowIntegrationTest` | `@SpringBootTest` + Testcontainers | seed a todo, a due project task, no journal entry; `GET /planning/today` reflects all three; `POST …:promote` creates a linked todo, appears in a follow-up `GET /todo?day=`; promoting a foreign/nonexistent task → `404`; adding a journal entry flips `hasEntry` on the next `GET`. |
| `ArchitectureTest` | ArchUnit | no new rule (D1) — existing rules already cover the new module. |

### Web

| Test | Covers |
| --- | --- |
| MSW handlers | add `GET /api/v1/planning/today`, `POST /api/v1/planning/today:promote`, sharing the existing todo/project-task/journal in-memory stores. |
| `TodayPage.test.tsx` | renders today's todos (interactive: complete/reorder work exactly as on `/day`), due tasks with correct overdue styling, and the journal prompt in both `hasEntry` states. |
| `DueTasksPanel.test.tsx` | "Add to today" calls `usePromoteTask`; the button flips to "Added" once the corresponding todo shows up in the mocked `useTodos` cache; a `404` from the mock server surfaces as an inline error, not a thrown exception. |
| `JournalPrompt.test.tsx` | quick-add on Enter calls `useCreateEntry`; panel switches to "Continue in Journal →" after success without a `planning/today` refetch (D7); with `hasEntry: true` from the start, no input is shown. |
| `today.spec.ts` (Playwright, new) | log in → lands on `/today` (D5); seed a due project task and confirm "Add to today" adds it to the visible todo list; add a quick journal line and confirm `/journal/:date` shows it after navigating there. |

---

## 8. Task breakdown (suggested order)

1. `ProjectsApi.requireTask` + `ProjectTaskService` implementation + extend
   `ProjectTaskServiceTest`.
2. `com.kairon.planning` module: `PlanningService` (`today`, `promote`) + unit
   tests.
3. `PlanningController` + `PlanningDtos`; controller tests.
4. `PlanningFlowIntegrationTest`.
5. Docs: `DESIGN.md` §3.1 — extend the `planning` module bullet to read from
   `journal` too (it already lists `todo`/`projects`; this ratifies the
   pre-existing `JournalApi.hasEntryForDay` M6 groundwork as used).
6. Web: `planning.ts` + `types.ts` additions, `planningKeys.ts`, `useToday` +
   `usePromoteTask`, MSW handlers.
7. Web: `TodayTasks.tsx` (reusing `DaySummary`/`QuickAdd`/`TodoList` and the
   existing todo hooks), `DueTasksPanel.tsx`, `JournalPrompt.tsx`.
8. Web: `TodayPage.tsx` assembling the above + reused rollover pieces (D10).
9. Web: routing (`App.tsx` index → `/today`) + `AppLayout.tsx` nav entry (D5).
10. Web tests + Playwright happy path.
11. Docs: tick the M6 boxes in `ROADMAP.md`; update `CLAUDE.md`'s "Current
    state" (new `planning` module bullet: `PlanningService`/`PlanningController`;
    extend the `projects` bullet with `requireTask`; extend the web bullet with
    `features/today/`); mark this plan `Accepted`.

---

## 9. Open questions / risks

| # | Question | Recommendation |
| --- | --- | --- |
| Q1 | D9's client-side "already added" check has a narrow race: two rapid clicks before the first `:promote` round-trips could still create two linked todos (no server-side dedup). | Accept as-is — single-user-scale, low-ceremony app; disabling the button optimistically on click (in addition to the post-refetch state) closes the realistic case cheaply if it turns out to matter, without a backend uniqueness constraint. |
| Q2 | D10 mounts the same rollover hooks on both `/today` and `/day` when both show "today" — could a user see the auto-rollover toast twice in one session if they visit both? | Accept — the sweep itself is idempotent (M2 §6.6), so the only possible symptom is a second empty-preview mount doing nothing, not a duplicate rollover. Not worth a shared "today shell" abstraction for a two-screen overlap this small. |
| Q3 | Should `dueOrOverdue`'s ordering (`plannedEnd ASC`, set in M6's own groundwork query) get a secondary sort (e.g. overdue tasks before due-today ones) for the Today panel specifically? | Ship as query-ordered for M6; revisit only if real usage shows the flat `plannedEnd` order buries overdue items in a long list — cheap to add a `status`/`overdue` tiebreaker later without a contract change. |
| Q4 | Is a "dismiss for today" action on `DueTasksPanel` (Out, §1) worth pulling into M6 rather than deferring? | Deferred per the Out list — `DESIGN.md` §2.4 doesn't call for it and the roadmap doesn't list it; add only if usage shows the due-task panel getting cluttered with tasks the user isn't ready to act on. |

---

## 10. Doc updates this milestone produces

- `ROADMAP.md` — tick the three M6 checkboxes once implemented.
- `DESIGN.md` §3.1 — extend the `planning` module bullet to name `journal` as a
  third module it reads through its public API (alongside `todo`/`projects`),
  matching what `JournalApi.hasEntryForDay`'s existing doc comment already
  commits to.
- `DATA_MODEL.md` — no change (§3, no schema this milestone).
- `CLAUDE.md` "Current state" — add a `planning` module bullet
  (`PlanningService`/`PlanningController`); extend the `projects` bullet with
  `ProjectsApi.requireTask`; extend the web bullet with `features/today/`
  (`TodayPage`/`TodayTasks`/`DueTasksPanel`/`JournalPrompt`, `useToday`); note
  `/today` as the new default landing route in the `AppLayout` mention.
- This file — flip `Status: Draft` → `Accepted` once Q1–Q4 are resolved and the
  milestone is implemented.
