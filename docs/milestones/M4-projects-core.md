# M4 — Projects (core) — implementation plan

Status: **Accepted**. Companion to [`../ROADMAP.md`](../ROADMAP.md) (M4 acceptance
criteria), [`../DESIGN.md`](../DESIGN.md) §2.3 / §3.2 / §5 / §7.1, and
[`../DATA_MODEL.md`](../DATA_MODEL.md) (`project_category`, `project`,
`project_task`). This is the detailed plan for the milestone; the roadmap
checkboxes are the acceptance test. Follows the structure and conventions of
[`M2-daily-todo.md`](M2-daily-todo.md) / [`M3-daily-journal.md`](M3-daily-journal.md) —
same module shape, same hand-rolled mapping, same authz/error rules.

M4 delivers the third product feature: small personal **projects**, optionally
grouped under a user-managed **category** (an "epic"-like label with its own
colour and display order), with an at-most-2-level task breakdown, a
**list/tree** view, and a **status board**. No timeline yet — the Gantt chart
and `task_dependency` are M5.

---

## 1. Scope

### In

- `project_category` + `project` + `project_task` schema (`V004__projects.sql`),
  including the FK from `todo_item.source_project_task_id` → `project_task`
  that M2 left as a bare `uuid` (M2 §3).
- `projects` backend module: category CRUD + `:reorder`; project CRUD (with an
  optional `categoryId`); task CRUD with ≤2-level hierarchy, estimates,
  status, progress, `:reorder`; a minimal `projects.api` port for M6/M8.
- `GET /project-categories`, `POST /project-categories`,
  `PATCH /project-categories/{id}`, `DELETE /project-categories/{id}`,
  `POST /project-categories:reorder`.
- `GET /projects` (paginated, filterable by `status` and `categoryId`),
  `GET /projects/{id}`, `POST /projects`, `PATCH /projects/{id}`,
  `DELETE /projects/{id}`.
- `GET /projects/{id}/tasks` (paginated), `POST /projects/{id}/tasks`,
  `PATCH /tasks/{taskId}`, `DELETE /tasks/{taskId}`,
  `POST /projects/{id}/tasks:reorder`.
- Web: a project list page grouped by category (in category display order,
  uncategorized last), a category-management dialog (add/rename/recolor/
  reorder/delete), a project detail page with a **list/tree** view
  (expand/collapse subtasks, drag reorder within a level) and a **board** view
  (drag cards between status columns).
- Tests: `@DataJpaTest` + Testcontainers repo tests, service unit tests, MockMvc
  controller tests, an `@SpringBootTest` flow test, ArchUnit rule for the new
  module, Vitest component tests, a Playwright happy path.

### Out (deferred, with the milestone that picks them up)

- `task_dependency`, the Gantt chart, cycle detection, `FS`/`SS`/`FF`/`SF`
  edges — M5.
- Assisted scheduling (forward pass, critical path) — M5/M7 (DESIGN §7.2).
- "Today" aggregation consuming `projects.api` — M6 builds the screen; this
  milestone only adds the port method M6 needs (D6 below).
- Auto-computed `progress_percent` rollup from subtask status — manual field
  for M4 (D3); a clean follow-up once there's real usage to learn from.
