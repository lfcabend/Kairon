# M5 — Gantt & dependencies — implementation plan

Status: **Accepted — implemented.** Companion to [`../ROADMAP.md`](../ROADMAP.md) (M5 acceptance
criteria), [`../DESIGN.md`](../DESIGN.md) §2.3 / §7, and
[`../DATA_MODEL.md`](../DATA_MODEL.md) (`task_dependency`). This is the
detailed plan for the milestone; the roadmap checkboxes are the acceptance
test. Follows the structure and conventions of
[`M4-projects-core.md`](M4-projects-core.md) — same module (`com.kairon.projects`),
same hand-rolled mapping, same authz/error rules, same whole-form `PATCH`
(M4 D15).

M5 adds **dependencies between tasks** (`task_dependency`, cycle-rejected) and
a **Gantt view** on the project detail page that renders bars, milestone
diamonds, dependency arrows, a today line, and progress fill from the same
`project_task` data the Tree/Board tabs already show — no new scheduling
computation, no auto-layout. Per DESIGN §7.1 ("render what the user enters"):
the user places every date by hand or by dragging a bar; the system only
warns, softly, when a finish-to-start edge is violated.

---

## 1. Scope

### In

- `task_dependency` schema (`V005__task_dependencies.sql`).
- `projects` backend additions: `TaskDependency` domain entity,
  `TaskDependencyRepository`, `TaskDependencyService` (create with cycle
  rejection, delete), and the cascade-delete extension to
  `ProjectTaskService`/`ProjectService` so a soft-deleted task's edges don't
  dangle (D5).
- `POST /tasks/{taskId}/dependencies`, `DELETE /dependencies/{depId}`,
  `GET /projects/{id}/dependencies` (D7 — replaces DESIGN §5.1's single
  `/gantt` endpoint; see the deviation note in §5).
- Web: a third **Gantt** tab on `ProjectDetailPage` (`gantt-task-react`) —
  bars, milestone diamonds, dependency arrows with a soft-violation glyph,
  a today line, progress fill; dragging a bar's ends or body `PATCH`es the
  task. A "Depends on" section in `TaskFormDialog` to add/remove predecessor
  edges (D1).
- Tests: `@DataJpaTest` + Testcontainers repo tests, service unit tests
  (incl. cycle rejection), MockMvc controller tests, an `@SpringBootTest`
  flow test, Vitest component tests, a Playwright happy path.

### Out (deferred, with the milestone/backlog item that picks it up)

- **Assisted scheduling** — forward pass (earliest start/finish from
  `estimate_hours` + `FS` lag) and critical-path highlighting (DESIGN §7.2)
  — confirmed out of M5 by the user; stays "optional here or deferred" under
  M7 per `ROADMAP.md`.
- **Drag-to-link** dependency creation on the Gantt canvas — M5 ships the
  dialog picker only (D1); revisit only if the picker proves limiting.
- **`SS`/`FF`/`SF`** dependency types and `lag_days` in the create-dependency
  UI — the schema carries all four types and lag from day one (per
  `DATA_MODEL.md`), but the M5 form only offers `FS` with `lag_days = 0`
  (D2). Adding the other types later is a web-only change, no migration.
- **Auto-computed summary bars** for parent tasks — parents keep manual
  dates like any task (D9, mirrors M4 D3's no-auto-rollup precedent for
  `progress_percent`).
- Dependency badges on `TaskRow`/`TaskCard` (Tree/Board) — dependencies are
  visible/editable only via the Gantt tab and the task form in M5 (D16).
- Reschedule/shift-by-N-days bulk operations (DESIGN §7.2) — same as
  assisted scheduling, M7 or later.

---

