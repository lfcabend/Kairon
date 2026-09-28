import type {
  AcceptProjectPlanBody,
  AssistantRun,
  AssistantRunListFilter,
  AssistantRunPage,
  AssistantSuggestedTask,
  GenerateProjectPlanBody,
  Project,
  RequestSummaryBody,
  SuggestedProjectPlan,
  TodoItem,
  TodoSuggestionBody,
} from "./types";

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

  generateProjectPlan: (body: GenerateProjectPlanBody) =>
    apiFetch<AssistantRun>("/assistant/project-plan", { method: "POST", body }),

  acceptProjectPlan: (id: string, excludedTaskKeys: string[]) =>
    apiFetch<Project>(`/assistant/suggested-projects/${id}:accept`, {
      method: "POST",
      body: { excludedTaskKeys } satisfies AcceptProjectPlanBody,
    }),

  dismissProjectPlan: (id: string) =>
    apiFetch<SuggestedProjectPlan>(`/assistant/suggested-projects/${id}:dismiss`, { method: "POST" }),

  requestSummary: (body: RequestSummaryBody) =>
    apiFetch<AssistantRun>("/assistant/summaries", { method: "POST", body }),

  listRuns: (filter: AssistantRunListFilter = {}) => {
    const params = new URLSearchParams();
    for (const kind of filter.kind ?? []) params.append("kind", kind);
    if (filter.from) params.set("from", filter.from);
    if (filter.to) params.set("to", filter.to);
    params.set("page", String(filter.page ?? 0));
    params.set("pageSize", String(filter.pageSize ?? 20));
    return apiFetch<AssistantRunPage>(`/assistant/runs?${params.toString()}`);
  },
};
