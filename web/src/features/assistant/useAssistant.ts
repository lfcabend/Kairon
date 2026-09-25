import { useMutation, useQueryClient } from "@tanstack/react-query";

import { assistantApi } from "@/lib/api/assistant";
import type { GenerateProjectPlanBody, Horizon } from "@/lib/api/types";

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
