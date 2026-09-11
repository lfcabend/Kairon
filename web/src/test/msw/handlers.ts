import { HttpResponse, http } from "msw";

import type { AuthResponse, JournalEntry, Me, TodoItem } from "@/lib/api/types";

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

/**
 * Baseline happy-path handlers. Individual tests narrow behaviour with
 * `server.use(...)` and seed rows with `seedTodos(...)`/`seedJournalEntries(...)`.
 */
export const handlers = [
  http.get("/api/v1/ping", () => HttpResponse.json({ pong: true, version: "test" })),

  http.post("/api/v1/auth/register", () =>
    HttpResponse.json(authResponse("access-registered"), { status: 201 }),
  ),

  http.post("/api/v1/auth/login", async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    if (body.password === "wrong-password") {
      return problem(401, "Invalid email or password.");
    }
    return HttpResponse.json(authResponse("access-login"));
  }),

  http.post("/api/v1/auth/refresh", () => problem(401, "Missing refresh token.")),

  http.post("/api/v1/auth/logout", () => new HttpResponse(null, { status: 204 })),
  http.post("/api/v1/auth/logout-all", () => new HttpResponse(null, { status: 204 })),

  http.get("/api/v1/me", ({ request }) => {
    if (authed(request)) return HttpResponse.json(meResponse);
    return problem(401, "Authentication required.");
  }),

  http.patch("/api/v1/me", async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const patch = (await request.json()) as Partial<Me>;
    Object.assign(meResponse, patch);
    return HttpResponse.json(meResponse);
  }),

  // --- todo -----------------------------------------------------------

  http.get("/api/v1/todo", ({ request }) => {
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

  http.post("/api/v1/todo", async ({ request }) => {
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

  http.patch("/api/v1/todo/:id", async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = todos.find((t) => t.id === params.id);
    if (!row) return problem(404, "Todo item not found.");
    const body = (await request.json()) as Partial<TodoItem>;
    Object.assign(row, body, { updatedAt: new Date().toISOString() });
    return HttpResponse.json(row);
  }),

  http.delete("/api/v1/todo/:id", ({ request, params }) => {
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

  http.get("/api/v1/todo/rollover-preview", ({ request }) => {
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

  http.get("/api/v1/journal/entry-days", ({ request }) => {
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

  http.get("/api/v1/journal", ({ request }) => {
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

  http.post("/api/v1/journal", async ({ request }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const body = (await request.json()) as Partial<JournalEntry> & { day: string };
    const maxPos = liveJournal()
      .filter((e) => e.day === body.day)
      .reduce((m, e) => Math.max(m, e.position), 0);
    const created = makeJournalEntry({ ...body, position: maxPos + 100 });
    journalEntries.push(created);
    return HttpResponse.json(created, { status: 201 });
  }),

  http.patch("/api/v1/journal/:id", async ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = journalEntries.find((e) => e.id === params.id);
    if (!row) return problem(404, "Journal entry not found.");
    const body = (await request.json()) as Partial<JournalEntry>;
    Object.assign(row, body, { updatedAt: new Date().toISOString() });
    return HttpResponse.json(row);
  }),

  http.delete("/api/v1/journal/:id", ({ request, params }) => {
    if (!authed(request)) return problem(401, "Authentication required.");
    const row = journalEntries.find((e) => e.id === params.id);
    if (row) row.removed = true;
    return new HttpResponse(null, { status: 204 });
  }),
];