## 2. Decisions locked for this milestone

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **Dependencies are created through `TaskFormDialog`'s new "Depends on" section**, not by dragging on the Gantt chart. It lists the task's current predecessors (each with a small × to remove) plus an "Add" `Select` populated from every other task in the same project. | User call, matches M4 D5's precedent (reparenting is a dialog dropdown, not a tree drag) — a picker is far cheaper to build correctly than drag-to-link (hit-testing bar edges, a temp arrow while dragging, drop validation) and doesn't tie the Gantt library choice to whether it supports link-dragging at all. |
| D2 | **The M5 UI only creates `FS` edges, with `lag_days` fixed at `0`.** `POST /tasks/{taskId}/dependencies` still accepts `type`/`lagDays` (defaulting `FS`/`0` when omitted) since the schema and service support all four types from day one — only the *form* doesn't expose a type/lag picker yet. | User call. `FS` is overwhelmingly the common case ("do X before Y"); shipping a 4-way type selector and a lag-days stepper for a first cut adds form complexity for edges the model already stores fine without a migration once there's a real need. |
| D3 | **Cycle rejection**: before inserting predecessor→successor, `TaskDependencyService` loads every edge in the project (`TaskDependencyRepository.findByProjectId`, a join through `project_task` since `task_dependency` carries no `project_id` of its own) and does an in-memory DFS from the new edge's successor — if the predecessor is already reachable, the new edge would close a cycle → `400`. One query per create call; project-scale edge counts make in-memory traversal simpler and fast enough, no need for a recursive SQL CTE. | Matches `DATA_MODEL.md`'s "the service rejects a new edge that would create a cycle (DFS on the dependency graph)" literally; in-memory over a small, already-project-scoped edge set is the simplest correct implementation. |
| D4 | **Same-project, no-self, no-duplicate enforcement in the service, not just the DB.** Same-project: the predecessor must resolve via `ProjectTaskRepository.findByIdAndProjectIdAndDeletedAtIsNull(predecessorId, successor's projectId)` → `400` "Predecessor task not found in this project." Self: `predecessorId.equals(successorId)` → `400` (mirrors `ProjectTaskService.patch`'s "a task can't be its own parent" check). Duplicate: an existing `(predecessorId, successorId)` row → `409`, checked before insert rather than caught off the DB unique-constraint violation (same pattern as `ProjectCategoryService`'s name-uniqueness check, M4 §3 note). | Consistency with existing service-layer validation style; the DB constraints (`task_dependency_uk`, the self-reference check) stay as a backstop, not the primary error path — a raw constraint-violation 500 is never what the user sees. |
| D5 | **`task_dependency` has no soft delete** (per `DATA_MODEL.md` — only `created_at`). Soft-deleting a task therefore **hard-deletes every edge touching it**: `TaskDependencyRepository.deleteAllForTask(taskId)` (a `@Modifying` bulk delete `WHERE predecessor_id = :taskId OR successor_id = :taskId`) is called from `ProjectTaskService.delete` for the task itself and each cascaded child (M4 D8's one-level cascade), and from `ProjectService.delete`'s per-task cascade loop, for every task the project cascade soft-deletes. | `DATA_MODEL.md`'s note under `project_task` — "Deleting a project cascades (soft) to its tasks **and their dependencies**" — and a dangling edge to a task that no longer shows anywhere (Tree/Board/Gantt already filter `deletedAt IS NULL`) would be a pure liability: it couldn't be deleted through the normal endpoint (its successor/predecessor task 404s) and would silently keep blocking cycle-detection against a task nobody can see. Both FKs also carry `ON DELETE CASCADE` as a schema-level backstop for the rare hard-delete-in-tests case, same reasoning as `project_task`'s own FKs (M4 §3 note). |
| D6 | **Extract the existing private `requireTaskWithProject` resolution out of `ProjectTaskService` into a package-private `TaskResolution` helper** in `projects.app`, reused by `ProjectTaskService` and the new `TaskDependencyService`. | Both services need the identical "resolve a task with no `projectId` in the URL, authorize via its owning project, 404 without leaking which part failed" logic (M4 §4.4's `TaskAndProject`/`requireTaskWithProject`). Copy-pasting an authorization-critical resolver risks the two copies drifting; extracting it is a small, mechanical refactor with no behavior change. |
| D7 | **No combined `GET /projects/{id}/gantt` endpoint.** Instead, a new `GET /projects/{id}/dependencies` (bare array, small/fully-fetched — same shape as `GET /project-categories`/`GET /projects/priority-ordered`, M4 D13/D19) returns the project's dependency edges with a computed `violatesConstraint` flag (D13). The Gantt tab composes this with the **already-fetched** `useProject(id)` (project window) and `useProjectTasks(id)` (the full task list, M4 D1's "one big page") — both already loaded by `ProjectDetailPage` for the Tree/Board tabs. **Documented deviation from `DESIGN.md` §5.1**, which lists a single `/gantt` endpoint returning "tasks + computed schedule + dependency edges"; ratified here the same way M4 ratified its own deviations (M4 §5's callout block) — see §5 below. | Every piece of data the combined endpoint would return except the edges is already sitting in the page's React Query cache by the time the Gantt tab renders; building and testing a second, overlapping read path (and a second loading/error state to reconcile with the Tree/Board tabs') buys nothing. "Computed schedule" is empty in M5 anyway (D18 defers §7.2), so the endpoint's only real payload is the edges. |
| D8 | **The Gantt's "today" line is computed client-side** via the existing `todayInZone(user.timezone)` utility (`web/src/lib/date.ts`), the same convention Todo/Journal's date-nav already use — not returned by the backend. | No backend module currently computes "today" itself for a response (Todo/Journal/planning all take a client-supplied day); introducing a new cross-module timezone lookup (`identity.api.UserAccountView.timezone` has no consumer yet) just to duplicate a value the client already knows how to compute is exactly the kind of extra machinery the project avoids. Falls out of D7 once there's no combined endpoint to put it in. |
| D9 | **Parent (summary) tasks keep manual `planned_start`/`planned_end`** like any task — no auto-computed span from subtask dates. The Gantt renders every task, top-level or subtask, as its own independent bar from its own dates; `gantt-task-react`'s built-in `type: 'project'` summary-bar (which *would* auto-span from children) is deliberately **not** used. | User call, explicitly consistent with M4 D3's precedent (`progress_percent` is manual, no rollup) — introduces no derived-field invalidation logic, no edge cases for a parent with zero, one, or mixed-milestone children. |
| D10 | **The Gantt shows every non-deleted task in the project, dated or not.** A task without both `plannedStart` and `plannedEnd` gets no bar; instead it's listed in a small "Unscheduled" panel below the chart (name + a "Set dates" link that opens `TaskFormDialog`). Milestone tasks always have both dates equal (M4 D9's invariant) so they always render. | Matches M4 D11's reasoning for the board (every task shows, hierarchy or not) — silently dropping undated tasks from the Gantt would make it lie about the project's full task count the way hiding subtasks from the board would have. |
| D11 | **Gantt row order mirrors the tree**: top-level tasks in `position` order, each immediately followed by its own subtasks in their `position` order (`TaskTree`'s existing ordering, M4 §6.6) — a flat row list, subtask names indented with a plain CSS/label prefix. No interactive expand/collapse on the Gantt itself (that's the Tree tab's job); collapsing isn't needed here since M5 is a small personal-scale project's full task list, not a large PM chart. | Reuses ordering logic the Tree tab already computes (`useProjectTasks`'s `topLevel`/`childrenByParentId`, M4 §6.5) instead of inventing a second ordering rule; keeps the Gantt's job narrowly "render the schedule," not "be another tree." |
| D12 | **Library: `gantt-task-react`** — the MVP choice `DESIGN.md`'s stack table already locked, with an explicit "replace with custom SVG/visx if it limits us" fallback. It maps directly onto what M5 needs: `isMilestone` → `type: 'milestone'`, `progressPercent` → `progress`, its own `dependencies: string[]` field on each task → the arrows, and an `onDateChange` callback → the drag-to-`PATCH` flow (D14). | Validates the ADR's stated fallback condition rather than deciding it in the abstract: M5's actual requirement list (bars, milestone diamonds, dependency arrows, today line, progress fill, drag-ends) is close to a checklist of the library's built-in props, so there's no concrete blocker yet to justify hand-rolling SVG. |
| D13 | **The FS-violation warning is computed server-side**, one boolean per edge — `violatesConstraint` in the `GET /projects/{id}/dependencies` response. For a `FS` edge: `violatesConstraint = successor.plannedStart != null && predecessor.plannedEnd != null && successor.plannedStart.isBefore(predecessor.plannedEnd.plusDays(lagDays))`. Non-`FS` edges (schema-only in M5, D2) and edges where either date is missing always report `false`. Rendered in the web app as a small warning glyph on the arrow/successor bar with a tooltip explaining which predecessor it violates — never a blocking validation (DESIGN §7.1: "a warning, not a hard error"). | Keeps the one number every future client (mobile, M11+) would otherwise have to reimplement in one place, server-side — same "SQL/service computes the numbers" instinct the `assistant` module's summaries follow (`docs/DESIGN.md` §13) — rather than duplicating lag-date arithmetic in each frontend. |
| D14 | **Dragging a bar's ends or body** (`gantt-task-react`'s `onDateChange`) issues a **whole-form** `PATCH /tasks/{taskId}` (M4 D15) with every other field taken from the task already in the React Query cache and only `plannedStart`/`plannedEnd` replaced by the dragged dates, snapped to day granularity (no time-of-day — the whole app is date-, not datetime-, based). Dragging a milestone diamond moves both dates together in one call (`isMilestone: true`, equal `plannedStart`/`plannedEnd`, matching the `markMilestone` invariant the service already enforces, M4 §4.4/D9). | Reuses the exact whole-form `PATCH` contract every other task edit already uses (`usePatchTask`, M4 §6.5) — no new partial-patch endpoint or DTO shape just for drag. |
| D15 | **Gantt is a third `TabsTrigger`** on `ProjectDetailPage`, alongside the existing "List / Tree" and "Board" (M4 §6.3). | Straightforward; matches the existing tab pattern exactly, no new page/route. |
| D16 | **Dependencies are not surfaced on `TaskRow`/`TaskCard`** (Tree/Board) in M5 — visible and editable only on the Gantt tab and in `TaskFormDialog`'s "Depends on" section. | Keeps M5's web surface to what's asked for; a "blocked by" chip on the tree/board rows is a cheap, obvious fast-follow once there's real usage, not a blocker for the milestone's acceptance criteria. |
| D17 | **Deleting an edge** happens via the × next to each predecessor listed in `TaskFormDialog`'s "Depends on" section (`DELETE /dependencies/{depId}`) — not by clicking a dependency arrow on the Gantt chart. | Consistent with D1 (picker-only, not canvas-interaction) and D16 (Gantt doesn't own dependency *editing*, only *display*) — avoids arrow hit-testing/click targets in the chosen library. |
| D18 | **Assisted scheduling (forward pass, critical path) is entirely out of M5**, confirmed by the user. `GanttEdgeResponse`/`GanttTaskResponse` (well, the dependency response, D7) carry no computed-schedule fields at all — every date rendered is exactly what's stored in `project_task`. | User call; matches `ROADMAP.md`'s M5 checklist (no forward-pass/critical-path bullet) and DESIGN §7.2's own framing as "phase 2... kept server-side... optional here or deferred" under M7. |

---

## 3. Data model — `V005__task_dependencies.sql`

New migration `backend/src/main/resources/db/migration/V005__task_dependencies.sql`.
Columns per [`../DATA_MODEL.md`](../DATA_MODEL.md) → `task_dependency`.

```sql
-- Dependencies between project tasks (module: projects). An edge points
-- predecessor -> successor; both tasks must belong to the same project
-- (service-enforced, docs/milestones/M5-gantt-dependencies.md D4) and the
-- service rejects any edge that would create a cycle (D3). No soft delete —
-- deleting a task hard-deletes its edges (D5).
-- See docs/DATA_MODEL.md (task_dependency) and docs/DESIGN.md §7.

CREATE TABLE task_dependency (
    id             uuid        PRIMARY KEY,
    predecessor_id uuid        NOT NULL REFERENCES project_task (id) ON DELETE CASCADE,
    successor_id   uuid        NOT NULL REFERENCES project_task (id) ON DELETE CASCADE,
    type           varchar(2)  NOT NULL DEFAULT 'FS',
    lag_days       integer     NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL,

    CONSTRAINT task_dependency_type_check CHECK (type IN ('FS', 'SS', 'FF', 'SF')),
    CONSTRAINT task_dependency_not_self_check CHECK (predecessor_id <> successor_id),
    CONSTRAINT task_dependency_uk UNIQUE (predecessor_id, successor_id)
);

CREATE INDEX task_dependency_predecessor_idx ON task_dependency (predecessor_id);
CREATE INDEX task_dependency_successor_idx   ON task_dependency (successor_id);
```

Notes:

- No `user_id` and no `project_id` — same reasoning as `project_task`
  (M4 §3 note): ownership is transitive through the tasks it connects. Every
  query resolves the owning project under the caller's `user_id` first
  (§4.4 below).
- `ON DELETE CASCADE` on both FKs is a schema-level backstop for a hard
  delete (tests, or a future data-cleanup job) — production deletes are the
  explicit `deleteAllForTask` bulk delete the service issues alongside a
  task's soft delete (D5), since a soft-deleted `project_task` row still
  exists and never fires the FK cascade.
- `task_dependency_uk` and `task_dependency_not_self_check` are backstops;
  `TaskDependencyService` checks both conditions itself first (D4) so the
  user always sees a clean `400`/`409`, not a raw constraint violation.
- Cross-project edges aren't blocked by the schema (there's no `project_id`
  to compare) — `TaskDependencyService.create` enforces it by requiring the
  predecessor to resolve via `ProjectTaskRepository.findByIdAndProjectIdAndDeletedAtIsNull`
  against the successor's own `projectId` (D4).

