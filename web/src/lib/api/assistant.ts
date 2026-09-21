import type { AssistantRun, AssistantSuggestedTask, TodoItem, TodoSuggestionBody } from "./types";

import { apiFetch } from "./client";

/** Thin wrapper over `/assistant/**`, mirroring `planningApi`/`todoApi`. */
export const assistantApi = {
  suggestTodos: (body: TodoSuggestionBody) =>
    apiFetch<AssistantRun>("/assistant/todo-suggestions", { method: "POST", body }),

  getRun: (id: string) => apiFetch<AssistantRun>(`/assistant/runs/${id}`),

  deleteRun: (id: string) => apiFetch<void>(`/assistant/runs/${id}`, { method: "DELETE" }),

  acceptSuggestedTask: (id: string) =>
    apiFetch<TodoItem>(`/assistant/suggested-tasks/${id}:accept`, { method: "POST" }),

  dismissSuggestedTask: (id: string) =>
    apiFetch<AssistantSuggestedTask>(`/assistant/suggested-tasks/${id}:dismiss`, { method: "POST" }),
};