- Any computation from `size` (e.g. deriving a default `estimate_hours` sum,
  or feeding M5's scheduling) — D16 keeps it a purely informational,
  at-a-glance label for M4; wiring it into anything computed is a separate
  decision for whenever there's a concrete reason to.
- Drag-to-nest (dragging one task row onto another to reparent it) — M4 uses
  an edit-dialog dropdown instead (D5); tree drag stays reorder-only.
- Tags, project templates, CSV/Markdown export — backlog.

---

## 2. Decisions locked for this milestone

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | **`GET /projects` and `GET /projects/{id}/tasks` return the standard paginated envelope** (`{ content, page, totalElements }`), following `DESIGN.md` §5's default literally — unlike Todo's day list (M2 D/Q1) or Journal's day list, a project's task tree is not bounded by construction the same way. In practice the web client requests one large page (`size` defaulting high, e.g. 200) since the tree/board views need the whole project's tasks to render; real pagination only bites past that, which isn't the common personal-scale case. | User call; matches the documented default, keeps room if a project or the project list ever grows past a screenful. |
| D2 | **`project_task.position` is scoped per `(project_id, parent_task_id)`** — top-level tasks are one ordered sequence, and each parent's subtasks are their own. `POST /projects/{id}/tasks:reorder` takes `{ parentTaskId?, orderedIds }` and validates the id set against exactly that sibling group, same shape as Todo's day-scoped `:reorder` (M2 §4.4). | Matches how a tree UI actually drags (siblings only); a flat per-project sequence would let reordering a subtask reshuffle unrelated top-level tasks. |
| D3 | **`progress_percent` is a manual field for M4** — the task form has a 0–100 number input; nothing recomputes it from subtask status. | Matches `DATA_MODEL.md` (a plain column); auto-rollup is a same-shape enhancement to add once there's real multi-task usage, and doesn't need a migration. |
| D4 | **The status board supports drag between columns**, via `@dnd-kit/core`/`@dnd-kit/sortable` (already a dependency from Todo's reorder, M2 §6.1). Dropping a card in a new column `PATCH`es `{ status }`; dropping within a column reorders `position` at that task's level (D2). | Low incremental cost given dnd-kit is already wired; matches the drag + keyboard precedent set in M2. |
| D5 | **Reparenting a task happens through the edit dialog's "Parent task" dropdown** (`PATCH /tasks/{taskId} { parentTaskId }`), not by dragging one tree row onto another. The service enforces the ≤2-level rule (§4.4). | Smaller, keyboard-friendly, reuses the existing form-edit pattern instead of adding nest-vs-reorder drag disambiguation to the tree for a first cut. |
| D6 | **`projects.api` port is added now, sized to what M6 needs**: `List<ProjectTaskView> dueOrOverdue(UserId, LocalDate day)` — tasks whose `[planned_start, planned_end]` window contains `day`, or that are overdue (`planned_end < day`) and not `DONE`. Implemented by `ProjectTaskService`, mirrors how `TodoApi`/`JournalApi` were sized to their first consumer (M2 D/§4.1, M3 D6). | Cheap now, sets the module boundary and ArchUnit rule; M6 then only *consumes* an existing port instead of needing a cross-module change to add one. |
| D7 | **Hand-rolled mapping** (`ProjectMapper`/`ProjectTaskMapper`, static factory methods), following the `todo`/`journal` precedent (M2 D5, M3 D5), not MapStruct. | Consistency; MapStruct still isn't on the classpath. |
| D8 | **Deleting a project cascades a soft-delete to its tasks** in the same transaction (`DATA_MODEL.md`'s note under `project_task`). `ProjectService.delete` sets `project.deletedAt`, then bulk-sets `deletedAt` on every non-deleted task in that project. `task_dependency` isn't in scope yet (M5 will extend the cascade). | Matches the documented data-model rule; a project with tasks shouldn't leave orphaned-looking rows visible to some other query path. |
| D9 | **Milestone dates**: setting `isMilestone = true` requires at least one of `plannedStart`/`plannedEnd` in the same request; the service then forces the other to match (`ProjectTask.markMilestone(LocalDate)` sets both). Unsetting `isMilestone` leaves the dates as-is (now just an equal, non-milestone start/end). | Keeps `DATA_MODEL.md`'s invariant (`is_milestone ⇒ planned_start == planned_end`) enforced in one place rather than trusted to the client. |
| D10 | **`GET /projects` defaults to hiding `ARCHIVED` projects**; an explicit `status=ARCHIVED` (or `status=` omitted plus a separate `includeArchived=true` flag) is needed to see them. Mirrors Todo hiding cancelled items behind a toggle (M2 §6.7). | An archived project isn't "current work" — keeping it out of the default list view is the same judgment call M2 already made for a different kind of done-ness. |
| D11 | **The board shows every task, top-level and subtask alike**, as its own card grouped by `status`; a subtask's card carries a small "in <parent task name>" chip for context. | Status is a per-task field independent of hierarchy — hiding subtasks from the board would make it lie about what's actually blocked/in-progress. |
| D12 | **Category is its own entity, `project_category`** (`id`, `userId`, `name`, `color`, `position`), referenced by a nullable `project.categoryId` — not a plain string field on `project`. | User call: the example category list has a deliberate, non-alphabetical display order ("Miscellaneous small improvements" pinned first) and gets renamed/recoloured as a unit — both need an identity of their own, not a label copied onto every project row. |
| D13 | **`GET /project-categories` returns a bare array**, not the paginated envelope `project`/`project_task` use (D1). | Same exception D1 itself declines for projects: a user's category count is small and fully ordered by construction (like Todo's day list, M2 D/Q1), and the reorder UI needs the complete set every time regardless — pagination has no use here. |
| D14 | **Deleting a category is a real hard `DELETE`, not a soft delete** — `project_category` carries no `deletedAt`. Any project referencing it has `categoryId` set to `null` (`ON DELETE SET NULL`), i.e. the projects become uncategorized, not deleted. | `project_category` isn't in the mobile-sync soft-delete set (`DATA_MODEL.md` conventions) — it's small reference data a client just refetches whole, so there's nothing a soft delete would protect that confirmation-before-delete in the UI doesn't already cover. |
| D15 | **`PATCH /projects/{id}` and `PATCH /tasks/{taskId}` are whole-form saves, not sparse per-field patches** — the edit dialog always submits every field it owns (nulls included), and the service overwrites rather than skipping `null`s. | Follows Journal's precedent, not Todo's: Todo's inline per-field edits treat an absent/`null` field as "leave unchanged" (M2 §4.4), which can't express "clear `categoryId` back to uncategorized" for a non-string field — there's no empty-string sentinel for a UUID. A modal form (D5's edit dialog) naturally has the complete field set in hand on every save anyway, so whole-form overwrite (M3 D1's "the editor always saves the whole entry together") is both correct here and simpler than adding a nullable-wrapper type just for `categoryId`. |
| D16 | **`project.size` is a plain nullable enum** (`XS`/`S`/`M`/`L`/`XL`), not its own entity — unlike category (D12), the scale is fixed and universal (not user-defined, no name/colour/order to manage), so it's exactly the same shape as `ProjectStatus`. No default; an unsized project just shows no size badge. | A t-shirt scale is a rough, at-a-glance sense of scope — distinct from `estimate_hours` on individual tasks (a real number, only meaningful once the work is broken down) — and doesn't need the per-user customization category needed. |
| D17 | **`GET /projects`'s page-size query param is renamed `pageSize`**, freeing `size` for the t-shirt-size filter. `ProjectController#list` takes `page`/`pageSize` as plain `@RequestParam`s and resolves `sort` **separately** as its own `Sort` method parameter (Spring's `SortHandlerMethodArgumentResolver` binds a bare `Sort sort` to the standard `sort` request param independently of `Pageable` — only `page`/`size` needed the rename, not `sort`), then builds `PageRequest.of(page, pageSize, sort)` itself instead of an auto-resolved `Pageable`. Every other paginated endpoint (`GET /projects/{id}/tasks`, etc.) keeps Spring's normal `size`. | The collision is real and easy to miss until a query string with both meanings breaks; renaming the one endpoint that has both is simpler than renaming project sizing's query param to something less obvious like `projectSize`. |
| D18 | **`project.priority_rank` is a manual, drag-ordered rank — a sparse `integer`, not a severity scale.** Unlike `todo_item.priority` (a 0–3 "how important is this" value the user picks per item), `priority_rank` only has meaning *relative to* the user's other projects: lower sorts first (most important), and the only way to change it is dragging, exactly like `todo_item.position`/`project_task.position`. There is no "priority level" to select in `ProjectFormDialog` — no `priority` field appears in `POST /projects` or `PATCH /projects/{id}` at all, matching how Todo/task `position` is never part of their `PATCH` bodies either (M2 §4.4, §4.4 here). | User clarification mid-review: "render the project according to their priority and then drag them in the priority I want" is an ordering operation, not a category picker — the existing `position` pattern already in this codebase (three other tables use it) is the correct shape, not a copy of Todo's severity scale. |
| D19 | **Priority ranking is global across all of a user's projects, not scoped per category.** Reordering flattens the category-grouped list into one cross-category, drag-orderable list for the duration of "Sort by: Priority" (§6.8); every other sort mode keeps the category grouping. | User call: a single ranking answers "what matters most right now" across the whole project list; a per-category rank never can. |
| D20 | **`POST /projects:reorder` (`{orderedIds}`) rewrites `priority_rank` to `100, 200, …`** over exactly the user's current non-`ARCHIVED`, non-deleted projects — same validate-the-full-set-or-400 contract as Todo's day `:reorder` (M2 §4.4) and category `:reorder` (D13's sibling). `ProjectService.create` always appends a new project at the bottom (`priority_rank = max(priority_rank over that same set) + 100`, or `100` if empty) — nothing sets an initial rank explicitly, same as Todo/task creation never taking a client-supplied `position`. | Consistency with the three other `:reorder` endpoints already in this milestone (categories, tasks) and M2's Todo precedent; "append at the bottom, promote by dragging" is a sensible default (a brand-new project isn't presumptively your top priority). |
| D21 | **Drag-reorder is only offered when the status and size filters are both at their defaults** (the default "not archived" status view, size = "Any size") — narrowing either filter still sorts by `priority_rank` (read-only) but hides the drag handles and disables `:reorder`. | `:reorder`'s all-or-nothing validation (D20) needs the dragged set to exactly equal the *whole* rankable scope; a filtered subset can't be unambiguously spliced back into a single global rank without either rewriting ranks for rows the user can't currently see or leaving the drop ambiguous. Restricting dragging to the unfiltered view sidesteps that rather than solving it with more machinery. |
| D22 | **`project_task` has no priority field at all, distinct or otherwise** — a task's `position` (already specified, §4.4/D2) *is* its priority within its sibling group. There's nothing new to add to the schema, `TaskFormDialog`, or the API: `TaskTree`'s existing drag reorder (§6.6) already is "set a task's priority." | User clarification mid-review, explicitly the opposite call from D18/D19 at the project level: for tasks, "order" and "priority" are asked to be **the same concept**, not two independent ones — unlike `todo_item`, which deliberately keeps a severity `priority` separate from its drag `position`. |
| D23 | **`TaskTree` gets an always-visible inline quick-add row** (`TaskQuickAdd`) at the bottom of the top-level list, and one more at the bottom of each expanded parent's subtask list — reusing Todo's exact `QuickAdd` pattern (`web/src/features/todo/QuickAdd.tsx`: a plain `Input`, Enter creates and clears while keeping focus, no dialog). It calls the **existing** `POST /projects/{id}/tasks` with just `{ name, parentTaskId? }` — no new endpoint or backend change, since every other field on `CreateCommand` was already optional (§4.4). The toolbar's "+ New task" (opens `TaskFormDialog`) stays, for when every field matters up front. | User call: rapid title-only capture for a backlog-style brain-dump, refined later — the same job Todo's `QuickAdd` already does for the day list (M2 §6.4). No schema or API work needed; this is a pure web-layer addition, since `CreateCommand`'s optional fields already cover a bare title. |

---

## 3. Data model — `V004__projects.sql`

New migration `backend/src/main/resources/db/migration/V004__projects.sql`.
Columns per [`../DATA_MODEL.md`](../DATA_MODEL.md) →
`project_category` / `project` / `project_task`.

```sql
-- Projects and their tasks (module: projects). A project belongs to one user
-- and may be grouped under a project_category (D12); project_task.parent_task_id
-- gives an at-most-2-level breakdown, enforced in the service
-- (docs/milestones/M4-projects-core.md D5/§4.4), not the schema.
-- See docs/DATA_MODEL.md (project_category, project, project_task) and
-- docs/DESIGN.md §2.3.

CREATE TABLE project_category (
    id           uuid          PRIMARY KEY,
    user_id      uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name         varchar(100)  NOT NULL,
    color        varchar(7)    NOT NULL DEFAULT '#6366f1',
    position     integer       NOT NULL,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    version      bigint        NOT NULL DEFAULT 0,

    CONSTRAINT project_category_color_check CHECK (color ~ '^#[0-9a-fA-F]{6}$'),
    CONSTRAINT project_category_user_name_uk UNIQUE (user_id, name)
);

CREATE INDEX project_category_user_position_idx ON project_category (user_id, position);

CREATE TABLE project (
    id           uuid          PRIMARY KEY,
    user_id      uuid          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    category_id  uuid          REFERENCES project_category (id) ON DELETE SET NULL,
    name         varchar(200)  NOT NULL,
    description  text,
    status       varchar(20)   NOT NULL DEFAULT 'PLANNING',
    size         varchar(2),
    priority_rank integer      NOT NULL,
    color        varchar(7)    NOT NULL DEFAULT '#6366f1',
    start_date   date,
    end_date     date,
    actual_start date,
    actual_end   date,
    deleted_at   timestamptz,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    version      bigint        NOT NULL DEFAULT 0,

    CONSTRAINT project_status_check CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'DONE', 'ARCHIVED')),
    CONSTRAINT project_size_check   CHECK (size IS NULL OR size IN ('XS', 'S', 'M', 'L', 'XL')),
    CONSTRAINT project_color_check  CHECK (color ~ '^#[0-9a-fA-F]{6}$')
);

CREATE INDEX project_user_status_idx  ON project (user_id, status)        WHERE deleted_at IS NULL;
CREATE INDEX project_category_idx    ON project (category_id)            WHERE deleted_at IS NULL;
CREATE INDEX project_priority_rank_idx ON project (user_id, priority_rank) WHERE deleted_at IS NULL AND status <> 'ARCHIVED';

CREATE TABLE project_task (
    id                uuid          PRIMARY KEY,
    project_id        uuid          NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    parent_task_id    uuid          REFERENCES project_task (id) ON DELETE CASCADE,
    name              varchar(300)  NOT NULL,
    description       text,
    status            varchar(20)   NOT NULL DEFAULT 'TODO',
    is_milestone      boolean       NOT NULL DEFAULT false,
    planned_start     date,
    planned_end       date,
    estimate_hours    numeric(6,2),
    actual_hours      numeric(6,2),
    progress_percent  smallint      NOT NULL DEFAULT 0,
    position          integer       NOT NULL,
    deleted_at        timestamptz,
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    version           bigint        NOT NULL DEFAULT 0,

    CONSTRAINT project_task_status_check   CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE')),
    CONSTRAINT project_task_progress_check CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT project_task_estimate_check CHECK (estimate_hours IS NULL OR estimate_hours >= 0),
    CONSTRAINT project_task_actual_check   CHECK (actual_hours IS NULL OR actual_hours >= 0)
);

CREATE INDEX project_task_project_idx ON project_task (project_id)     WHERE deleted_at IS NULL;
CREATE INDEX project_task_parent_idx  ON project_task (parent_task_id) WHERE deleted_at IS NULL;

-- M2 left this as a bare uuid because project_task didn't exist yet (M2 §3).
ALTER TABLE todo_item
    ADD CONSTRAINT todo_item_source_project_task_fk
    FOREIGN KEY (source_project_task_id) REFERENCES project_task (id) ON DELETE SET NULL;
```

Notes:

- `project_task` has **no `user_id` column** (per `DATA_MODEL.md`) — a task is
  owned transitively through its project. Every task query is authorized by
  first resolving the project under the caller's `user_id`, then scoping the
  task by `project_id` (§4.4), never by a direct `user_id` filter on
  `project_task`.
- `parent_task_id` is `ON DELETE CASCADE` (self-referencing) and `project_id`
  is `ON DELETE CASCADE` — both only matter for a hard delete, which the app
  never performs outside tests; production deletes are the soft-delete +
  cascade in D8.
- The ≤2-level rule is **not** a DB constraint (no cheap recursive check) —
  it's enforced in `ProjectTaskService` (§4.4) the same way the rollover
  look-back window is service-enforced rather than schema-enforced.
- `color` defaults to an indigo swatch; the check constraint just guards the
  `#rrggbb` shape client-side pickers already produce.
- `project_category_user_name_uk` (D12) is a backstop; `ProjectCategoryService`
  checks `existsByUserIdAndName` before insert/rename and throws
  `ApiException.conflict` (409), following the `AuthService` registration
  precedent for `app_user.email` rather than catching a constraint violation.
- `project.category_id` is `ON DELETE SET NULL` (D14) — deleting a category
  never blocks on or cascades to the projects that referenced it.
- `priority_rank` has no `DEFAULT` — every `INSERT` goes through
  `ProjectService.create`, which always computes it (D20), the same as
  `todo_item.position`/`project_task.position` never default in their
  tables either.

---

## 4. Backend

### 4.1 Package layout — `com.kairon.projects`

```
com.kairon.projects
├── api/            ProjectsApi (port), ProjectView, ProjectTaskView     ← other modules depend only on this
├── domain/         ProjectCategory (@Entity), Project (@Entity), ProjectStatus,
│                   ProjectTask (@Entity), ProjectTaskStatus
├── repo/           ProjectCategoryRepository, ProjectRepository, ProjectTaskRepository
├── app/            ProjectCategoryService, ProjectService, ProjectTaskService, ProjectsProperties
├── config/         ProjectsConfig
└── web/            ProjectCategoryController, ProjectController, ProjectTaskController,
                    ProjectCategoryDtos, ProjectDtos, ProjectTaskDtos
```

- **`ProjectsApi`** (D6): `List<ProjectTaskView> dueOrOverdue(UserId userId, LocalDate day)`.
  Implemented by `ProjectTaskService`. One method, sized to what M6's Today
  screen needs (DESIGN §2.4) — the same "just what the next module needs"
  sizing M2/M3 used for `TodoApi`/`JournalApi`.

### 4.2 Domain

- **`ProjectStatus`** — `PLANNING`, `ACTIVE`, `ON_HOLD`, `DONE`, `ARCHIVED`.
- **`ProjectSize`** — `XS`, `S`, `M`, `L`, `XL` (D16).
- **`ProjectTaskStatus`** — `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`.
- **`ProjectCategory`** `@Entity` in `projects.domain`, same conventions
  (no `deletedAt` — D14):
  - `static ProjectCategory create(UUID userId, String name, String color, int position)`
  - `void rename(String)`, `void recolor(String hex)`, `void moveTo(int position)`
- **`Project`** `@Entity` in `projects.domain`, following `TodoItem`/`JournalEntry`
  conventions: `@Id UUID` from `Uuidv7.next()`, `@Version long`,
  `@CreationTimestamp`/`@UpdateTimestamp`, private all-args constructor + static
  factories, behaviour methods rather than setters:
  - `static Project create(UUID userId, UUID categoryId, String name, String description, String color, ProjectSize size, int priorityRank, LocalDate startDate, LocalDate endDate)`
  - `void rename(String)`, `void editDescription(String)`, `void recolor(String hex)`, `void recategorize(UUID categoryId)`, `void resize(ProjectSize size)` (nullable — clears sizing)
  - `void moveTo(int priorityRank)` — set only by `ProjectService.reorder` (D20); no clamping, a plain sparse position exactly like `TodoItem.moveTo`
  - `void reschedule(LocalDate start, LocalDate end)` (planned window)
  - `void markActualStart(LocalDate)`, `void markActualEnd(LocalDate)`
  - `void changeStatus(ProjectStatus)`
  - `void softDelete(Instant when)`
- **`ProjectTask`** `@Entity` in `projects.domain`, same conventions:
  - `static ProjectTask create(UUID projectId, UUID parentTaskId, String name, String description, int position)`
  - `void rename(String)`, `void editDescription(String)`
  - `void changeStatus(ProjectTaskStatus)`
  - `void setEstimateHours(BigDecimal)`, `void setActualHours(BigDecimal)`
  - `void setProgress(int percent)` — validates `0..100`
  - `void reparent(UUID newParentTaskId)` — depth validated by the *service*
    (§4.4) before this is called; the entity just assigns
  - `void markMilestone(LocalDate date)` → `isMilestone=true`,
    `plannedStart=plannedEnd=date`; `void unmarkMilestone()` → `isMilestone=false`
  - `void reschedule(LocalDate start, LocalDate end)` — rejects a call that
    would leave `isMilestone=true` with unequal dates (use `markMilestone`
    instead)
  - `void moveTo(int position)`
  - `void softDelete(Instant when)`

### 4.3 Repositories

```java
// ProjectCategoryRepository extends JpaRepository<ProjectCategory, UUID>
List<ProjectCategory> findByUserIdOrderByPositionAsc(UUID userId);
Optional<ProjectCategory> findByIdAndUserId(UUID id, UUID userId);
boolean existsByUserIdAndName(UUID userId, String name);                       // create conflict check (D12 note, §3)
boolean existsByUserIdAndNameAndIdNot(UUID userId, String name, UUID selfId);   // rename conflict check, excluding itself
```

```java
// ProjectRepository extends JpaRepository<Project, UUID>
Optional<Project> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

// One query, nullable filters — avoids a 2^n explosion of derived-query method
// names for status × categoryId × size combinations.
@Query("""
        SELECT p FROM Project p
        WHERE p.userId = :userId AND p.deletedAt IS NULL
          AND (:status IS NULL OR p.status = :status)
          AND (:categoryId IS NULL OR p.categoryId = :categoryId)
          AND (:size IS NULL OR p.size = :size)
        """)
Page<Project> search(UUID userId, ProjectStatus status, UUID categoryId, ProjectSize size, Pageable pageable);

// Backs the flattened Priority sort mode (D19/D21) and ProjectService.reorder's target
// set (D20): every non-deleted, non-ARCHIVED project for the user, rank-ordered.
List<Project> findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc(UUID userId, ProjectStatus excludedStatus);
```

```java
// ProjectTaskRepository extends JpaRepository<ProjectTask, UUID>
Page<ProjectTask> findByProjectIdAndDeletedAtIsNullOrderByParentTaskIdAscPositionAsc(UUID projectId, Pageable pageable);

List<ProjectTask> findByProjectIdAndParentTaskIdAndDeletedAtIsNullOrderByPositionAsc(UUID projectId, UUID parentTaskId);

Optional<ProjectTask> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);

boolean existsByParentTaskIdAndDeletedAtIsNull(UUID parentTaskId);   // reparent guard: "does this task have children?"

List<ProjectTask> findByProjectIdAndDeletedAtIsNull(UUID projectId); // D8's cascade soft-delete

// Backs ProjectsApi.dueOrOverdue — joins the owning project for the user_id
// scope project_task itself doesn't carry.
@Query("""
        SELECT t FROM ProjectTask t JOIN Project p ON t.projectId = p.id
        WHERE p.userId = :userId AND p.deletedAt IS NULL AND t.deletedAt IS NULL
          AND ((t.plannedStart <= :day AND t.plannedEnd >= :day)
               OR (t.plannedEnd < :day AND t.status <> 'DONE'))
        ORDER BY t.plannedEnd ASC
        """)
List<ProjectTask> findDueOrOverdue(UUID userId, LocalDate day);
```

### 4.4 Application services

**`ProjectCategoryService`** (`@Service`, methods `@Transactional`; reads
`readOnly = true`). Every method takes a `UserId` and 404s on someone else's
row.

| Method | Notes |
| --- | --- |
| `list(UserId)` | position-ordered; the whole set (D13) — no pagination. |
| `create(UserId, CreateCommand{name, color?})` | `existsByUserIdAndName` → 409 (§3 note); `position = (max position for user) + 100`, or `100` if empty. |
| `patch(UserId, id, PatchCommand{name?, color?, expectedVersion?})` | renaming re-checks the uniqueness guard (excluding itself); stale `expectedVersion` → 409. |
| `reorder(UserId, orderedIds)` | validates the id set exactly equals the user's categories, rewrites `position` to `100, 200, …` — same shape as Todo's day `:reorder` (M2 §4.4), scoped to the user instead of a day. |
| `delete(UserId, id)` | hard `DELETE` (D14/D12) — referencing projects fall back to `categoryId = null` via the FK, no application-level cascade needed. |

**`ProjectService`** (`@Service`, methods `@Transactional`; reads
`readOnly = true`). Every method takes a `UserId` and 404s (never 403) on
someone else's row via `ApiException.notFound(...)`, per DESIGN §3.2.

| Method | Notes |
| --- | --- |
| `list(UserId, ProjectStatus? status, UUID? categoryId, ProjectSize? size, Pageable)` | `status` filters; when absent, excludes `ARCHIVED` (D10). `categoryId`/`size` further narrow; all three go through `ProjectRepository.search` (§4.3). |
| `listByPriority(UserId)` | the flattened, rank-ordered view (D19) — every non-`ARCHIVED`, non-deleted project via `findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc`. No pagination — same "it's a small personal-scale set" reasoning as category (D13). |
| `get(UserId, id)` | single project. |
| `create(UserId, CreateCommand{categoryId?, size?, ...})` | defaults `status=PLANNING`, `color` from the command or the default swatch, `size=null` (unsized, D16), `priorityRank = max(priorityRank over listByPriority's set) + 100` (or `100` if empty, D20); `categoryId`, if given, must resolve under the same `UserId` (404 otherwise — same leak-avoidance rule as any foreign row). |
| `patch(UserId, id, PatchCommand)` | whole-form overwrite of `categoryId`, `name`, `description`, `status`, `size`, `color`, `startDate`, `endDate`, `actualStart`, `actualEnd` (D15) — `categoryId`/`size: null` clear them; **does not** touch `priorityRank` (D18) — that's `reorder`'s job only; optional `expectedVersion` → 409 on mismatch. |
| `reorder(UserId, orderedIds)` | validates `orderedIds` exactly equals `listByPriority(UserId)`'s current id set (D20/D21) → `400` otherwise; rewrites `priorityRank` to `100, 200, …` in the given order. |
| `delete(UserId, id)` | soft-deletes the project, then bulk soft-deletes every non-deleted task in it (D8). |

**`ProjectTaskService`** (`@Service`, `@Transactional`; reads `readOnly = true`).
Every method resolves the **project** first under `UserId` (404 if missing/
foreign), then scopes the task by `projectId` — no direct `user_id` filter
exists on `project_task` (§3).

| Method | Notes |
| --- | --- |
| `list(UserId, projectId, Pageable)` | full project tree (ordered `parentTaskId, position` — client assembles the hierarchy from `parentTaskId`, since there are only 2 levels). |
| `create(UserId, projectId, CreateCommand{name, description?, parentTaskId?, ...})` | validates `parentTaskId` per the depth rule below; `position = (max position for (project, parentTaskId)) + 100`, or `100` if empty. |
| `patch(UserId, projectId, taskId, PatchCommand)` | whole-form overwrite (D15) incl. `status`, `estimateHours`, `actualHours`, `progressPercent`, `plannedStart`/`plannedEnd`, `isMilestone`, and **`parentTaskId`** (reparenting, D5 — `null` moves the task back to top-level, unambiguous under D15) — depth-validated the same way as create; stale `expectedVersion` → 409. |
| `reorder(UserId, projectId, parentTaskId?, orderedIds)` | validates the id set exactly equals that sibling group (D2), rewrites `position` to `100, 200, …`. |
| `delete(UserId, projectId, taskId)` | soft-deletes the task **and** its subtasks (if any) — same cascade shape as D8, one level deep since a subtask can't itself have children. |

**Depth rule** (backs `create`/`patch`'s `parentTaskId`, D5/D9's note on
schema vs. service enforcement):

1. `parentTaskId == null` → always allowed (top-level).
2. `parentTaskId != null` → the referenced task must exist in the same
   project, not be deleted, and **itself have `parentTaskId == null`**
   (i.e. only a top-level task can be a parent) — otherwise `400`
   ("a subtask can't have its own subtasks").
3. A task that currently **has children**
   (`existsByParentTaskIdAndDeletedAtIsNull`) cannot be given a non-null
   `parentTaskId` — that would push its children to depth 3 — otherwise `400`.

### 4.5 Web layer

**`ProjectCategoryController`** `@RestController @RequestMapping("/api/v1")`
(stays at `/api/v1` for the same `:reorder`-path-combiner reason as
`ProjectTaskController` below) — `GET /project-categories`,
`POST /project-categories`, `PATCH /project-categories/{id}`,
`DELETE /project-categories/{id}`, `POST /project-categories:reorder`.

**`ProjectController`** `@RestController @RequestMapping("/api/v1")` (stays at
`/api/v1`, not `/api/v1/projects`, for the same `:reorder`-path-combiner
reason as `ProjectTaskController` — `POST /projects:reorder` needs no slash
before the colon) — `GET /projects`, `POST /projects`, `GET /projects/{id}`,
`PATCH /projects/{id}`, `DELETE /projects/{id}`, `POST /projects:reorder`
(D20). `GET /projects` takes `page`/`pageSize` as plain `@RequestParam`s, a
`Sort sort` parameter resolved separately by Spring's
`SortHandlerMethodArgumentResolver` (still the standard `sort` request
param — no rename needed there), and builds its own
`PageRequest.of(page, pageSize, sort)` (D17) rather than an auto-resolved
`Pageable`, since `size` is taken by the t-shirt-size filter on this one
endpoint. There's also `GET /projects/priority-ordered` (backs
`listByPriority`, D19) — a bare array, no pagination.

**`ProjectTaskController`** `@RestController @RequestMapping("/api/v1")` (same
reasoning as `TodoController`/`JournalController` — stays at `/api/v1` so
`:reorder` resolves without the path-combiner inserting a slash before the
colon) — `GET /projects/{id}/tasks`, `POST /projects/{id}/tasks`,
`PATCH /tasks/{taskId}`, `DELETE /tasks/{taskId}`,
`POST /projects/{id}/tasks:reorder`.

**`ProjectCategoryDtos`** / **`ProjectDtos`** / **`ProjectTaskDtos`** —
package-private `final class`es, same shape as `TodoDtos`/`JournalDtos`:
request records with Bean Validation, `...Response.from(...)` factories.
Controllers never see an entity (ArchUnit-enforced).

**`ProjectsConfig`** `@Configuration` in `projects.config` with
`@EnableConfigurationProperties(ProjectsProperties.class)` —
`kairon.projects.task-list-default-size` (default 200, backs D1's "one big
page" client behaviour with a server-side default `Pageable` size when the
client doesn't specify one).

### 4.6 Authorization & errors

Same rules as Todo/Journal (M2 §4.6 / M3 §4.6): a project resolves by
`userId`; a task resolves by first resolving its project under `userId`, then
by `projectId`; missing/foreign row → 404; stale `expectedVersion` → 409;
validation failures → `application/problem+json` (already global); the depth
rule (§4.4) also renders as `400` via the existing `ApiException.badRequest`
path.

---

## 5. API contract

Base path `/api/v1`. Bearer access token on every call. Dates are ISO
`LocalDate` strings.

### `ProjectCategory` response shape

```json
{
  "id": "018f…",
  "name": "Home Improvements",
  "color": "#f59e0b",
  "position": 200,
  "createdAt": "2026-08-30T09:00:00Z",
  "updatedAt": "2026-08-30T09:00:00Z",
  "version": 0
}
```

### `Project` response shape

```json
{
  "id": "018f…",
  "categoryId": "018f…",
  "name": "Home network overhaul",
  "description": "Replace the switch stack, run new drops.",
  "status": "ACTIVE",
  "size": "M",
  "priorityRank": 300,
  "color": "#6366f1",
  "startDate": "2026-09-01",
  "endDate": "2026-10-15",
  "actualStart": "2026-09-03",
  "actualEnd": null,
  "createdAt": "2026-08-30T09:00:00Z",
  "updatedAt": "2026-09-10T18:22:00Z",
  "version": 3
}
```

### `ProjectTask` response shape

```json
{
  "id": "018f…",
  "projectId": "018f…",
  "parentTaskId": null,
  "name": "Run new drops",
  "description": null,
  "status": "IN_PROGRESS",
  "isMilestone": false,
  "plannedStart": "2026-09-08",
  "plannedEnd": "2026-09-12",
  "estimateHours": 6.0,
  "actualHours": 2.5,
  "progressPercent": 40,
  "position": 200,
  "createdAt": "2026-09-01T10:00:00Z",
  "updatedAt": "2026-09-10T18:22:00Z",
  "version": 1
}
```

| Method & path | Body | Response |
| --- | --- | --- |
| `GET /project-categories` | — | `ProjectCategory[]` (position order, D13). |
| `POST /project-categories` | `{ name, color? }` | `201` + `ProjectCategory`; `409` if `name` already exists for this user. |
| `PATCH /project-categories/{id}` | `{ name?, color?, expectedVersion? }` | `200` + `ProjectCategory`; `409` on version mismatch or a name collision. |
| `DELETE /project-categories/{id}` | — | `204` (hard delete; referencing projects become uncategorized, D14). |
| `POST /project-categories:reorder` | `{ orderedIds: string[] }` | `200` + `ProjectCategory[]` (reordered). `400` if `orderedIds` ≠ the user's current categories. |
| `GET /projects?status=&categoryId=&size=&page=&pageSize=&sort=` | — | `{ content: Project[], page, totalElements }`; `status` omitted ⇒ excludes `ARCHIVED` (D10); `categoryId`/`size` narrow further. Page size is `pageSize`, not Spring's usual `size` — see D17. |
| `GET /projects/priority-ordered` | — | `Project[]` (`priorityRank` order, D19) — every non-`ARCHIVED` project, flat, ignoring category. Backs the "Sort by: Priority" list view. |
| `POST /projects` | `{ categoryId?, name, description?, size?, color?, startDate?, endDate? }` | `201` + `Project` (`status=PLANNING`, `size` defaults unset, `priorityRank` appended at the bottom, D20); `404` if `categoryId` doesn't resolve under the caller. |
| `GET /projects/{id}` | — | `Project`. |
| `PATCH /projects/{id}` | `{ categoryId, name, description, status, size, color, startDate, endDate, actualStart, actualEnd, expectedVersion? }` | Whole-form overwrite (D15) — **excludes `priorityRank`**, which only changes via `:reorder` (D18) — `200` + `Project`; `409` on version mismatch. |
| `DELETE /projects/{id}` | — | `204` (soft delete; cascades to tasks, D8). |
| `POST /projects:reorder` | `{ orderedIds: string[] }` | `200` + `Project[]` (rank order). `400` if `orderedIds` ≠ the user's current non-`ARCHIVED` projects (D20) — the web client only offers this when the status/size filters are at their defaults (D21). |
| `GET /projects/{id}/tasks?page=&size=&sort=` | — | `{ content: ProjectTask[], page, totalElements }`, `parentTaskId`-then-`position` ordered; client defaults `size` high (D1). |
| `POST /projects/{id}/tasks` | `{ name, description?, parentTaskId?, plannedStart?, plannedEnd?, estimateHours?, isMilestone? }` | `201` + `ProjectTask` (`status=TODO`); `400` on a depth-rule violation. |
| `PATCH /tasks/{taskId}` | `{ name, description, status, parentTaskId, plannedStart, plannedEnd, estimateHours, actualHours, progressPercent, isMilestone, expectedVersion? }` | Whole-form overwrite (D15) — `200` + `ProjectTask`; `409` on version mismatch, `400` on a depth-rule violation. |
| `DELETE /tasks/{taskId}` | — | `204` (soft delete; cascades to its own subtasks, one level). |
| `POST /projects/{id}/tasks:reorder` | `{ parentTaskId?, orderedIds: string[] }` | `200` + `ProjectTask[]` (that sibling group, reordered). `400` if `orderedIds` ≠ that group's current members. |

> **Deviations from `DESIGN.md` §5.1 to ratify:**
> 1. `GET /project-categories` + the category CRUD/`:reorder` endpoints are
>    **new**, not in the §5.1 sketch at all (D12) — needs a new row.
> 2. `GET /projects` gains explicit `status`/`categoryId`/`size` filter params
>    and the default-hides-`ARCHIVED` behaviour (D10) — §5.1 lists the
>    endpoint without spelling this out. `GET /projects/priority-ordered` and
>    `POST /projects:reorder` are **new** (D18–D21), needed once priority
>    became a drag-ordered rank rather than a static field.
> 3. `PATCH /projects/{id}` and `PATCH /tasks/{taskId}` are whole-form saves
>    (D15), not the sparse optional-field patches §5's general API design
>    section implies — `PATCH /tasks/{taskId}` additionally accepts
>    `parentTaskId` for reparenting (D5). Both worth a one-line note in §5.1.
> 4. §5.1 doesn't yet list the dependency/Gantt endpoints — those stay out of
>    scope here and arrive with M5, unchanged from what §5.1 already sketches
>    for them.
>
> Called out again in §9 as open items.

---

## 6. Web

### 6.1 New dependencies

None beyond what Todo already introduced — `@dnd-kit/core`/`@dnd-kit/sortable`
(D4/D2's drag) and the existing shadcn primitives are reused as-is.

### 6.2 shadcn/ui components to add

Into `web/src/components/ui/`: `tabs` (pulls `@radix-ui/react-tabs`, for the
List/Tree ⇄ Board switch on the project detail page), `select` (pulls
`@radix-ui/react-select`, for the parent-task/category dropdowns and status
filter), `progress` (pulls `@radix-ui/react-progress`, for the progress bar),
`badge` (status/priority-style chips — small, no Radix dependency). No colour
picker library — `color` fields use a plain `<input type="color">` paired
with the hex text value, same as any other controlled input.

### 6.3 Routing & app shell

```tsx
<Route path="projects" element={<ProjectListPage />} />
<Route path="projects/:id" element={<ProjectDetailPage />} />
```

`AppLayout.tsx` gains a `NavLink` to `/projects`, between `Journal` and
`Account`.

### 6.4 Feature folder — `web/src/features/projects/`

```
features/projects/
├── ProjectListPage.tsx       route component: "Sort by" select drives two render modes —
│                             Status/Recently updated/Name group into category sections
│                             (client-side comparator within each); Priority (D19) flattens
│                             into one dnd-kit-orderable list (ProjectPriorityList) fetched
│                             from GET /projects/priority-ordered, drag calls :reorder (D20),
│                             disabled unless status/size filters are at their defaults (D21)
├── ProjectCard.tsx           list row: color swatch, name, status badge, size badge (D16),
│                             date range, task count — no priority indicator here (D18:
│                             priority only has meaning as position, shown by list order,
│                             not as a per-card value)
├── ProjectPriorityList.tsx   flat dnd-kit SortableContext over every non-archived project;
│                             each row is a ProjectCard plus a drag handle; onDragEnd calls
│                             useReorderProjects (D20)
├── ProjectFormDialog.tsx     create/edit project (category select, name, description, status,
│                             size select, color, dates) — no priority field (D18)
├── CategoryManagerDialog.tsx add/rename/recolor/reorder/delete categories (dnd-kit list),
│                             opened from ProjectListPage ("Manage categories")
├── ProjectDetailPage.tsx     route component: header + Tabs (List/Tree ⇄ Board)
├── TaskTree.tsx              dnd-kit SortableContext per sibling level; expand/collapse subtasks;
│                             a TaskQuickAdd row at the bottom of the top-level list and of each
│                             expanded parent's subtask list (D23)
├── TaskRow.tsx               drag handle, status pill, inline title edit (sends the whole task
│                             back per D15 — only the title value changes), estimate, progress
│                             bar, milestone badge, ⋯ menu (edit incl. reparent / delete)
├── TaskQuickAdd.tsx          always-visible title-only input, Enter creates and refocuses —
│                             mirrors web/src/features/todo/QuickAdd.tsx exactly (D23)
├── TaskFormDialog.tsx        create/edit task (name, description, status, dates, estimate,
│                             progress, milestone toggle, parent-task select — D5)
├── TaskBoard.tsx             dnd-kit board: TODO/IN_PROGRESS/BLOCKED/DONE columns (D4/D11)
├── TaskCard.tsx               board card: name, "in <parent>" chip if a subtask, estimate, progress
├── useProjectCategories.ts   list query + create/patch/delete/reorder mutations
├── useProjects.ts            list/detail queries + create/patch/delete mutations
├── useProjectTasks.ts        task list query (paginated) + create/patch/delete/reorder mutations
└── projectKeys.ts            query-key factory (incl. category keys)
```

### 6.5 Data layer

- **`web/src/lib/api/projects.ts`** — `projectsApi` object mirroring
  `todoApi`/`journalApi`, covering categories, projects, and tasks.
- **Types** — add `ProjectCategory`, `CreateProjectCategoryBody`,
  `PatchProjectCategoryBody`, `Project` (incl. read-only `priorityRank:
  number`), `ProjectStatus`, `ProjectSize` (`"XS" | "S" | "M" | "L" | "XL"`),
  `ProjectTask`, `ProjectTaskStatus`, `CreateProjectBody`, `PatchProjectBody`
  (whole-form, D15 — non-optional fields, `categoryId: string | null`,
  `size: ProjectSize | null`; **no `priorityRank`**, D18),
  `CreateProjectTaskBody`, `PatchProjectTaskBody` (whole-form, D15,
  `parentTaskId: string | null`), `ReorderBody`, and
  `ProjectsPage`/`ProjectTasksPage = { content: T[]; page: number; totalElements: number }`
  to `web/src/lib/api/types.ts` (hand-written, as today).
- **Query keys** — `projectKeys.categories()`, `projectKeys.list(status, categoryId, size, page)`,
  `projectKeys.priorityOrdered()`, `projectKeys.detail(id)`, `projectKeys.tasks(projectId)`.
- **Hooks**:
  - `useProjectCategories()` → `projectKeys.categories()` — bare array (D13).
  - `useCreateCategory()` / `usePatchCategory()` / `useDeleteCategory()` /
    `useReorderCategories()` — optimistic; delete also invalidates
    `projectKeys.list(...)` since it changes projects' `categoryId`.
  - `useProjects(status, categoryId, size, page)` → `projectKeys.list(...)`
    (`page`/`pageSize` on the wire, D17). Used for every "Sort by" mode
    except Priority.
  - `useProjectsByPriority()` → `projectKeys.priorityOrdered()` — bare array
    (D19), used only in Priority sort mode.
  - `useReorderProjects()` → `POST /projects:reorder`; optimistic against
    `projectKeys.priorityOrdered()`, then invalidates `projectKeys.list(...)`
    too since ranking is reflected there on the next Priority-mode visit.
  - `useProject(id)` → `projectKeys.detail(id)`.
  - `useCreateProject()` / `usePatchProject()` / `useDeleteProject()` —
    optimistic, same pattern as Todo/Journal mutations; `usePatchProject`
    always sends the full current project with the changed field(s)
    overridden (D15), not a sparse diff — `priorityRank` is never part of
    that body (D18).
  - `useProjectTasks(projectId)` → fetches with a high `size` (D1), assembles
    a `{ topLevel: Task[], childrenByParentId: Map }` shape the tree/board
    both consume.
  - `useCreateTask()` / `usePatchTask()` / `useDeleteTask()` /
    `useReorderTasks()` — optimistic in place; `usePatchTask` is whole-form
    (D15) for the same reason as `usePatchProject`. `useCreateTask` is shared
    by both `TaskFormDialog` (full fields) and `TaskQuickAdd` (title only,
    D23) — one mutation, two callers.

### 6.6 List/tree view

- `TaskTree` renders top-level tasks as rows; each with children gets an
  expand/collapse chevron. Because the hierarchy is ≤2 levels, this is one
  `SortableContext` per level (the top-level list, and — independently — each
  expanded parent's children), not a general recursive tree.
- Drag reorders within a level only (D2); dropping a row onto another does
  **not** reparent (D5) — reparenting is the edit dialog's "Parent task"
  `Select` (top-level tasks in the project, or "None").
- `TaskRow` shows a status pill (click opens a small status menu — same four
  values as the board columns), inline title edit (click/Enter, Esc reverts,
  same UX precedent as Todo's `TodoRow`), a progress bar, and a milestone
  badge (◆) when `isMilestone`.
- **`TaskQuickAdd` (D23)** — a `TaskQuickAdd` row always renders at the
  bottom of the top-level list (`parentTaskId: null`) and, independently, at
  the bottom of every currently-expanded parent's subtask list
  (`parentTaskId` = that parent's id) — collapsing a parent hides its
  quick-add row along with its children. Enter creates a bare-title task
  (`status=TODO`, no dates/estimate/milestone) at the end of that sibling
  group and refocuses the input, so several titles can be typed in a row
  without a dialog — refining status/dates/estimate/parent happens later via
  `TaskRow`'s ⋯ menu → `TaskFormDialog`.

### 6.7 Board view

- `TaskBoard` renders four `@dnd-kit/sortable` columns (`TODO`,
  `IN_PROGRESS`, `BLOCKED`, `DONE`) over **every** task in the project flat —
  top-level and subtasks together (D11). A subtask's `TaskCard` shows a small
  "in <parent task name>" chip.
- Dropping a card in a different column `PATCH`es `{ status }`; dropping
  within a column reorders that task's sibling group (D2) — cross-column drag
  and within-column reorder share one `onDragEnd` handler, branching on
  whether the source and destination column differ.

### 6.8 Interactions

- **"Sort by" defaults to Status** (preserving the pre-existing
  status-then-`updatedAt` default ordering) — Priority is an opt-in mode a
  user switches to, not the landing view, since it's a bigger visual jump
  (categories disappear).
- **Project list, "Sort by" = Status / Recently updated / Name** — grouped
  into a section per category, in the category's `position` order, with an
  "Uncategorized" section last (only shown if non-empty); within a section,
  cards order by the chosen key via a plain client-side comparator over the
  already-fetched section, ties broken by `updatedAt` desc. A status
  `Select` filters across all sections, defaulting to "active" (hides
  `ARCHIVED`, D10); an optional size `Select` (`Any size` / `XS`…`XL`)
  narrows further. "+ New project" opens `ProjectFormDialog` with the
  category pre-selected if opened from within a section (e.g. a section's
  own "+ New" affordance). A "Manage categories" button opens
  `CategoryManagerDialog`.
- **Project list, "Sort by" = Priority** (D19) — category sections disappear;
  `ProjectPriorityList` renders every non-`ARCHIVED` project as one flat,
  drag-orderable column (`useProjectsByPriority`), each row a `ProjectCard`
  plus a drag handle, top of list = highest priority. When the status or
  size filter is narrowed away from its default, the list still sorts by
  rank but drag handles and `:reorder` are disabled (D21) — a small inline
  note explains why ("Clear filters to reorder"). This is the only sort mode
  where "+ New project" appends the new project at the very bottom of the
  visible list (D20's append-at-max-rank-plus-100 behaviour made visible).
- **Size badge** — `ProjectCard` shows the size (when set) as a small
  letter-badge (`XS`…`XL`) next to the status badge, distinct styling (e.g.
  outline vs. filled) so the two badges aren't confused at a glance. There is
  no separate priority indicator on the card (D18) — priority is expressed
  entirely by the card's position in the Priority-sorted list, not a value
  printed on it.
- **`CategoryManagerDialog`** — a dnd-kit-reorderable list of the user's
  categories (name, colour swatch, drag handle, rename-in-place, delete with
  a confirmation naming how many projects will become uncategorized), plus an
  always-visible "+ New category" row at the bottom.
- **Empty states** — "No projects yet." on the list (no category sections
  render until at least one category or project exists); "No tasks yet." on
  an empty tree/board, both with the relevant "+ New" action focused,
  mirroring Todo/Journal's empty-state pattern.
- **Milestone form** — `TaskFormDialog` collapses `plannedStart`/`plannedEnd`
  into one date field when the milestone toggle is on (D9); expands back to
  two fields when off.
- **Delete confirmation** — deleting a project with tasks shows a `Dialog`
  confirming the task count that will go with it ("This project has 8 tasks.
  Delete all of them too?") before calling `useDeleteProject` (D8's cascade
  made visible to the user, not just the data model).

---

## 7. Testing

### Backend

| Test | Type | Covers |
| --- | --- | --- |
| `ProjectCategoryRepositoryTest` | `@DataJpaTest` + Testcontainers PG | position ordering, `existsByUserIdAndName`, scoping to the querying user. |
| `ProjectRepositoryTest` | `@DataJpaTest` + Testcontainers PG | status/category/size filters (`search`, §4.3), soft-delete filtering, pagination; `findByUserIdAndDeletedAtIsNullAndStatusNotOrderByPriorityRankAsc` returns every non-`ARCHIVED` project rank-ordered, excludes other users'. |
| `ProjectTaskRepositoryTest` | `@DataJpaTest` + Testcontainers PG | sibling-group query (`project_id, parent_task_id, position`), `existsByParentTaskIdAndDeletedAtIsNull` (reparent guard), `findDueOrOverdue` (window-contains-day and overdue-not-done cases, scoped by the owning project's `user_id`). |
| `ProjectCategoryServiceTest` | plain JUnit 5 + AssertJ, Mockito repo | `create` position = max + 100; duplicate name → 409; `reorder` rewrites 100/200/… and rejects a mismatched id set; `delete` doesn't touch `project` rows itself (the FK's `ON DELETE SET NULL` is a repo/integration-level concern, D14). |
| `ProjectServiceTest` | plain JUnit 5 + AssertJ, Mockito repo | `create` defaults (`size` unset, `priorityRank` = max + 100 over the non-archived set, `100` when empty, D20); `create`/`patch` with a foreign `categoryId` → 404; an invalid `size` string → 400 (Jackson enum-binding failure, same as an invalid `status`); `patch`'s whole-form body never changes `priorityRank` even if a caller tries to smuggle it in (D18); `reorder` rewrites `priorityRank` to `100/200/…` and rejects an id set that isn't exactly the current non-`ARCHIVED` set (D20); `delete` cascades to tasks (D8); foreign-user row → 404; stale `expectedVersion` → 409; `status=` filter default excludes `ARCHIVED` (D10); `patch` with `categoryId`/`size: null` clears them (D15). |
| `ProjectTaskServiceTest` | plain JUnit 5 + AssertJ, Mockito repo | `create` position = max + 100 per sibling group; **depth rule** — assigning a subtask as a parent → 400; assigning a parent to a task that has children → 400; valid reparent succeeds; `patch` with `parentTaskId: null` moves a task back to top-level (D15); `reorder` rewrites 100/200/… within a sibling group and rejects a mismatched id set; `progressPercent` outside 0–100 rejected; `markMilestone` forces equal dates; `delete` cascades to a task's own subtasks. |
| `ProjectCategoryControllerTest` / `ProjectControllerTest` / `ProjectTaskControllerTest` | `@WebMvcTest` + `@Import({SecurityConfig, WebMvcConfig, CurrentUserArgumentResolver})`, `@MockitoBean` services/`JwtDecoder` | every endpoint's happy path; blank `name` → 400; `progressPercent`/invalid `size` out of range → 400; `GET /projects?size=&pageSize=&sort=name,asc` binds correctly (D17); `GET /projects/priority-ordered` returns the flat rank order; `POST /projects:reorder` mismatch → 400; a `priorityRank` field in a `PATCH /projects/{id}` body is silently ignored, not applied (D18); no token → 401 problem+json; 409 body shape. |
| `ProjectsFlowIntegrationTest` | `@SpringBootTest` + Testcontainers | register → login → `POST /project-categories` → `POST /projects` ×2 (with that category) → `POST /projects:reorder` → assert `priorityRank` order → `POST .../tasks` ×3 (one nested) → `:reorder` → reparent via `PATCH` → `DELETE` a project → assert its tasks are soft-deleted too → `DELETE` the category → assert the remaining project is uncategorized. |
| `ArchitectureTest` (extend) | ArchUnit | add `projectsInternalsArePrivate`: no class outside `com.kairon.projects..` depends on `com.kairon.projects.domain..` / `com.kairon.projects.repo..`. |

### Web

| Test | Covers |
| --- | --- |
| MSW handlers (`web/src/test/msw/handlers.ts`) | add `GET/POST/PATCH/DELETE /api/v1/project-categories*`, `:reorder`, `GET/POST/PATCH/DELETE /api/v1/projects*`, `GET /projects/priority-ordered`, `POST /projects:reorder`, `GET/POST /projects/{id}/tasks`, `PATCH/DELETE /tasks/{taskId}`, `:reorder`, with an in-memory category/project/task store. |
| `ProjectListPage.test.tsx` | renders projects grouped into category sections in position order for Status/Recently updated/Name sort modes (client-side re-sort, no refetch); switching "Sort by" to Priority flattens into `ProjectPriorityList` and fetches `GET /projects/priority-ordered`; status/size filters refetch and, when narrowed, disable drag in Priority mode (D21); size badge renders only when set; "+ New project" creates and navigates to detail. |
| `ProjectPriorityList.test.tsx` | drag reorder calls `useReorderProjects` with the full current id list; drag handles are absent/disabled when a non-default filter is active (D21); a newly created project appears at the bottom. |
| `CategoryManagerDialog.test.tsx` | create/rename/recolor a category; drag reorder calls `:reorder`; delete shows the affected-project count and, on confirm, the list's "Uncategorized" section gains those projects. |
| `ProjectDetailPage.test.tsx` | Tabs switch between tree and board without losing task state; both render the same underlying task set. |
| `TaskTree.test.tsx` | expand/collapse; drag reorder within a level calls `:reorder` with the right sibling group; reparent via the form dialog updates the tree shape; a quick-add row exists at the bottom of the top-level list and appears/disappears under a parent as it expands/collapses (D23). |
| `TaskQuickAdd.test.tsx` | Enter with a non-empty title calls `useCreateTask` with `{name, parentTaskId}` and clears+refocuses the input; empty/whitespace-only submits are ignored; consecutive Enters add several tasks in order at the bottom of the sibling group. |
| `TaskBoard.test.tsx` | drag a card to a new column `PATCH`es status; drag within a column reorders; subtasks show the parent chip. |
| `projects.spec.ts` (Playwright) | log in as the seeded dev user, create a category, create two projects in it, add a task and a subtask, drag the task to `IN_PROGRESS` on the board, reorder on the tree, switch to Priority sort and drag-reorder the two projects, delete a project. |

---

## 8. Task breakdown (suggested order)

1. `V004__projects.sql` (incl. the `todo_item` FK) + `ProjectCategory`/
   `Project`/`ProjectTask` entities + repositories + repo tests.
2. `ProjectCategoryService` + `ProjectCategoryController` +
   `ProjectCategoryDtos`; service + controller tests (CRUD, `:reorder`,
   duplicate-name conflict).
3. `ProjectService` (incl. `listByPriority`/`reorder`, D18–D20) +
   `ProjectController` + `ProjectDtos`; service + controller tests (CRUD,
   status/category/size filters, priority-ordered list, reorder).
4. `ProjectTaskService` (incl. the depth rule) + `ProjectTaskController` +
   `ProjectTaskDtos`; service + controller tests (CRUD, `:reorder`, depth
   rule, milestone dates).
5. `ProjectsApi`/`ProjectTaskView`/`ProjectView` port + `dueOrOverdue` query.
6. `ArchitectureTest` rule + `ProjectsFlowIntegrationTest`.
7. Web: shadcn components (`tabs`, `select`, `progress`, `badge`);
   `types.ts` additions, `lib/api/projects.ts`, `projectKeys`, query/mutation
   hooks (incl. categories), MSW handlers.
8. Web: `CategoryManagerDialog` + `useProjectCategories`.
9. Web: `ProjectListPage` (grouped by category, Status/Recently
   updated/Name sort modes) + `ProjectCard` + `ProjectFormDialog` (incl.
   category select, no priority field per D18); routing + nav link.
10. Web: `ProjectPriorityList` + `useProjectsByPriority`/`useReorderProjects`
    + the "Sort by" select's Priority mode (D19), incl. the filter-disables-
    drag behaviour (D21).
11. Web: `ProjectDetailPage` + `TaskTree` + `TaskRow` (no drag yet) +
    `TaskFormDialog` (incl. parent-task select, milestone toggle) +
    `TaskQuickAdd` (D23).
12. Web: dnd-kit reorder on the tree and the category list; `TaskBoard` +
    `TaskCard` + cross-column drag.
13. Web: delete-confirmation dialogs (project cascade, D8; category
    uncategorize, D14).
14. Web tests + Playwright happy path.
15. Docs: tick the M4 boxes in `ROADMAP.md`; add the category endpoints,
    `status`/`categoryId`/`size` filters, `priority-ordered`/`:reorder`
    endpoints, and `parentTaskId` reparenting notes to `DESIGN.md` §5.1;
    update `CLAUDE.md`'s "Current state" module list once implemented; mark
    this plan `Accepted`.

---

## 9. Open questions / risks

| # | Question | Recommendation |
| --- | --- | --- |
| Q1 | `GET /projects`/`GET /projects/{id}/tasks` paginated envelope (D1) vs. Todo/Journal's bare-array precedent for bounded lists. | Resolved as **paginated envelope** — user call, matches `DESIGN.md` §5's default; revisit only if the "one big page" client pattern (§6.5) proves awkward in practice. |
| Q2 | Should `ProjectTaskView` (the `projects.api` port's DTO) expose the project's `name`/`color` denormalized, so M6's Today screen doesn't need a second lookup per task? | Include them — cheap to add to the view now, saves M6 an N+1 or a join it would otherwise have to write itself. |
| Q3 | Milestone task cards on the board (D11) — render as a normal card, or visually distinct (diamond accent) even before the Gantt (M5) exists? | Small visual accent now (a ◆ prefix / border tint) — cheap, and gives the milestone flag *some* visible meaning before M5's Gantt fully uses it. |
| Q4 | Does `ProjectTaskService.delete`'s one-level cascade (§4.4) need to worry about depth beyond 2? | No — the depth rule (§4.4) already guarantees no task has grandchildren, so "delete this task's direct children" is always the complete cascade. |
| Q5 | Category is currently 1:1 per project (D12) — should a project be able to belong to more than one category (many-to-many)? | Keep 1:1 for M4 — the example list (this session) categorizes each project into exactly one bucket; a project genuinely spanning categories is closer to what tags (already backlog, `DATA_MODEL.md` "Not in the first cut") would solve than what an epic-style grouping is for. Revisit only if single-category proves limiting in practice. |
| Q6 | Should `ProjectFormDialog` allow creating a brand-new category inline (a "+ Create '<typed name>'" option in the category `Select`), or only picking from existing ones managed via `CategoryManagerDialog`? | Add the inline quick-create — cheap (reuses `useCreateCategory`), and avoids a context-switch out of "I'm creating a project" just to first go create its category. |
| Q7 | Should a `DONE`/`ARCHIVED`-status project display any different in the Priority list — e.g. dimmed — given it still occupies a rank slot? | Not for M4 — `DONE` projects already show normally everywhere else in the list; `ARCHIVED` ones are excluded from ranking entirely (D20), so there's nothing extra to signal. Revisit only if a long-lived `DONE` project cluttering the ranked list turns out to bother in practice. |
| Q8 | D21 disables dragging under a narrowed filter rather than solving the interleave problem outright — is a disabled state with an inline hint enough, or does it need a stronger nudge (e.g. an auto "Clear filters" button right on the list)? | Inline hint is enough for M4 — it's one click on an already-visible filter control; a dedicated button is the kind of polish to add only if usage shows people missing the hint. |

---

## 10. Doc updates this milestone produces

- `ROADMAP.md` — tick the three M4 checkboxes (already updated, this session,
  to fold category CRUD/grouping into them).
- `DESIGN.md` §5.1 — already updated (this session) with the
  `project-categories` row, `GET /projects`'s `status`/`categoryId`/`size`
  filters, `GET /projects/priority-ordered`, and `POST /projects:reorder`;
  confirm it still matches once implemented, and add the `parentTaskId`
  reparenting note for `PATCH /tasks/{taskId}` (not yet done).
- `DESIGN.md` §2.3 — already updated with the category, t-shirt-size, and
  priority-rank description (this session); confirm it still matches once
  implemented.
- `DATA_MODEL.md` — already describes `project_category`/`project` (incl.
  `size`/`priority_rank`)/`project_task` (this session); confirm the DDL
  matches (status/size/progress/estimate/color check constraints,
  `project_category_user_name_uk`).
- `CLAUDE.md` "Current state" — add the `projects` module bullet once
  implemented, matching the `todo`/`journal` entries' level of detail.
- This file — flip `Status: Draft` → `Accepted` once Q1–Q8 are resolved and
  the milestone is implemented.