---

## 4. Backend

### 4.1 Package layout additions — `com.kairon.projects`

```
com.kairon.projects
├── domain/    + TaskDependency (@Entity), TaskDependencyType (FS/SS/FF/SF)
├── repo/      + TaskDependencyRepository
├── app/       + TaskDependencyService, TaskDependencyView, TaskDependencyMapper,
│                TaskResolution (extracted, D6)
└── web/       + TaskDependencyController, TaskDependencyDtos
```

No change to `projects.api` — M6's Today screen doesn't need dependency data
(confirmed against `ROADMAP.md`'s M6 bullets), so `ProjectsApi` (M4 D6) stays
at its current single method.

### 4.2 Domain

- **`TaskDependencyType`** — `FS`, `SS`, `FF`, `SF` (all four modeled; only
  `FS` is creatable through the M5 web form, D2).
- **`TaskDependency`** `@Entity` in `projects.domain`, same conventions as
  `ProjectTask` minus `@Version`/`updatedAt` (edges are create/delete only,
  never edited — changing type or lag means delete-and-recreate):
  - `@Id UUID` from `Uuidv7.next()`, `@CreationTimestamp createdAt`.
  - `static TaskDependency create(UUID predecessorId, UUID successorId, TaskDependencyType type, int lagDays)`.
  - Getters only — no behavior methods, there's nothing to mutate.

### 4.3 Repositories

```java
// TaskDependencyRepository extends JpaRepository<TaskDependency, UUID>
Optional<TaskDependency> findByPredecessorIdAndSuccessorId(UUID predecessorId, UUID successorId); // D4's duplicate check

// Loads every edge that touches this project, via a subquery join through
// project_task since task_dependency carries no project_id of its own.
@Query("""
        SELECT d FROM TaskDependency d
        WHERE d.predecessorId IN (
            SELECT t.id FROM ProjectTask t WHERE t.projectId = :projectId AND t.deletedAt IS NULL
        )
        """)
List<TaskDependency> findByProjectId(@Param("projectId") UUID projectId);   // D3's cycle check + D7's list endpoint

@Modifying
@Query("DELETE FROM TaskDependency d WHERE d.predecessorId = :taskId OR d.successorId = :taskId")
void deleteAllForTask(@Param("taskId") UUID taskId);   // D5
```

### 4.4 Application services

**`TaskResolution`** (D6) — package-private final class in `projects.app`,
extracted verbatim from `ProjectTaskService`'s current private method:

```java
record TaskAndProject(ProjectTask task, Project project) {}

static TaskAndProject requireTaskWithProject(
        ProjectTaskRepository tasks, ProjectRepository projects, UserId userId, UUID taskId) {
    ProjectTask task = tasks.findByIdAndDeletedAtIsNull(taskId)
            .orElseThrow(() -> ApiException.notFound("Task not found."));
    Project project = projects.findByIdAndUserIdAndDeletedAtIsNull(task.getProjectId(), userId.value())
            .orElseThrow(() -> ApiException.notFound("Task not found."));
    return new TaskAndProject(task, project);
}
```

`ProjectTaskService` is refactored to call this instead of its own private
copy; no behavior change.

**`TaskDependencyService`** (`@Service`, `@Transactional`; reads
`readOnly = true`). Resolves the **successor task's project** first under
`UserId` via `TaskResolution` (404 if missing/foreign), same rule as every
other task-scoped operation.

```java
public record CreateCommand(UUID predecessorId, String type, Integer lagDays) {}
```

| Method | Notes |
| --- | --- |
| `list(UserId, projectId)` | `requireProject` then `dependencies.findByProjectId`; maps each edge with its computed `violatesConstraint` (D13) — needs the project's tasks in hand, so it loads `tasks.findByProjectIdAndDeletedAtIsNull(projectId)` once (same method `ProjectTaskService`'s D8 cascade already uses) and builds an id→task map to evaluate each `FS` edge's dates. |
| `create(UserId, successorTaskId, CreateCommand)` | `TaskResolution.requireTaskWithProject` for the successor; `predecessorId == successorTaskId` → `400`; predecessor must resolve in the same project → `400` otherwise; existing `(predecessorId, successorTaskId)` row → `409`; `createsCycle` (DFS over `findByProjectId`, D3) → `400` "This would create a circular dependency."; else creates and saves. |
| `delete(UserId, dependencyId)` | Loads the edge (404 if missing); authorizes via `TaskResolution.requireTaskWithProject(..., dependency.getSuccessorId())` (either end works, both are in the same project); hard `DELETE`. |
| `deleteAllForTask(UUID taskId)` *(package-private, no `UserId` — called by already-authorized callers)* | Thin wrapper over `TaskDependencyRepository.deleteAllForTask`, used by `ProjectTaskService.delete` and `ProjectService.delete` (D5). |

