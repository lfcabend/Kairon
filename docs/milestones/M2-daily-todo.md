# M2 — Daily Todo — implementation plan

Status: **Accepted** (implemented). Q1–Q8 resolved as recommended: bare-array list
responses (Q1, with a DESIGN §5 note added), `rollover-preview` and
`:rollover-undo` added to DESIGN §5.1 (Q2), the minimal `todo.api` port is in
place (Q3), the rollover sweep spans the look-back window (Q4), Undo is a real
endpoint (Q5), `:complete` takes a `{complete?}` body flag (Q6), mapping stays
hand-rolled (Q7), and a `local`-profile `DevDataSeeder` provides the Playwright
login (Q8). Companion to [`../ROADMAP.md`](../ROADMAP.md) (M2 acceptance
criteria), [`../DESIGN.md`](../DESIGN.md) §2.1 / §3.2 / §5, and
[`../DATA_MODEL.md`](../DATA_MODEL.md) (`todo_item`). This is the detailed plan for
the milestone; the roadmap checkboxes are the acceptance test.

M2 delivers the first product feature: a keyboard-first **day view** for working a
todo list, backed by a new `todo` module and the `todo_item` table.

---

## 1. Scope

### In

- `todo_item` schema (`V002__todo.sql`).
- `todo` backend module: CRUD, `:complete`, `:reorder`, `:rollover`, a
  rollover-preview read, and a rollover-undo (for the auto mode below).
- `GET /todo?day=` and `GET /todo?from=&to=`.
- Web: a minimal app shell (top nav), the day view at `/day/:date?`, quick-add,
  inline title edit, complete toggle, drag + keyboard reorder, plain-textarea
  notes, and rollover in three user-selectable modes.
- Tests: `@DataJpaTest` + Testcontainers repo tests, service unit tests, MockMvc
  controller tests, an `@SpringBootTest` flow test, ArchUnit rule for the new
  module, Vitest component tests, one Playwright happy path.

### Out (deferred, with the milestone that picks them up)

- Multi-day / week column view — day view + date nav only.
- Markdown **rendering** of `notes` — raw textarea now; renderer arrives with the
  Journal (M3) and can be retrofitted.
- The project badge as a working link — needs `projects` (M4). Rendered as a
  non-interactive chip when `sourceProjectTaskId` is set.
- "Today" aggregation screen — M6.
- Tags, recurring todos, reminders — backlog.
- Generated OpenAPI client — web keeps hand-written types in
  `web/src/lib/api/types.ts` (as in M1) until the spec is published.

---

## 2. Decisions locked for this milestone

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **Introduce a minimal app shell now.** A new `AppLayout` (top nav bar: *Day*, *Account*; more links added as milestones land) wraps the protected routes. `/` redirects to `/day/<today>`. `AccountPage` moves under the shell; `PingCard` is dropped from it (M0 scaffolding). | Every milestone from here needs navigation; deferring it means reworking the day view later. |
| D2 | **Rollover behaviour is a per-user preference with all three modes implemented.** `app_user.preferences.todo.rollover` ∈ `{ "manual", "pick", "auto" }`, default `"manual"`. Set from a radio group on `AccountPage`. | User asked for a configurable choice rather than one fixed UX. |
| D3 | **Notes: plain `<textarea>` only.** Expanding a row reveals a raw textarea bound to `notes`, saved on blur. No markdown lib. | Keep M2 small; `react-markdown` enters with M3. |
| D4 | **Rollover sweeps every past day that still has `OPEN` items**, inside a bounded look-back window (`kairon.todo.rollover-look-back-days`, default 14) — not "yesterday", and not just the single most-recent active day. The list is often skipped for days at a time, so unfinished items must not be stranded in the gap between two active days. The preview groups carried items by their source day; `pick` mode and the `auto`-mode Undo keep the user in control of an over-eager sweep. | Reflects irregular use; the bounded window plus `CANCELLED`-not-deleted sources make an unwanted sweep cheap to reverse. |
| D5 | **Follow the identity module's hand-rolled mapping precedent** (static `from(...)` factory methods on DTO records), not MapStruct. MapStruct is named in DESIGN/CLAUDE but is not on the classpath and not used by `identity`; adopting it is a separate decision. | Consistency with existing code; no new build wiring. |

---

## 3. Data model — `V002__todo.sql`

