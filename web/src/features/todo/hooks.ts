import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { todoApi } from "@/lib/api/todo";
import type { CreateTodoBody, PatchTodoBody, TodoItem } from "@/lib/api/types";
import { randomId } from "@/lib/id";

import { todoKeys } from "./todoKeys";

const byOrder = (a: TodoItem, b: TodoItem) =>
  a.position - b.position || a.createdAt.localeCompare(b.createdAt);

/**
 * Every rollover-preview query, for any `onDay` — invalidated after any
 * mutation that can change which items are "stranded open" (create,
 * complete/reopen, a status-changing patch, delete). Without this, a
 * rollover-preview fetched while viewing today goes stale the moment you
 * change something on a *different* day and come back, since DayView never
 * unmounts across a date change and the query key doesn't change either.
 */
function invalidateRolloverPreviews(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ["todo", "rollover-preview"] });
}

export function useTodos(date: string) {
  return useQuery({
    queryKey: todoKeys.day(date),
    queryFn: () => todoApi.list(date),
  });
}

/** Optimistically appends a temporary row; reconciles on success, rolls back on error. */
export function useCreateTodo(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateTodoBody) => todoApi.create(body),
    onMutate: async (body) => {
      await qc.cancelQueries({ queryKey: todoKeys.day(date) });
      const previous = qc.getQueryData<TodoItem[]>(todoKeys.day(date)) ?? [];
      const tempId = `temp-${randomId()}`;
      const maxPos = previous.reduce((m, t) => Math.max(m, t.position), 0);
      const optimistic: TodoItem = {
        id: tempId,
        day: body.day,
        title: body.title,
        notes: body.notes ?? null,
        status: "OPEN",
        priority: body.priority ?? 0,
        position: maxPos + 100,
        estimateMinutes: body.estimateMinutes ?? null,
        sourceProjectTaskId: body.sourceProjectTaskId ?? null,
        rolledOverFromId: null,
        completedAt: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        version: 0,
      };
      qc.setQueryData<TodoItem[]>(todoKeys.day(date), [...previous, optimistic]);
      return { previous, tempId };
    },
    onError: (_err, _body, ctx) => {
      if (ctx) qc.setQueryData(todoKeys.day(date), ctx.previous);
    },
    onSuccess: (created, _body, ctx) => {
      qc.setQueryData<TodoItem[]>(todoKeys.day(date), (list = []) =>
        list.map((t) => (t.id === ctx?.tempId ? created : t)).sort(byOrder),
      );
      invalidateRolloverPreviews(qc);
    },
  });
}

export function usePatchTodo(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: PatchTodoBody }) => todoApi.patch(id, body),
    onMutate: async ({ id, body }) => {
      await qc.cancelQueries({ queryKey: todoKeys.day(date) });
      const previous = qc.getQueryData<TodoItem[]>(todoKeys.day(date)) ?? [];
      qc.setQueryData<TodoItem[]>(
        todoKeys.day(date),
        previous.map((t) => (t.id === id ? { ...t, ...stripUndefined(body) } : t)),
      );
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx) qc.setQueryData(todoKeys.day(date), ctx.previous);
    },
    onSuccess: (updated) => {
      qc.setQueryData<TodoItem[]>(todoKeys.day(date), (list = []) =>
        list.map((t) => (t.id === updated.id ? updated : t)).sort(byOrder),
      );
      invalidateRolloverPreviews(qc);
    },
  });
}

export function useCompleteTodo(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, complete }: { id: string; complete: boolean }) =>
      todoApi.complete(id, complete),
    onMutate: async ({ id, complete }) => {
      await qc.cancelQueries({ queryKey: todoKeys.day(date) });
      const previous = qc.getQueryData<TodoItem[]>(todoKeys.day(date)) ?? [];
      qc.setQueryData<TodoItem[]>(
        todoKeys.day(date),
        previous.map((t) =>
          t.id === id
            ? {
                ...t,
                status: complete ? "DONE" : "OPEN",
                completedAt: complete ? new Date().toISOString() : null,
              }
            : t,
        ),
      );
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx) qc.setQueryData(todoKeys.day(date), ctx.previous);
    },
    onSuccess: (updated) => {
      qc.setQueryData<TodoItem[]>(todoKeys.day(date), (list = []) =>
        list.map((t) => (t.id === updated.id ? updated : t)),
      );
      invalidateRolloverPreviews(qc);
    },
  });
}

export function useReorderTodo(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (orderedIds: string[]) => todoApi.reorder({ day: date, orderedIds }),
    onMutate: async (orderedIds) => {
      await qc.cancelQueries({ queryKey: todoKeys.day(date) });
      const previous = qc.getQueryData<TodoItem[]>(todoKeys.day(date)) ?? [];
      const rank = new Map(orderedIds.map((id, i) => [id, i]));
      qc.setQueryData<TodoItem[]>(
        todoKeys.day(date),
        [...previous].sort((a, b) => (rank.get(a.id) ?? 0) - (rank.get(b.id) ?? 0)),
      );
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx) qc.setQueryData(todoKeys.day(date), ctx.previous);
    },
    onSuccess: (reordered) => {
      qc.setQueryData<TodoItem[]>(todoKeys.day(date), reordered);
    },
  });
}

export function useDeleteTodo(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => todoApi.remove(id),
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: todoKeys.day(date) });
      const previous = qc.getQueryData<TodoItem[]>(todoKeys.day(date)) ?? [];
      qc.setQueryData<TodoItem[]>(
        todoKeys.day(date),
        previous.filter((t) => t.id !== id),
      );
      return { previous };
    },
    onError: (_err, _id, ctx) => {
      if (ctx) qc.setQueryData(todoKeys.day(date), ctx.previous);
    },
    onSuccess: () => invalidateRolloverPreviews(qc),
  });
}

function stripUndefined<T extends object>(obj: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== undefined),
  ) as Partial<T>;
}