Cycle check (`createsCycle`, private):

```java
private boolean createsCycle(UUID projectId, UUID predecessorId, UUID successorId) {
    Map<UUID, List<UUID>> adjacency = dependencies.findByProjectId(projectId).stream()
            .collect(Collectors.groupingBy(TaskDependency::getPredecessorId,
                    Collectors.mapping(TaskDependency::getSuccessorId, Collectors.toList())));
    Deque<UUID> stack = new ArrayDeque<>(List.of(successorId));
    Set<UUID> visited = new HashSet<>();
    while (!stack.isEmpty()) {
        UUID current = stack.pop();
        if (current.equals(predecessorId)) return true;
        if (!visited.add(current)) continue;
        stack.addAll(adjacency.getOrDefault(current, List.of()));
    }
    return false;
}
```

**`ProjectTaskService`** additions (D5, D6): constructor gains
`TaskDependencyRepository dependencies`; `delete(UserId, taskId)` calls
`dependencies.deleteAllForTask(taskId)` for the task itself and for each
cascaded child's id, after the existing soft-delete loop. Its private
`requireTaskWithProject`/`TaskAndProject` are removed in favor of
`TaskResolution` (D6).

**`ProjectService`** additions (D5): constructor gains
`TaskDependencyRepository dependencies`; `delete(UserId, id)`'s existing
per-task cascade loop (M4 D8) also calls `dependencies.deleteAllForTask(task.getId())`
for every task it soft-deletes.