New migration `backend/src/main/resources/db/migration/V002__todo.sql`. Columns
per [`../DATA_MODEL.md`](../DATA_MODEL.md) → `todo_item`.

```sql
-- Daily todo items (module: todo). One item belongs to one user and one calendar
-- day (interpreted in the user's timezone; the server stores whatever `day` the
-- client sends). See docs/DATA_MODEL.md (todo_item) and docs/DESIGN.md §2.1.

CREATE TABLE todo_item (
    id                     uuid         PRIMARY KEY,
    user_id                uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    day                    date         NOT NULL,
    title                  varchar(500) NOT NULL,
    notes                  text,
    status                 varchar(20)  NOT NULL DEFAULT 'OPEN',
    priority               smallint     NOT NULL DEFAULT 0,
    position               integer      NOT NULL,
    estimate_minutes       integer,
    source_project_task_id uuid,        -- FK added in V004 when project_task exists
    rolled_over_from_id     uuid        REFERENCES todo_item (id) ON DELETE SET NULL,
    completed_at           timestamptz,
    deleted_at             timestamptz,
    created_at             timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL,
    version                bigint       NOT NULL DEFAULT 0,

    CONSTRAINT todo_item_status_check   CHECK (status IN ('OPEN', 'DONE', 'CANCELLED')),
    CONSTRAINT todo_item_priority_check CHECK (priority BETWEEN 0 AND 3)
);

CREATE INDEX todo_item_user_day_idx ON todo_item (user_id, day) WHERE deleted_at IS NULL;
CREATE INDEX todo_item_source_task_idx ON todo_item (source_project_task_id);
```

Notes:

- `source_project_task_id` is a bare `uuid` for now; the FK to `project_task` is
  added in `V004__projects.sql` (that table does not exist yet). The
  `todo_item_source_task_idx` is created now so M6 promotion queries are cheap.
- `rolled_over_from_id` self-references `todo_item`; `ON DELETE SET NULL` so a
  hard delete of an ancestor (not expected — deletes are soft) can't orphan.
- Partial index on `deleted_at IS NULL` — every list query filters soft-deleted
  rows out.

---

## 4. Backend

### 4.1 Package layout — `com.kairon.todo`

```
com.kairon.todo
├── api/            TodoApi (port), TodoItemView            ← other modules depend only on this
├── domain/         TodoItem (@Entity), TodoStatus (enum)
├── repo/           TodoItemRepository
├── app/            TodoService, RolloverService, TodoConfig
└── web/            TodoController, TodoDtos
```

- **`api` port now, minimal.** `TodoApi` exposes only what M6 `planning` will
  need (`List<TodoItemView> forDay(UserId, LocalDate)` and a
  `create(...)` used by "promote to today"). Implemented by `TodoService`.
  Adding it now sets the cross-module pattern and lets the ArchUnit rule be
  written once. It costs ~1 interface + 1 record.

### 4.2 Domain

- **`TodoStatus`** — `OPEN`, `DONE`, `CANCELLED`.
- **`TodoItem`** `@Entity` in `todo.domain`, following `RefreshToken`/`AppUser`
  conventions: `@Id UUID` from `Uuidv7.next()`, `@Version long`,
  `@CreationTimestamp` / `@UpdateTimestamp`, private all-args constructor + static
  factories, behaviour methods rather than setters:
  - `static TodoItem create(UUID userId, LocalDate day, String title, int position, ...)`
  - `static TodoItem rolledFrom(TodoItem source, LocalDate toDay, int position)` —
    copies `title`, `notes`, `priority`, `estimateMinutes`,
    `sourceProjectTaskId`; sets `rolledOverFromId = source.id`.
  - `void rename(String)`, `void editNotes(String)`, `void reprioritise(int)`,
    `void estimate(Integer minutes)`, `void moveTo(int position)`
  - `void complete(Instant when)` → `status=DONE`, `completedAt=when`
  - `void reopen()` → `status=OPEN`, `completedAt=null`
  - `void cancel()` → `status=CANCELLED`
  - `void softDelete(Instant when)` → `deletedAt=when`

### 4.3 Repository — `TodoItemRepository extends JpaRepository<TodoItem, UUID>`

