import { useMutation, useQueryClient } from "@tanstack/react-query";

import { assistantApi } from "@/lib/api/assistant";
import type { GenerateProjectEditBody, GenerateProjectPlanBody, Horizon } from "@/lib/api/types";

import { projectKeys } from "../projects/projectKeys";
import { todoKeys } from "../todo/todoKeys";

/**
 * The result is shown directly in `SuggestedTaskList` from the mutation's own
 * data — no query-cache entry for the run itself (mirrors `usePromoteTask`'s
 * "no optimistic machinery for a one-shot action" reasoning, M6 §6.3).
 */
export function useSuggestTodos() {
  return useMutation({
    mutationFn: ({ day, horizon }: { day: string; horizon: Horizon }) =>
      assistantApi.suggestTodos({ day, horizon }),
  });
}

/** Invalidates the accepted suggestion's own day, not a fixed date — a WEEK-horizon run can span several. */
export function useAcceptSuggestedTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id }: { id: string; day: string }) => assistantApi.acceptSuggestedTask(id),
    onSuccess: (_created, { day }) => qc.invalidateQueries({ queryKey: todoKeys.day(day) }),
  });
}

export function useDismissSuggestedTask() {
  return useMutation({
    mutationFn: (id: string) => assistantApi.dismissSuggestedTask(id),
  });
}

/** Same "no cache entry for the run itself" reasoning as `useSuggestTodos` (M8.5). */
export function useGenerateProjectPlan() {
  return useMutation({
    mutationFn: (body: GenerateProjectPlanBody) => assistantApi.generateProjectPlan(body),
  });
}

/** Invalidates the project list so the newly created project shows up if the user navigates back to it. */
export function useAcceptProjectPlan() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, excludedTaskKeys }: { id: string; excludedTaskKeys: string[] }) =>
      assistantApi.acceptProjectPlan(id, excludedTaskKeys),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["projects", "list"] }),
  });
}

export function useDismissProjectPlan() {
  return useMutation({
    mutationFn: (id: string) => assistantApi.dismissProjectPlan(id),
  });
}

/** Same "no cache entry for the run itself" reasoning as `useGenerateProjectPlan` (M9.5). */
export function useGenerateProjectEdit() {
  return useMutation({
    mutationFn: (body: GenerateProjectEditBody) => assistantApi.generateProjectEdit(body),
  });
}

/** Invalidates the edited project's own detail/tasks/dependencies so its page reflects the change immediately. */
export function useAcceptProjectEdit(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, excludedOperationKeys }: { id: string; excludedOperationKeys: string[] }) =>
      assistantApi.acceptProjectEdit(id, excludedOperationKeys),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: projectKeys.detail(projectId) });
      void qc.invalidateQueries({ queryKey: projectKeys.tasks(projectId) });
      void qc.invalidateQueries({ queryKey: projectKeys.dependencies(projectId) });
    },
  });
}

export function useDismissProjectEdit() {
  return useMutation({
    mutationFn: (id: string) => assistantApi.dismissProjectEdit(id),
  });
}
