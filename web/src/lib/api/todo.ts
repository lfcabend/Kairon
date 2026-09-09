import type {
  CreateTodoBody,
  PatchTodoBody,
  ReorderBody,
  RolloverBody,
  RolloverPreview,
  RolloverUndoBody,
  TodoItem,
} from "./types";

import { apiFetch } from "./client";

/** Thin wrapper over the `/todo` endpoints, mirroring `authApi`. */
export const todoApi = {
  list: (day: string) => apiFetch<TodoItem[]>(`/todo?day=${day}`),

  range: (from: string, to: string) => apiFetch<TodoItem[]>(`/todo?from=${from}&to=${to}`),

  create: (body: CreateTodoBody) => apiFetch<TodoItem>("/todo", { method: "POST", body }),

  patch: (id: string, body: PatchTodoBody) =>
    apiFetch<TodoItem>(`/todo/${id}`, { method: "PATCH", body }),

  remove: (id: string) => apiFetch<void>(`/todo/${id}`, { method: "DELETE" }),

  complete: (id: string, complete: boolean) =>
    apiFetch<TodoItem>(`/todo/${id}:complete`, { method: "POST", body: { complete } }),

  reorder: (body: ReorderBody) =>
    apiFetch<TodoItem[]>("/todo:reorder", { method: "POST", body }),

  rolloverPreview: (onDay: string) =>
    apiFetch<RolloverPreview>(`/todo/rollover-preview?onDay=${onDay}`),

  rollover: (body: RolloverBody) =>
    apiFetch<{ rolledOver: TodoItem[] }>("/todo:rollover", { method: "POST", body }),

  rolloverUndo: (body: RolloverUndoBody) =>
    apiFetch<{ reopened: TodoItem[] }>("/todo:rollover-undo", { method: "POST", body }),
};