```java
List<TodoItem> findByUserIdAndDayAndDeletedAtIsNullOrderByPositionAscCreatedAtAsc(UUID userId, LocalDate day);

List<TodoItem> findByUserIdAndDayBetweenAndDeletedAtIsNullOrderByDayAscPositionAsc(UUID userId, LocalDate from, LocalDate to);

Optional<TodoItem> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

// Rollover preview / sweep: every non-deleted OPEN item on a day strictly before
// `onDay` and no earlier than `notBefore`, oldest day first. The service groups
// the result by `day` for the preview and carries the whole set on rollover.
List<TodoItem> findByUserIdAndStatusAndDayLessThanAndDayGreaterThanEqualAndDeletedAtIsNullOrderByDayAscPositionAsc(
        UUID userId, TodoStatus status, LocalDate onDay, LocalDate notBefore);
```

### 4.4 Application services

**`TodoService`** (`@Service`, methods `@Transactional`; reads `readOnly = true`).
Every method takes a `UserId` and 404s (never 403) on someone else's row via
`ApiException.notFound(...)`, per DESIGN §3.2.

| Method | Notes |
| --- | --- |
| `list(UserId, LocalDate day)` | ordered list for the day. |
| `range(UserId, LocalDate from, LocalDate to)` | flat, day-then-position ordered; guard `to - from ≤ 92 days`. |
| `create(UserId, CreateCommand)` | `position = (max position for (user,day)) + 100`, or `100` if empty. Ties are broken by `created_at` so a rare concurrent-create collision is cosmetic. |
| `patch(UserId, id, PatchCommand)` | partial update (`title`, `notes`, `priority`, `estimateMinutes`, `status`). Optional `expectedVersion`: if present and stale → `ApiException.conflict` (409). Allowed status transitions: `OPEN↔DONE` (via `complete`/`reopen`), `OPEN→CANCELLED`, `CANCELLED→OPEN`. |
| `complete(UserId, id, boolean complete)` | `complete=true` → `complete(now)`; `false` → `reopen()`. Backs `POST /todo/{id}:complete`. |
| `reorder(UserId, LocalDate day, List<UUID> orderedIds)` | validates the id set exactly equals the day's non-deleted items, then rewrites `position` to `100, 200, 300, …`. Returns the reordered list. |
| `softDelete(UserId, id)` | sets `deletedAt`. |

**`RolloverService`** (`@Service`, `@Transactional`).

| Method | Notes |
| --- | --- |
| `preview(UserId, LocalDate onDay)` | loads every eligible `OPEN` item in `[onDay.minusDays(lookBack), onDay)` and groups them by `day`. Returns `{ sourceDays: [{ day, items }], totalItems }` (empty list ⇒ nothing to carry). |
| `rollover(UserId, RolloverCommand{toDay, fromDay?, ids?})` | resolves the target set: `ids` when given (each validated `OPEN`, owned, `day < toDay`); otherwise **every** eligible `OPEN` item in `[toDay.minusDays(lookBack), toDay)`, optionally narrowed to a single `fromDay`. For each: create `TodoItem.rolledFrom(source, toDay, nextPosition)`, then `source.cancel()` (per DATA_MODEL: **cancel the old, create the new**). New positions are appended in source-day, then source-position order so the carried block stays coherent. Returns the created items. |
| `undo(UserId, UndoCommand{createdIds})` | for each created item still present and `rolledOverFromId != null`: `reopen()` its source, then `softDelete(now)` the created item. Only touches items owned by the user. Used by the `auto` mode's toast. |

### 4.5 Web layer

**`TodoController`** `@RestController @RequestMapping("/api/v1/todo")`, mirroring
`MeController` (constructor injection, `@CurrentUser UserId`, `@Valid @RequestBody`).

**`TodoDtos`** — package-private `final class` holding request/response records
with `@NotBlank`/`@Size`/`@Min`/`@Max` and a `TodoItemResponse.from(TodoItemView)`
factory. Controllers never see the entity (ArchUnit-enforced).

**`TodoConfig`** `@Configuration` in `todo.config` with
`@EnableConfigurationProperties(TodoProperties.class)` — `kairon.todo.rollover-look-back-days`
(default 14), `kairon.todo.range-max-days` (default 92). Mirrors `IdentityConfig`.

### 4.6 Authorization & errors

- Scope every query by `userId` from `@CurrentUser`; missing/foreign row →
  `ApiException.notFound` (404).
- Stale `expectedVersion` → 409 via `ApiException.conflict`; the existing
  `GlobalExceptionHandler` also maps `OptimisticLockingFailureException` → 409.
