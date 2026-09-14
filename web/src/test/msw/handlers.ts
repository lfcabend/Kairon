import { HttpResponse, http } from "msw";

import type {
  AuthResponse,
  JournalEntry,
  Me,
  Project,
  ProjectCategory,
  ProjectTask,
  TaskDependency,
  TodoItem,
} from "@/lib/api/types";

export const testUser = {
  id: "018f5b3e-0000-7000-8000-0000000000aa",
  email: "ada@example.com",
  displayName: "Ada",
  timezone: "UTC",
};

export const authResponse = (accessToken: string): AuthResponse => ({
  accessToken,
  tokenType: "Bearer",
  expiresInSeconds: 900,
  user: testUser,
});

export const meResponse: Me = {
  ...testUser,
  status: "ACTIVE",
  preferences: {},
};

export function resetMeResponse() {
  Object.assign(meResponse, { ...testUser, status: "ACTIVE", preferences: {} });
}

const problem = (status: number, detail: string) =>
  HttpResponse.json({ status, detail }, { status });

// --- in-memory todo store ------------------------------------------------

type Row = TodoItem & { removed?: boolean };

let todos: Row[] = [];
let seq = 0;

export function resetTodoStore() {
  todos = [];
  seq = 0;
}

export function seedTodos(items: Partial<TodoItem>[]) {
  for (const item of items) todos.push(makeTodo(item));
}

export function allTodos(): Row[] {
  return todos;
}

function makeTodo(partial: Partial<TodoItem>): Row {
  seq += 1;
  const now = new Date(2026, 0, 1, 0, 0, seq).toISOString();
  return {
    id: partial.id ?? `todo-${seq}`,
    day: partial.day ?? "2026-09-09",
    title: partial.title ?? `Task ${seq}`,
    notes: partial.notes ?? null,
    status: partial.status ?? "OPEN",
    priority: partial.priority ?? 0,
    position: partial.position ?? seq * 100,
    estimateMinutes: partial.estimateMinutes ?? null,
    sourceProjectTaskId: partial.sourceProjectTaskId ?? null,
    rolledOverFromId: partial.rolledOverFromId ?? null,
    completedAt: partial.completedAt ?? null,
    createdAt: partial.createdAt ?? now,
    updatedAt: now,
    version: partial.version ?? 0,
  };
}

const authed = (request: Request) =>
  request.headers.get("Authorization")?.startsWith("Bearer ");

const live = () => todos.filter((t) => !t.removed);

function sortRows(rows: Row[]): Row[] {
  return [...rows].sort((a, b) => a.day.localeCompare(b.day) || a.position - b.position);
}

// --- in-memory journal store ---------------------------------------------

type JournalRow = JournalEntry & { removed?: boolean };

let journalEntries: JournalRow[] = [];
let journalSeq = 0;

export function resetJournalStore() {
  journalEntries = [];
  journalSeq = 0;
}

export function seedJournalEntries(entries: Partial<JournalEntry>[]) {
  for (const entry of entries) journalEntries.push(makeJournalEntry(entry));
}

function makeJournalEntry(partial: Partial<JournalEntry>): JournalRow {
  journalSeq += 1;
  const now = new Date(2026, 0, 1, 0, 0, journalSeq).toISOString();
  return {
    id: partial.id ?? `journal-${journalSeq}`,
    day: partial.day ?? "2026-09-09",
    position: partial.position ?? journalSeq * 100,
    title: partial.title ?? null,
    content: partial.content ?? "",
    mood: partial.mood ?? null,
    createdAt: partial.createdAt ?? now,
    updatedAt: now,
    version: partial.version ?? 0,
  };
}

const liveJournal = () => journalEntries.filter((e) => !e.removed);

function sortJournalRows(rows: JournalRow[]): JournalRow[] {
  return [...rows].sort((a, b) => a.day.localeCompare(b.day) || a.position - b.position);
}

// --- in-memory projects store ---------------------------------------------

type CategoryRow = ProjectCategory;
type ProjectRow = Project & { removed?: boolean };
type TaskRow = ProjectTask & { removed?: boolean };