### 4.5 Web layer

**`TaskDependencyController`** `@RestController @RequestMapping("/api/v1")`:

- `GET /projects/{id}/dependencies` → `TaskDependencyResponse[]` (D7).
- `POST /tasks/{taskId}/dependencies` → `201` + `TaskDependencyResponse`.
- `DELETE /dependencies/{depId}` → `204`.

**`TaskDependencyDtos`** — package-private, same shape as `ProjectTaskDtos`:

```java
record CreateTaskDependencyRequest(@NotNull UUID predecessorId, String type, Integer lagDays) {}

record TaskDependencyResponse(
        UUID id, UUID predecessorId, UUID successorId, String type, int lagDays,
        boolean violatesConstraint, Instant createdAt) {
    static TaskDependencyResponse from(TaskDependencyView v) { ... }
    static List<TaskDependencyResponse> from(List<TaskDependencyView> views) { ... }
}
```

### 4.6 Authorization & errors

Same rules as the rest of `projects` (M4 §4.6): an edge resolves by first
resolving one of its tasks under `userId` via `TaskResolution`; missing/
foreign → `404`; a same-project/self/duplicate/cycle violation → `400`/`409`
per D3/D4. No `expectedVersion` concept here — edges have no `@Version`
(D5's "create/delete only" note).

---

## 5. API contract

Base path `/api/v1`. Bearer access token on every call.

### `TaskDependency` response shape

```json
{
  "id": "018f…",
  "predecessorId": "018f…",
  "successorId": "018f…",
  "type": "FS",
  "lagDays": 0,
  "violatesConstraint": false,
  "createdAt": "2026-09-10T18:22:00Z"
}
```

| Method & path | Body | Response |
| --- | --- | --- |
| `GET /projects/{id}/dependencies` | — | `TaskDependency[]` — every edge touching a task in this project, `violatesConstraint` computed per D13 (D7). |
| `POST /tasks/{taskId}/dependencies` | `{ predecessorId, type?, lagDays? }` (`type` defaults `FS`, `lagDays` defaults `0` — D2) | `201` + `TaskDependency`; `400` on self/cross-project/cycle, `409` on a duplicate edge. |
| `DELETE /dependencies/{depId}` | — | `204`. |

> **Deviation from `DESIGN.md` §5.1 to ratify (D7):**
> §5.1's sketch lists a single `GET /projects/{id}/gantt` returning
> "tasks + computed schedule + dependency edges." M5 ships
> `GET /projects/{id}/dependencies` instead — tasks and the project window
> are already available via the existing `GET /projects/{id}/tasks` and
> `GET /projects/{id}` (both already fetched by `ProjectDetailPage`), and
> "computed schedule" is empty in M5 since assisted scheduling is fully
> deferred (D18). If M7 later adds a real computed schedule, that's the more
> natural time to introduce a combined `/gantt` endpoint, since only then
> does it return something the pieces above genuinely can't.
>
> Called out again in §9 as an open item.

---

## 6. Web

### 6.1 New dependencies

**`gantt-task-react`** (MIT) — the one new package this milestone adds
(D12). No other new libraries; dependency editing reuses the existing
`select`/`dialog` shadcn primitives already in the project.

### 6.2 Routing & app shell

No new routes — the Gantt view is a tab (D15) on the existing
`/projects/:id` route.

### 6.3 Feature folder additions — `web/src/features/projects/`

```
features/projects/
├── GanttView.tsx              route-tab component: composes useProject + useProjectTasks
│                               (already fetched by ProjectDetailPage) with the new
│                               useTaskDependencies; builds the gantt-task-react `Task[]`
│                               (D9 manual bars, D10 unscheduled split, D11 row order,
│                               D12 type mapping), renders the chart + an "Unscheduled"
│                               list below it, handles onDateChange → usePatchTask (D14)
├── TaskDependencySection.tsx   the "Depends on" block embedded in TaskFormDialog (D1):
│                               lists current predecessors with a × (useDeleteDependency,
│                               D17), an "Add" Select of other project tasks
│                               (useCreateDependency) — surfaces 400/409 from the create
│                               call inline (cycle / duplicate / self messages)
├── useTaskDependencies.ts      list query (GET /projects/{id}/dependencies) +
│                               create/delete mutations, dependencyKeys.ts-style keys
│                               folded into projectKeys (dependencies(projectId))
```

`TaskFormDialog.tsx` gains one addition: render `<TaskDependencySection
projectId={projectId} task={task} />` when editing an existing task (a
brand-new, not-yet-saved task has no id to hang edges off of, so the section
only appears in edit mode — same "must exist first" reasoning as why
`progressPercent`/`actualHours` inputs are also edit-only in the current
form, M4 §6.5).

### 6.4 Data layer

- **`web/src/lib/api/projects.ts`** — add `getDependencies(projectId)`,
  `createDependency(taskId, body)`, `deleteDependency(id)` to the existing
  `projectsApi` object.
- **Types** — add `TaskDependency`, `CreateTaskDependencyBody` to
  `web/src/lib/api/types.ts`.
- **Query keys** — `projectKeys.dependencies(projectId)`.
- **Hooks**:
  - `useTaskDependencies(projectId)` → bare array (D7), like
    `useProjectCategories`/`useProjectsByPriority`.
  - `useCreateDependency(projectId)` / `useDeleteDependency(projectId)` —
    invalidate `projectKeys.dependencies(projectId)` on success; the create
    mutation surfaces the backend's `400`/`409` message directly (cycle/
    duplicate/self), no client-side pre-validation duplicated (the picker
    only excludes the task itself from its own "Add" list — everything else
    is server-validated, D4).