- Validation failures render as `application/problem+json` with the per-field
  `errors` array (already handled globally).

---

## 5. API contract

Base path `/api/v1`. Bearer access token on every call. `day`, `from`, `to`,
`fromDay`, `toDay` are ISO `LocalDate` strings.

### `TodoItem` response shape

```json
{
  "id": "018f…",
  "day": "2026-09-09",
  "title": "Draft the Q3 planning note",
  "notes": null,
  "status": "OPEN",
  "priority": 0,
  "position": 300,
  "estimateMinutes": 30,
  "sourceProjectTaskId": null,
  "rolledOverFromId": null,
  "completedAt": null,
  "createdAt": "2026-09-09T08:14:03Z",
  "updatedAt": "2026-09-09T08:14:03Z",
  "version": 0
}
```

| Method & path | Body | Response |
| --- | --- | --- |
| `GET /todo?day=2026-09-09` | — | `TodoItem[]` (position order). |
| `GET /todo?from=2026-09-01&to=2026-09-14` | — | `TodoItem[]` (day, then position). |
| `POST /todo` | `{ day, title, notes?, priority?, estimateMinutes?, sourceProjectTaskId? }` | `201` + `TodoItem`. |
| `PATCH /todo/{id}` | `{ title?, notes?, priority?, estimateMinutes?, status?, expectedVersion? }` | `200` + `TodoItem`; `409` on version mismatch. |
| `DELETE /todo/{id}` | — | `204` (soft delete). |
| `POST /todo/{id}:complete` | `{ complete?: boolean }` (default `true`) | `200` + `TodoItem`. |
| `POST /todo:reorder` | `{ day, orderedIds: string[] }` | `200` + `TodoItem[]` (the day, reordered). `400` if `orderedIds` ≠ the day's items. |
| `GET /todo/rollover-preview?onDay=2026-09-09` | — | `{ sourceDays: [{ day: string, items: TodoItem[] }], totalItems: number }` (oldest day first). |
| `POST /todo:rollover` | `{ toDay, fromDay?, ids?: string[] }` | `200` + `{ rolledOver: TodoItem[] }` (new items on `toDay`). `fromDay` and `ids` both omitted ⇒ every `OPEN` item from the last `rollover-look-back-days` days is carried. Carried sources become `CANCELLED`. |
| `POST /todo:rollover-undo` | `{ createdIds: string[] }` | `200` + `{ reopened: TodoItem[] }` (the restored sources). Deletes the created items. |

> **Deviations from DESIGN §5.1 to ratify:**
> 1. List endpoints return a **bare array**, not the `{ content, page, totalElements }`
>    envelope §5 prescribes — a day's list is a small, fully-ordered set with no
>    pagination need. (Consistent with the "simplest viable" principle.)
> 2. `GET /todo/rollover-preview` and `POST /todo:rollover-undo` are **new**
>    endpoints not in the §5.1 sketch, needed by D2/D4. DESIGN §5.1 should gain
>    both rows.
> 3. `POST /todo:rollover`'s `fromDay` is now **optional** (§5.1 sketches it as
>    required). Omitting it sweeps every past `OPEN` day in the look-back window
>    (D4); it stays as an optional narrowing filter.
>
> These are called out again in §9 as open items.

---

## 6. Web

### 6.1 New dependencies

| Package | Why | Size note |
| --- | --- | --- |
| `@dnd-kit/core`, `@dnd-kit/sortable`, `@dnd-kit/modifiers` | Accessible drag **and** keyboard reorder in one primitive (ROADMAP: "drag/keyboard reorder"). | ~30 kB gz. If we want to trim M2, ship keyboard-only reorder first and add dnd-kit as a fast-follow. |
| `sonner` | Toast for the `auto` rollover mode's "Rolled N items · Undo". shadcn's default toast. | ~5 kB gz. |

**No date library.** We need only ±1 day on an ISO date and locale formatting —
a ~15-line `web/src/lib/date.ts` (`todayInZone(tz)`, `addDays(iso, n)`,
`formatLongDate(iso)` via `Intl.DateTimeFormat`) covers it. `date-fns` is the
fallback if this grows.

### 6.2 shadcn/ui components to add

Into `web/src/components/ui/` following the existing `button`/`input`/`label`
pattern: `checkbox`, `dropdown-menu`, `dialog`, `radio-group` (pulls
`@radix-ui/react-{checkbox,dropdown-menu,dialog,radio-group}`).

