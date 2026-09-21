import { useMutation, useQueryClient } from "@tanstack/react-query";

import { assistantApi } from "@/lib/api/assistant";
import type { Horizon } from "@/lib/api/types";

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