### 6.5 Gantt rendering details

- **Task → bar mapping**: `id`, `name`; `start`/`end` from `plannedStart`/
  `plannedEnd` (parsed to local-midnight `Date`, day granularity only);
  `type: isMilestone ? 'milestone' : 'task'` (never `'project'`, D9);
  `progress: progressPercent`; `dependencies: [predecessorId, …]` from the
  fetched edges filtered to this task as successor.
- **Unscheduled split (D10)**: tasks missing either date are excluded from
  the `Task[]` passed to `gantt-task-react` and rendered instead in a plain
  list below the chart, each with a "Set dates" button opening
  `TaskFormDialog`.
- **Row order (D11)**: the same `topLevel`/`childrenByParentId` shape
  `useProjectTasks` already produces for `TaskTree` is flattened
  top-then-its-children, in `position` order; subtask rows get an indent via
  a name prefix (no library nesting/expand-collapse).
- **Violation glyph (D13)**: for each dependency, look up its
  `violatesConstraint` flag (from `useTaskDependencies`) and render a small
  warning icon on the arrow (or, if the library doesn't expose per-edge
  styling, on the successor bar) with a tooltip: "Starts before '<predecessor
  name>' finishes."
- **Drag → PATCH (D14)**: `onDateChange(task, children)` takes the dragged
  task's new `start`/`end`, converts back to ISO `LocalDate` strings, and
  calls `usePatchTask` with the full current task from the React Query cache,
  only `plannedStart`/`plannedEnd` (and, for a milestone, both set equal)
  overridden — exactly the whole-form contract `TaskFormDialog` already
  uses (M4 D15).