### 6.3 App shell & routing

- **`web/src/components/AppLayout.tsx`** — top bar (app name, `NavLink`s to
  `/day` and `/account`, display name from the `me` query), `<Outlet />`,
  `<Toaster />` from `sonner`.
- **`web/src/App.tsx`**:

```tsx
<Routes>
  <Route path="/login" element={<LoginPage />} />
  <Route path="/register" element={<RegisterPage />} />
  <Route element={<ProtectedRoute />}>
    <Route element={<AppLayout />}>
      <Route index element={<Navigate to="/day" replace />} />
      <Route path="day/:date?" element={<DayView />} />
      <Route path="account" element={<AccountPage />} />
    </Route>
  </Route>
  <Route path="*" element={<Navigate to="/" replace />} />
</Routes>
```

- `AccountPage` loses `<PingCard />`; gains the rollover-mode section (§6.6).

### 6.4 Feature folder — `web/src/features/todo/`

```
features/todo/
├── DayView.tsx           route component: resolves the date, owns the queries
├── DateNav.tsx           ‹ › + long date + "Today" button; ←/→ key handler
├── DaySummary.tsx        "3 open · 2 done · ~90 min left" (computed from the list)
├── QuickAdd.tsx          always-visible input; Enter creates, keeps focus
├── TodoList.tsx          dnd-kit SortableContext; splits open / done / cancelled
├── TodoRow.tsx           drag handle, checkbox, inline-edit title, priority,
│                         project chip, rolled-over ↩, estimate, ⋯ menu, notes
├── RolloverPrompt.tsx    manual / pick banner; hidden in auto mode
├── RolloverPickerDialog.tsx   checkbox list for "pick" mode
├── useRollover.ts        preview query + rollover/undo mutations + auto effect
└── todoKeys.ts           query-key factory
```

### 6.5 Data layer

- **`web/src/lib/api/todo.ts`** — `todoApi` object mirroring `authApi`
  (`apiFetch<TodoItem[]>("/todo?day=" + day)`, etc.).
- **Types** — add `TodoItem`, `TodoStatus`, `CreateTodoBody`, `PatchTodoBody`,
  `ReorderBody`, `RolloverBody`, and
  `RolloverPreview = { sourceDays: { day: string; items: TodoItem[] }[]; totalItems: number }`
  to `web/src/lib/api/types.ts` (hand-written, as today).
- **Query keys** — `todoKeys.day(date)`, `todoKeys.rolloverPreview(date)`.
- **Hooks** (`useQuery`/`useMutation` on the shared `queryClient`):
  - `useTodos(date)` → `todoKeys.day(date)`.
  - `useCreateTodo(date)` — **optimistic**: append a temp row (negative/UUID temp
    id) to `todoKeys.day(date)`; reconcile on success; roll back + toast the
    `ProblemDetail` on error.
  - `usePatchTodo(date)`, `useCompleteTodo(date)` — optimistic in place.
  - `useReorderTodo(date)` — optimistic reorder; on drop send full `orderedIds`.
  - `useDeleteTodo(date)` — optimistic remove.
  - `useRollover()` / `useRolloverUndo()` — invalidate `todoKeys.day(fromDay)`
    and `todoKeys.day(toDay)`.

### 6.6 Rollover — the three modes (D2)

`useRollover(date, mode)` where `mode` comes from
`me.preferences?.todo?.rollover ?? "manual"`:

| Mode | Behaviour when `rollover-preview` for today is non-empty |
| --- | --- |
| `manual` | `RolloverPrompt` banner: *"12 unfinished from 3 earlier days → **Roll over**"* (or *"From Fri 5 Sep: 2 unfinished"* when `sourceDays` has one entry). Click → `rollover({ toDay: today })` — sweeps the whole preview. |
| `pick` | Same banner; click opens `RolloverPickerDialog`, a checkbox list **grouped by source day** (day sub-headers, "select all" per day) → `rollover({ toDay: today, ids })`. |
| `auto` | On the **first** render of the day view for **today**, an effect calls `rollover({ toDay: today })`, then `toast("Rolled 12 items from 3 earlier days", { action: Undo })`. Undo → `rolloverUndo({ createdIds })`. |

Guards for `auto`:

- Only when the resolved `:date` equals `todayInZone(me.timezone)` — never while
  browsing other days.