let categories: CategoryRow[] = [];
let categorySeq = 0;
let projects: ProjectRow[] = [];
let projectSeq = 0;
let tasks: TaskRow[] = [];
let taskSeq = 0;
let dependencies: TaskDependency[] = [];
let dependencySeq = 0;

export function resetProjectsStore() {
  categories = [];
  categorySeq = 0;
  projects = [];
  projectSeq = 0;
  tasks = [];
  taskSeq = 0;
  dependencies = [];
  dependencySeq = 0;
}

export function seedTaskDependencies(rows: Partial<TaskDependency>[]): TaskDependency[] {
  const created = rows.map(makeDependency);
  dependencies.push(...created);
  return created;
}

function makeDependency(partial: Partial<TaskDependency>): TaskDependency {
  dependencySeq += 1;
  return {
    id: partial.id ?? `dependency-${dependencySeq}`,
    predecessorId: partial.predecessorId ?? "",
    successorId: partial.successorId ?? "",
    type: partial.type ?? "FS",
    lagDays: partial.lagDays ?? 0,
    violatesConstraint: false,
    createdAt: partial.createdAt ?? new Date(2026, 0, 1, 0, 0, dependencySeq).toISOString(),
  };
}

/** Mirrors the backend's D13 formula: an `FS` edge violates when the successor starts too early. */
function withViolatesConstraint(edge: TaskDependency): TaskDependency {
  if (edge.type !== "FS") return { ...edge, violatesConstraint: false };
  const predecessor = tasks.find((t) => t.id === edge.predecessorId);
  const successor = tasks.find((t) => t.id === edge.successorId);
  if (!predecessor?.plannedEnd || !successor?.plannedStart) return { ...edge, violatesConstraint: false };
  const predecessorEnd = new Date(predecessor.plannedEnd);
  predecessorEnd.setDate(predecessorEnd.getDate() + edge.lagDays);
  return { ...edge, violatesConstraint: new Date(successor.plannedStart) < predecessorEnd };
}

export function seedCategories(rows: Partial<ProjectCategory>[]): CategoryRow[] {
  const created = rows.map(makeCategory);
  categories.push(...created);
  return created;
}

export function seedProjects(rows: Partial<Project>[]): ProjectRow[] {
  const created = rows.map(makeProject);
  projects.push(...created);
  return created;
}

export function seedProjectTasks(rows: Partial<ProjectTask>[]): TaskRow[] {
  const created = rows.map(makeTask);
  tasks.push(...created);
  return created;
}

function makeCategory(partial: Partial<ProjectCategory>): CategoryRow {
  categorySeq += 1;
  const now = new Date(2026, 0, 1, 0, 0, categorySeq).toISOString();
  return {
    id: partial.id ?? `category-${categorySeq}`,
    name: partial.name ?? `Category ${categorySeq}`,
    color: partial.color ?? "#6366f1",
    position: partial.position ?? categorySeq * 100,
    createdAt: partial.createdAt ?? now,
    updatedAt: now,
    version: partial.version ?? 0,
  };
}

function makeProject(partial: Partial<Project>): ProjectRow {
  projectSeq += 1;
  const now = new Date(2026, 0, 1, 0, 0, projectSeq).toISOString();
  return {
    id: partial.id ?? `project-${projectSeq}`,
    categoryId: partial.categoryId ?? null,
    name: partial.name ?? `Project ${projectSeq}`,
    description: partial.description ?? null,
    status: partial.status ?? "PLANNING",
    size: partial.size ?? null,
    priorityRank: partial.priorityRank ?? projectSeq * 100,
    color: partial.color ?? "#6366f1",
    startDate: partial.startDate ?? null,
    endDate: partial.endDate ?? null,
    actualStart: partial.actualStart ?? null,
    actualEnd: partial.actualEnd ?? null,
    createdAt: partial.createdAt ?? now,
    updatedAt: now,
    version: partial.version ?? 0,
  };
}