- **Today line**: `todayInZone(user.timezone)` (D8) passed as
  `gantt-task-react`'s today-highlight, consistent with the rest of the
  app's date handling.
- **Empty state**: "No tasks yet." when the project has none scheduled or
  unscheduled — mirrors the Tree/Board empty states.

---

## 7. Testing

### Backend

| Test | Type | Covers |
| --- | --- | --- |
| `TaskDependencyRepositoryTest` | `@DataJpaTest` + Testcontainers PG | `findByPredecessorIdAndSuccessorId`, `findByProjectId` (join-through-task scoping, excludes another project's edges), `deleteAllForTask` removes edges on either side. |
| `TaskDependencyServiceTest` | plain JUnit 5 + AssertJ, Mockito repo | self-dependency → `400`; cross-project predecessor → `400`; duplicate edge → `409`; a 3-edge cycle (`A→B`, `B→C`, attempt `C→A`) → `400`; a valid edge creates; `delete` 404s on a foreign task's edge; `deleteAllForTask` invoked correctly from `ProjectTaskService.delete`/`ProjectService.delete`'s cascades (verify via Mockito). |
| `ProjectTaskServiceTest` (extend) | plain JUnit 5 + AssertJ | `delete` now also calls `dependencies.deleteAllForTask` for the task and each cascaded child. |
| `ProjectServiceTest` (extend) | plain JUnit 5 + AssertJ | `delete`'s cascade also calls `dependencies.deleteAllForTask` per cascaded task. |
| `TaskDependencyControllerTest` | `@WebMvcTest` + `@Import({SecurityConfig, WebMvcConfig, CurrentUserArgumentResolver})`, `@MockitoBean` services/`JwtDecoder` | happy path for all three endpoints; `GET .../dependencies` returns `violatesConstraint=true` for a manufactured `FS` violation; no token → `401`; `400`/`409` body shapes. |
| `ProjectsFlowIntegrationTest` (extend) | `@SpringBootTest` + Testcontainers | after the existing flow (M4 §7), add two tasks with an `FS` dependency between them, assert `GET /projects/{id}/dependencies` reflects it; attempt the reverse edge and assert `400` (cycle); delete the predecessor task and assert the edge is gone. |
| `ArchitectureTest` | ArchUnit | existing `projectsInternalsArePrivate` rule already covers the new classes (still all under `com.kairon.projects..`) — no new rule needed. |

### Web

| Test | Covers |
| --- | --- |
| MSW handlers | add `GET /api/v1/projects/{id}/dependencies`, `POST /api/v1/tasks/{taskId}/dependencies`, `DELETE /api/v1/dependencies/{id}`, with an in-memory edge store shared with the existing task store. |
| `TaskDependencySection.test.tsx` | lists current predecessors with names resolved; adding one calls `useCreateDependency` and the new edge appears; removing calls `useDeleteDependency`; a `409`/`400` from the mock server surfaces as an inline error, not a thrown exception. |
| `GanttView.test.tsx` | scheduled tasks render as bars with the right `type` (`milestone` vs `task`); an unscheduled task appears in the "Unscheduled" list, not the chart; a manufactured violated edge shows the warning glyph; dragging a bar (simulated `onDateChange`) calls `usePatchTask` with the dragged dates and the task's other fields unchanged. |
| `projects.spec.ts` (Playwright, extend) | after the existing M4 flow, add a second task, create a dependency between them on the task form, switch to the Gantt tab, confirm the bar/arrow render, drag one bar's end and confirm the date persists on reload. |

---

## 8. Task breakdown (suggested order)

1. `V005__task_dependencies.sql` + `TaskDependency` entity + `TaskDependencyRepository` + repo tests.
2. Extract `TaskResolution` out of `ProjectTaskService` (D6) — pure refactor, existing tests must still pass unchanged.
3. `TaskDependencyService` (create incl. cycle rejection, delete, `deleteAllForTask`) + unit tests.
4. Wire `deleteAllForTask` into `ProjectTaskService.delete` and `ProjectService.delete`'s cascades (D5) + extend their existing tests.
5. `TaskDependencyController` + `TaskDependencyDtos`; controller tests.
6. Extend `ProjectsFlowIntegrationTest`.
7. Web: add `gantt-task-react`; `types.ts`/`projects.ts` additions, `useTaskDependencies` + mutations, MSW handlers.
8. Web: `TaskDependencySection` wired into `TaskFormDialog`.
9. Web: `GanttView` (static render first — bars, milestones, arrows, today line, progress, unscheduled list) as the third tab.
10. Web: wire `onDateChange` → `usePatchTask` (drag support).
11. Web tests + Playwright happy path.
12. Docs: tick the M5 boxes in `ROADMAP.md`; update `DESIGN.md` §5.1 to match the `/dependencies` endpoint (replacing the `/gantt` sketch, D7) and §7.1 if any detail shifted during implementation; update `CLAUDE.md`'s "Current state" projects-module bullet; mark this plan `Accepted`.

---

## 9. Open questions / risks

| # | Question | Recommendation |
| --- | --- | --- |
| Q1 | D7 drops the single `/gantt` endpoint `DESIGN.md` §5.1 documents — is composing three client-side fetches (project, tasks, dependencies) an acceptable trade for not building/testing a redundant combined read path? | **Resolved: yes.** Shipped as planned; `DESIGN.md` §5.1/§7.1 updated to match. |
| Q2 | `gantt-task-react` is unmaintained-adjacent (infrequent releases) — does its `dependencies`/`onDateChange`/`type: 'milestone'` API actually cover everything D12 assumes, or will it need patching/wrapping once someone's hands are on it? | **Resolved: fit is close enough, one gap found.** `Task.dependencies`/`onDateChange`/`type: 'milestone'`/`progress` map cleanly (D12/D14). The one gap: the library's today-shading always uses the browser's system clock (`new Date()` internal to `GridBody`) — there's no prop to override it, so D8's "`todayInZone` passed as the today-highlight" isn't literally possible. `GanttView` uses `todayInZone(user.timezone)` only to center the initial `viewDate`; the shaded line itself is the library's own, system-clock-based one. Not worth a custom-SVG fallback for a cosmetic detail. |
| Q3 | Should the "Unscheduled" list (D10) let a user drag a task from it directly onto the chart to give it dates, instead of only linking out to `TaskFormDialog`? | Not for M5 — the form link is enough for a first cut; a direct drag-to-schedule interaction is a plausible fast-follow once the plain Gantt is in daily use. |
| Q4 | D2 ships `FS`-only in the UI — when `SS`/`FF`/`SF` are eventually exposed, does the violation check (D13) need to generalize beyond the `FS` formula now, or is that a later change? | Later change — `violatesConstraint` is scoped to `FS` explicitly in D13's spec; extending it is a small, isolated addition to `TaskDependencyService.list` whenever the other types actually ship a form control. |
| Q5 *(found during implementation)* | Can `TaskDependencySection`'s "Add" `Select` (D1) be exercised by opening it in a Vitest/jsdom component test? | No — Radix Select's popper-positioning code enters a synchronous loop under jsdom's zero-size layout (every element reports a 0×0 rect there) and hangs the test process rather than failing it. `TaskDependencySection.test.tsx` covers list/remove without opening the picker; the add flow and the cycle/duplicate error surfacing are covered instead by `e2e/projects.spec.ts`'s M5 test, which runs against a real browser. |

---

## 10. Doc updates this milestone produces

- `ROADMAP.md` — tick the four M5 checkboxes once implemented.
- `DESIGN.md` §5.1 — replace the `GET /projects/{id}/gantt` row with
  `GET /projects/{id}/dependencies` + the two dependency-mutation endpoints
  (D7's deviation, ratified above).
- `DESIGN.md` §7.1 — confirm it still matches once implemented (it already
  describes exactly this scope; no rewrite expected, just a status check).
- `DATA_MODEL.md` — already describes `task_dependency`; confirm the DDL
  matches (type/self-reference/uniqueness constraints).
- `CLAUDE.md` "Current state" — extend the `projects` module bullet with
  `TaskDependency`/`TaskDependencyService`/`TaskDependencyController` and the
  web `GanttView`/`TaskDependencySection`, matching the existing level of
  detail; update the migrations list with `V005`.
- This file — flip `Status: Draft` → `Accepted` once Q1–Q4 are resolved and
  the milestone is implemented.