- Runs once per mount; after it rolls, the sources are `CANCELLED`, so the next
  preview is empty and a remount is a no-op (natural idempotency).
- If `me` (and thus the timezone) hasn't loaded yet, wait — don't guess.

**Preference control** — `AccountPage` gains a "Daily todo" `<section>` with a
shadcn `RadioGroup` (Manual / Pick which / Automatic) that `PATCH /me`s
`{ preferences: { ...me.preferences, todo: { rollover } } }`.

### 6.7 Interactions

Row anatomy and the keyboard map are specified in the screen design (this
session's discussion). Implementation notes:

- **Inline edit** — `TodoRow` swaps the title `<span>` for an `<input>` on click /
  Enter / `e`; Esc reverts, Enter or blur commits via `usePatchTodo`.
- **Selection** — `DayView` holds `selectedId`; `↑`/`↓` move it (skip when a text
  field is focused). `Space`/`x` toggle complete, `1`/`2`/`3`/`0` set priority,
  `Alt+↑`/`Alt+↓` reorder, `n` or `/` focus quick-add.
- **`←`/`→`** outside inputs → `navigate("/day/" + addDays(date, ±1))`.
- **dnd-kit** — `SortableContext` over the open items; `KeyboardSensor` +
  `PointerSensor`. On `onDragEnd`, compute the new order and call
  `useReorderTodo`.
- **Done / cancelled** — done items render below a divider, dimmed + line-through;
  cancelled collapse behind a "Show N cancelled" toggle.
- **States** — skeleton rows while loading; "Nothing planned for this day yet."
  empty state with quick-add focused; inline retry on list-fetch error using the
  `ProblemDetail.detail`.

---

## 7. Testing

### Backend

| Test | Type | Covers |
| --- | --- | --- |
| `TodoItemRepositoryTest` | `@DataJpaTest` + Testcontainers PG | day query ordering, soft-delete filtering, `dayBetween` range, the rollover-sweep query (returns OPEN items across multiple skipped days oldest-first, respects `notBefore`, ignores DONE/CANCELLED/deleted). |
| `TodoServiceTest` | plain JUnit 5 + AssertJ, Mockito repo | `create` position = max + 100; `reorder` rewrites to 100/200/…; `reorder` rejects a mismatched id set (400); `complete(true/false)` sets/clears `completedAt`; foreign-user row → 404; stale `expectedVersion` → 409; status-transition rules. |
| `RolloverServiceTest` | plain JUnit 5 | `preview` groups eligible OPEN items by source day across a multi-day gap and respects the look-back bound; `rollover` with no `ids` sweeps every eligible day, cancels sources, and creates `toDay` copies carrying `notes`/`priority`/`estimateMinutes`/`sourceProjectTaskId` with `rolledOverFromId` set and positions appended in source-day order; explicit `ids` roll only that subset; `undo` reopens sources and soft-deletes the created items. |
| `TodoControllerTest` | `@WebMvcTest(TodoController.class)` + `@Import({SecurityConfig, WebMvcConfig, CurrentUserArgumentResolver})`, `@MockitoBean TodoService`/`RolloverService`/`JwtDecoder` | every endpoint's happy path; blank title → 400; `priority` out of range → 400; no token → `application/problem+json` 401; `:reorder` mismatch → 400; 409 body shape. |
| `TodoFlowIntegrationTest` | `@SpringBootTest` + Testcontainers | register → login → `POST /todo` ×3 → `:reorder` → `:complete` one → `:rollover` to the next day → assert next day has the carried items and the source day's items are `CANCELLED`. |
| `ArchitectureTest` (extend) | ArchUnit | add `todoInternalsArePrivate`: no class outside `com.kairon.todo..` depends on `com.kairon.todo.domain..` / `com.kairon.todo.repo..`. The generic entity-in-`domain` and no-entity-in-`web` rules already apply. |

### Web

| Test | Covers |
| --- | --- |
| MSW handlers (`web/src/test/msw/handlers.ts`) | add `GET/POST/PATCH/DELETE /api/v1/todo*`, `:complete`, `:reorder`, `rollover-preview`, `:rollover`, `:rollover-undo` with an in-memory day list. |
| `DayView.test.tsx` | renders the list; quick-add + Enter appends a row; checkbox toggles status; `‹`/`›` and `←`/`→` change the fetched day; empty state. |
| `RolloverPrompt.test.tsx` | `manual` shows the banner and rolls all on click; `pick` opens the dialog and rolls the checked subset; `auto` rolls on mount for today only (not for a past day) and the toast Undo calls `:rollover-undo`. |
| `AccountPage.test.tsx` (extend) | the rollover `RadioGroup` PATCHes `preferences.todo.rollover`. |
| `todo.spec.ts` (Playwright) | log in as the seeded dev user, add a task, complete it, navigate a day, roll an item over. |

---

## 8. Task breakdown (suggested order)

1. `V002__todo.sql` + `TodoItem` + `TodoStatus` + `TodoItemRepository` +
   `TodoItemRepositoryTest`.
2. `TodoService` + `TodoApi`/`TodoItemView` + `TodoDtos` + `TodoController` +
   `TodoConfig`; `TodoServiceTest` + `TodoControllerTest` (CRUD, `:complete`,
   `:reorder`).
3. `RolloverService` + `rollover-preview` / `:rollover` / `:rollover-undo`
   endpoints; `RolloverServiceTest` + controller cases.
4. `ArchitectureTest` rule + `TodoFlowIntegrationTest`.
5. Web: add deps + shadcn components; `AppLayout` + routing change; move
   `AccountPage`, drop `PingCard`.
6. Web: `types.ts` additions, `lib/api/todo.ts`, `todoKeys`, query/mutation hooks,
   MSW handlers.
7. Web: `DayView` + `DateNav` + `DaySummary` + `QuickAdd` + `TodoList` +
   `TodoRow` (no reorder yet); inline edit, complete, notes textarea.
8. Web: dnd-kit reorder + the full keyboard map.
9. Web: `useRollover` + `RolloverPrompt` + `RolloverPickerDialog` + auto-mode
   effect + toast/undo.
10. Web: `AccountPage` rollover `RadioGroup`.
11. Web tests + Playwright happy path.
12. Docs: tick the M2 boxes in `ROADMAP.md`; add the two new endpoints and the
    bare-array list-response note to `DESIGN.md` §5.1 / §5; mark this plan
    `Accepted`.

---

## 9. Open questions / risks

| # | Question | Recommendation |
| --- | --- | --- |
| Q1 | List endpoints return a bare array vs. the DESIGN §5 `{ content, page, totalElements }` envelope. | Bare array — no pagination need for a day's list. Needs sign-off + a DESIGN §5 note. |
| Q2 | `GET /todo/rollover-preview` and `POST /todo:rollover-undo` aren't in the §5.1 sketch. | Add both to §5.1. |
| Q3 | Add the `todo.api` port now or wait for M6. | Add the minimal port now (§4.1) — cheap, sets the pattern, one ArchUnit rule. |
| Q4 | ~~Rollover across several skipped days.~~ **Resolved** (user feedback): rollover sweeps every past day with `OPEN` items in the look-back window, grouped by source day in the preview, rather than only the most-recent active day or "yesterday". | — |
| Q5 | Is Undo (`:rollover-undo`) worth the extra endpoint, or should `auto`'s toast be informational only? | Keep it — `auto` acts without asking, so a real Undo matters. It's a small inverse operation. |
| Q6 | `:complete` toggle via `{ complete: bool }` vs. a separate reopen path (`PATCH { status: "OPEN" }`). | Body flag — one endpoint the keyboard toggle can call unconditionally. `PATCH` still accepts `status` for the menu. |
| Q7 | MapStruct (named in DESIGN/CLAUDE) vs. hand-rolled factories (what `identity` does). | Hand-rolled for M2 (D5); decide on MapStruct as its own change if the mapping boilerplate grows. |
| Q8 | Dev-seeded user for the Playwright run — `application-local.yml` mentions "a dev-only seeded user behind a flag" but it isn't built yet. | Add a small `local`-profile seeder (or a Playwright `beforeAll` that registers) as part of task 11. |

---

## 10. Doc updates this milestone produces

- `ROADMAP.md` — tick the six M2 checkboxes.
- `DESIGN.md` §5 / §5.1 — bare-array list responses for `todo`; add
  `GET /todo/rollover-preview` and `POST /todo:rollover-undo`.
- `DATA_MODEL.md` — already describes `todo_item` and the rollover semantics; no
  change expected beyond confirming the DDL matches.
- This file — flip `Status: Draft` → `Accepted` once Q1–Q8 are resolved.