function makeTask(partial: Partial<ProjectTask>): TaskRow {
  taskSeq += 1;
  const now = new Date(2026, 0, 1, 0, 0, taskSeq).toISOString();
  return {
    id: partial.id ?? `task-${taskSeq}`,
    projectId: partial.projectId ?? "",
    parentTaskId: partial.parentTaskId ?? null,
    name: partial.name ?? `Task ${taskSeq}`,
    description: partial.description ?? null,
    status: partial.status ?? "TODO",
    isMilestone: partial.isMilestone ?? false,
    plannedStart: partial.plannedStart ?? null,
    plannedEnd: partial.plannedEnd ?? null,
    estimateHours: partial.estimateHours ?? null,
    actualHours: partial.actualHours ?? null,
    progressPercent: partial.progressPercent ?? 0,
    position: partial.position ?? taskSeq * 100,
    createdAt: partial.createdAt ?? now,
    updatedAt: now,
    version: partial.version ?? 0,
  };
}

const liveProjects = () => projects.filter((p) => !p.removed);
const liveTasks = () => tasks.filter((t) => !t.removed);
const byRank = (rows: ProjectRow[]) => [...rows].sort((a, b) => a.priorityRank - b.priorityRank);
const byTaskPosition = (rows: TaskRow[]) => [...rows].sort((a, b) => a.position - b.position);

const projectHandlers = [
  http.get("/kairon/api/v1/project-categories", ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    return HttpResponse.json([...categories].sort((a, b) => a.position - b.position));
  }),

  http.post("/kairon/api/v1/project-categories", async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as Partial<ProjectCategory>;
    if (categories.some((c) => c.name === body.name)) {
      return problem(409, `A category named "${body.name}" already exists.`);
    }
    const maxPos = categories.reduce((m, c) => Math.max(m, c.position), 0);
    const created = makeCategory({ ...body, position: maxPos + 100 });
    categories.push(created);
    return HttpResponse.json(created, { status: 201 });
  }),

  http.patch("/kairon/api/v1/project-categories/:id", async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = categories.find((c) => c.id === params.id);
    if (!row) return problem(404, "Project category not found.");
    const body = (await request.json()) as Partial<ProjectCategory>;
    Object.assign(row, body, { updatedAt: new Date().toISOString() });
    return HttpResponse.json(row);
  }),

  http.delete("/kairon/api/v1/project-categories/:id", ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    categories = categories.filter((c) => c.id !== params.id);
    for (const p of projects) if (p.categoryId === params.id) p.categoryId = null;
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(/\/api\/v1\/project-categories:reorder$/, async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as { orderedIds: string[] };
    const ids = new Set(categories.map((c) => c.id));
    if (body.orderedIds.length !== ids.size || !body.orderedIds.every((id) => ids.has(id))) {
      return problem(400, "`orderedIds` must list exactly the user's current categories.");
    }
    body.orderedIds.forEach((id, i) => {
      categories.find((c) => c.id === id)!.position = (i + 1) * 100;
    });
    return HttpResponse.json([...categories].sort((a, b) => a.position - b.position));
  }),

  http.get(/\/api\/v1\/projects\/priority-ordered$/, ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    return HttpResponse.json(byRank(liveProjects().filter((p) => p.status !== "ARCHIVED")));
  }),

  http.get("/kairon/api/v1/projects", ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const url = new URL(request.url);
    const status = url.searchParams.get("status");
    const categoryId = url.searchParams.get("categoryId");
    const size = url.searchParams.get("size");
    const includeArchived = url.searchParams.get("includeArchived") === "true";
    const page = Number(url.searchParams.get("page") ?? "0");
    const pageSize = Number(url.searchParams.get("pageSize") ?? "50");
    let rows = liveProjects();
    if (status) rows = rows.filter((p) => p.status === status);
    else if (!includeArchived) rows = rows.filter((p) => p.status !== "ARCHIVED");
    if (categoryId) rows = rows.filter((p) => p.categoryId === categoryId);
    if (size) rows = rows.filter((p) => p.size === size);
    rows = byRank(rows);
    const start = page * pageSize;
    return HttpResponse.json({
      content: rows.slice(start, start + pageSize),
      page,
      totalElements: rows.length,
    });
  }),

  http.post(/\/api\/v1\/projects:reorder$/, async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as { orderedIds: string[] };
    const rankable = liveProjects().filter((p) => p.status !== "ARCHIVED");
    const ids = new Set(rankable.map((p) => p.id));
    if (body.orderedIds.length !== ids.size || !body.orderedIds.every((id) => ids.has(id))) {
      return problem(400, "`orderedIds` must list exactly the user's current non-archived projects.");
    }
    body.orderedIds.forEach((id, i) => {
      projects.find((p) => p.id === id)!.priorityRank = (i + 1) * 100;
    });
    return HttpResponse.json(byRank(rankable));
  }),

  http.post("/kairon/api/v1/projects", async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as Partial<Project> & { name: string };
    if (!body.name?.trim()) return problem(400, "Name must not be blank.");
    const maxRank = liveProjects()
      .filter((p) => p.status !== "ARCHIVED")
      .reduce((m, p) => Math.max(m, p.priorityRank), 0);
    const created = makeProject({ ...body, priorityRank: maxRank + 100 });
    projects.push(created);
    return HttpResponse.json(created, { status: 201 });
  }),

  http.get("/kairon/api/v1/projects/:id", ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = liveProjects().find((p) => p.id === params.id);
    if (!row) return problem(404, "Project not found.");
    return HttpResponse.json(row);
  }),

  http.patch("/kairon/api/v1/projects/:id", async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = projects.find((p) => p.id === params.id);
    if (!row) return problem(404, "Project not found.");
    const body = (await request.json()) as Partial<Project>;
    const { priorityRank: _ignored, ...rest } = body;
    Object.assign(row, rest, { updatedAt: new Date().toISOString() });
    return HttpResponse.json(row);
  }),

  http.delete("/kairon/api/v1/projects/:id", ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = projects.find((p) => p.id === params.id);
    if (row) row.removed = true;
    for (const t of tasks) if (t.projectId === params.id) t.removed = true;
    return new HttpResponse(null, { status: 204 });
  }),

  http.get(/\/api\/v1\/projects\/([^/]+)\/tasks$/, ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const projectId = params[0] as string;
    const rows = byTaskPosition(liveTasks().filter((t) => t.projectId === projectId));
    return HttpResponse.json({ content: rows, page: 0, totalElements: rows.length });
  }),

  http.post(/\/api\/v1\/projects\/([^/]+)\/tasks$/, async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const projectId = params[0] as string;
    const body = (await request.json()) as Partial<ProjectTask> & { name: string };
    if (!body.name?.trim()) return problem(400, "Name must not be blank.");
    const siblings = liveTasks().filter(
      (t) => t.projectId === projectId && t.parentTaskId === (body.parentTaskId ?? null),
    );
    const maxPos = siblings.reduce((m, t) => Math.max(m, t.position), 0);
    const created = makeTask({ ...body, projectId, position: maxPos + 100 });
    tasks.push(created);
    return HttpResponse.json(created, { status: 201 });
  }),

  http.patch("/kairon/api/v1/tasks/:id", async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = tasks.find((t) => t.id === params.id);
    if (!row) return problem(404, "Task not found.");
    const body = (await request.json()) as Partial<ProjectTask>;
    Object.assign(row, body, { updatedAt: new Date().toISOString() });
    return HttpResponse.json(row);
  }),

  http.delete("/kairon/api/v1/tasks/:id", ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = tasks.find((t) => t.id === params.id);
    if (row) row.removed = true;
    const cascadedIds = [params.id as string];
    for (const t of tasks) {
      if (t.parentTaskId === params.id) {
        t.removed = true;
        cascadedIds.push(t.id);
      }
    }
    dependencies = dependencies.filter(
      (d) => !cascadedIds.includes(d.predecessorId) && !cascadedIds.includes(d.successorId),
    );
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(/\/api\/v1\/projects\/([^/]+)\/tasks:reorder$/, async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const projectId = params[0] as string;
    const body = (await request.json()) as { parentTaskId: string | null; orderedIds: string[] };
    const siblings = liveTasks().filter(
      (t) => t.projectId === projectId && t.parentTaskId === body.parentTaskId,
    );
    const ids = new Set(siblings.map((t) => t.id));
    if (body.orderedIds.length !== ids.size || !body.orderedIds.every((id) => ids.has(id))) {
      return problem(400, "`orderedIds` must list exactly that sibling group's current members.");
    }
    body.orderedIds.forEach((id, i) => {
      tasks.find((t) => t.id === id)!.position = (i + 1) * 100;
    });
    return HttpResponse.json(byTaskPosition(siblings));
  }),

  http.get(/\/api\/v1\/projects\/([^/]+)\/dependencies$/, ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const projectId = params[0] as string;
    const taskIds = new Set(liveTasks().filter((t) => t.projectId === projectId).map((t) => t.id));
    const edges = dependencies.filter((d) => taskIds.has(d.predecessorId)).map(withViolatesConstraint);
    return HttpResponse.json(edges);
  }),

  http.post(/\/api\/v1\/tasks\/([^/]+)\/dependencies$/, async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const successorId = params[0] as string;
    const successor = tasks.find((t) => t.id === successorId);
    if (!successor) return problem(404, "Task not found.");
    const body = (await request.json()) as { predecessorId: string; type?: string; lagDays?: number };
    if (body.predecessorId === successorId) return problem(400, "A task can't depend on itself.");
    const predecessor = tasks.find((t) => t.id === body.predecessorId && t.projectId === successor.projectId);
    if (!predecessor) return problem(400, "Predecessor task not found in this project.");
    if (dependencies.some((d) => d.predecessorId === body.predecessorId && d.successorId === successorId)) {
      return problem(409, "This dependency already exists.");
    }
    // DFS from the new edge's successor — if the predecessor is already reachable, it's a cycle (D3).
    const adjacency = new Map<string, string[]>();
    for (const d of dependencies) adjacency.set(d.predecessorId, [...(adjacency.get(d.predecessorId) ?? []), d.successorId]);
    const stack = [successorId];
    const visited = new Set<string>();
    while (stack.length > 0) {
      const current = stack.pop()!;
      if (current === body.predecessorId) return problem(400, "This would create a circular dependency.");
      if (visited.has(current)) continue;
      visited.add(current);
      stack.push(...(adjacency.get(current) ?? []));
    }
    const created = makeDependency({
      predecessorId: body.predecessorId,
      successorId,
      type: (body.type as TaskDependency["type"]) ?? "FS",
      lagDays: body.lagDays ?? 0,
    });
    dependencies.push(created);
    return HttpResponse.json(withViolatesConstraint(created), { status: 201 });
  }),

  http.delete("/kairon/api/v1/dependencies/:id", ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    if (!dependencies.some((d) => d.id === params.id)) return problem(404, "Dependency not found.");
    dependencies = dependencies.filter((d) => d.id !== params.id);
    return new HttpResponse(null, { status: 204 });
  }),
];

/**
 * Baseline happy-path handlers. Individual tests narrow behaviour with
 * `server.use(...)` and seed rows with `seedTodos(...)`/`seedJournalEntries(...)`.
 */
export const handlers = [
  http.get("/kairon/api/v1/ping", () => HttpResponse.json({ pong: true, version: "test" })),

  http.get("/kairon/actuator/info", () =>
    HttpResponse.json({
      build: { version: "test", time: "2026-01-01T00:00:00Z", commit: "abc1234" },
      deploy: { image: "kairon:abc1234", deployedAt: "2026-01-01T00:05:00Z" },
    }),
  ),

  http.post("/kairon/api/v1/auth/register", () =>
    HttpResponse.json(authResponse("access-registered"), { status: 201 }),
  ),

  http.post("/kairon/api/v1/auth/login", async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    if (body.password === "wrong-password") {
      return problem(401, "Invalid email or password.");
    }
    return HttpResponse.json(authResponse("access-login"));
  }),

  http.post("/kairon/api/v1/auth/refresh", () => problem(401, "Missing refresh token.")),

  http.post("/kairon/api/v1/auth/logout", () => new HttpResponse(null, { status: 204 })),
  http.post("/kairon/api/v1/auth/logout-all", () => new HttpResponse(null, { status: 204 })),

  http.get("/kairon/api/v1/me", ({ request }) => {
    if (authed(request)) return HttpResponse.json(meResponse);
    return problem(401, "Authentication required.");
  }),

  http.patch("/kairon/api/v1/me", async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const patch = (await request.json()) as Partial<Me>;
    Object.assign(meResponse, patch);
    return HttpResponse.json(meResponse);
  }),

  // --- todo -----------------------------------------------------------

  http.get("/kairon/api/v1/todo", ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const url = new URL(request.url);
    const day = url.searchParams.get("day");
    const from = url.searchParams.get("from");
    const to = url.searchParams.get("to");
    let rows = live();
    if (day) rows = rows.filter((t) => t.day === day);
    else if (from && to) rows = rows.filter((t) => t.day >= from && t.day <= to);
    else return problem(400, "Provide either `day` or both `from` and `to`.");
    return HttpResponse.json(sortRows(rows));
  }),

  http.post("/kairon/api/v1/todo", async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as Partial<TodoItem> & { title: string };
    if (!body.title?.trim()) return problem(400, "Title must not be blank.");
    const maxPos = live()
      .filter((t) => t.day === body.day)
      .reduce((m, t) => Math.max(m, t.position), 0);
    const created = makeTodo({ ...body, position: maxPos + 100 });
    todos.push(created);
    return HttpResponse.json(created, { status: 201 });
  }),

  http.patch("/kairon/api/v1/todo/:id", async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = todos.find((t) => t.id === params.id);
    if (!row) return problem(404, "Todo item not found.");
    const body = (await request.json()) as Partial<TodoItem>;
    Object.assign(row, body, { updatedAt: new Date().toISOString() });
    return HttpResponse.json(row);
  }),

  http.delete("/kairon/api/v1/todo/:id", ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = todos.find((t) => t.id === params.id);
    if (row) row.removed = true;
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(/\/api\/v1\/todo\/([^/]+):complete$/, async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const id = decodeURIComponent(new URL(request.url).pathname.split("/").pop()!.replace(":complete", ""));
    const row = todos.find((t) => t.id === id);
    if (!row) return problem(404, "Todo item not found.");
    const body = (await request.json().catch(() => ({}))) as { complete?: boolean };
    const complete = body.complete ?? true;
    row.status = complete ? "DONE" : "OPEN";
    row.completedAt = complete ? new Date().toISOString() : null;
    return HttpResponse.json(row);
  }),

  http.post(/\/api\/v1\/todo:reorder$/, async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as { day: string; orderedIds: string[] };
    const dayRows = live().filter((t) => t.day === body.day);
    const ids = new Set(dayRows.map((t) => t.id));
    if (body.orderedIds.length !== ids.size || !body.orderedIds.every((id) => ids.has(id))) {
      return problem(400, "`orderedIds` must list exactly the non-deleted items for that day.");
    }
    body.orderedIds.forEach((id, i) => {
      const row = todos.find((t) => t.id === id)!;
      row.position = (i + 1) * 100;
    });
    return HttpResponse.json(sortRows(dayRows));
  }),

  http.get("/kairon/api/v1/todo/rollover-preview", ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const onDay = new URL(request.url).searchParams.get("onDay")!;
    const eligible = live().filter((t) => t.status === "OPEN" && t.day < onDay);
    const byDay = new Map<string, Row[]>();
    for (const t of eligible) {
      if (!byDay.has(t.day)) byDay.set(t.day, []);
      byDay.get(t.day)!.push(t);
    }
    const sourceDays = [...byDay.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([day, items]) => ({ day, items }));
    return HttpResponse.json({ sourceDays, totalItems: eligible.length });
  }),

  http.post(/\/api\/v1\/todo:rollover$/, async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as { toDay: string; fromDay?: string; ids?: string[] };
    let sources = live().filter((t) => t.status === "OPEN" && t.day < body.toDay);
    if (body.ids) sources = sources.filter((t) => body.ids!.includes(t.id));
    else if (body.fromDay) sources = sources.filter((t) => t.day === body.fromDay);
    const rolledOver = sources.map((source) => {
      source.status = "CANCELLED";
      return makeTodo({
        day: body.toDay,
        title: source.title,
        notes: source.notes,
        priority: source.priority,
        estimateMinutes: source.estimateMinutes,
        rolledOverFromId: source.id,
      });
    });
    todos.push(...rolledOver);
    return HttpResponse.json({ rolledOver });
  }),

  http.post(/\/api\/v1\/todo:rollover-undo$/, async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as { createdIds: string[] };
    const reopened: Row[] = [];
    for (const id of body.createdIds) {
      const created = todos.find((t) => t.id === id);
      if (!created?.rolledOverFromId) continue;
      created.removed = true;
      const source = todos.find((t) => t.id === created.rolledOverFromId);
      if (source) {
        source.status = "OPEN";
        reopened.push(source);
      }
    }
    return HttpResponse.json({ reopened });
  }),

  // --- journal ----------------------------------------------------------

  http.get("/kairon/api/v1/journal/entry-days", ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const url = new URL(request.url);
    const from = url.searchParams.get("from")!;
    const to = url.searchParams.get("to")!;
    const days = [...new Set(liveJournal().filter((e) => e.day >= from && e.day <= to).map((e) => e.day))].sort();
    return HttpResponse.json(days);
  }),

  http.get(/\/api\/v1\/journal:search$/, ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const url = new URL(request.url);
    const q = (url.searchParams.get("q") ?? "").trim();
    const page = Number(url.searchParams.get("page") ?? "0");
    const size = Number(url.searchParams.get("size") ?? "20");
    if (!q) return problem(400, "`q` must not be blank.");
    const needle = q.toLowerCase();
    const matches = liveJournal().filter(
      (e) => (e.title ?? "").toLowerCase().includes(needle) || e.content.toLowerCase().includes(needle),
    );
    const start = page * size;
    const content = matches.slice(start, start + size).map((e) => {
      const haystack = e.content || e.title || "";
      const idx = haystack.toLowerCase().indexOf(needle);
      const snippet =
        idx === -1
          ? haystack.slice(0, 80)
          : haystack.slice(Math.max(0, idx - 20), idx) +
            `<b>${haystack.slice(idx, idx + needle.length)}</b>` +
            haystack.slice(idx + needle.length, idx + needle.length + 40);
      return { id: e.id, day: e.day, title: e.title, snippet, mood: e.mood, createdAt: e.createdAt };
    });
    return HttpResponse.json({ content, page, totalElements: matches.length });
  }),

  http.get("/kairon/api/v1/journal", ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const url = new URL(request.url);
    const day = url.searchParams.get("day");
    const from = url.searchParams.get("from");
    const to = url.searchParams.get("to");
    let rows = liveJournal();
    if (day) rows = rows.filter((e) => e.day === day);
    else if (from && to) rows = rows.filter((e) => e.day >= from && e.day <= to);
    else return problem(400, "Provide either `day` or both `from` and `to`.");
    return HttpResponse.json(sortJournalRows(rows));
  }),

  http.post("/kairon/api/v1/journal", async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as Partial<JournalEntry> & { day: string };
    const maxPos = liveJournal()
      .filter((e) => e.day === body.day)
      .reduce((m, e) => Math.max(m, e.position), 0);
    const created = makeJournalEntry({ ...body, position: maxPos + 100 });
    journalEntries.push(created);
    return HttpResponse.json(created, { status: 201 });
  }),

  http.patch("/kairon/api/v1/journal/:id", async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = journalEntries.find((e) => e.id === params.id);
    if (!row) return problem(404, "Journal entry not found.");
    const body = (await request.json()) as Partial<JournalEntry>;
    Object.assign(row, body, { updatedAt: new Date().toISOString() });
    return HttpResponse.json(row);
  }),

  http.delete("/kairon/api/v1/journal/:id", ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = journalEntries.find((e) => e.id === params.id);
    if (row) row.removed = true;
    return new HttpResponse(null, { status: 204 });
  }),

  // --- projects (M4) -----------------------------------------------------

  ...projectHandlers,
];
