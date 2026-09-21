import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { planningApi } from "@/lib/api/planning";

import { todoKeys } from "../todo/todoKeys";
import { planningKeys } from "./planningKeys";

/**
 * `TodayPage` reads only `.dueProjectTasks` and `.journalPrompt` from this —
 * the interactive todo list reuses `useTodos(date)` instead (D4).
 */
export function useToday(date: string) {
  return useQuery({
    queryKey: planningKeys.today(date),
    queryFn: () => planningApi.today(date),
    enabled: !!date,
  });
}

/**
 * No optimistic append (D9's "Added" state already reads live from the
 * refetched `useTodos(date)` cache once this invalidates it).
 */
export function usePromoteTask(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (projectTaskId: string) => planningApi.promote({ projectTaskId, day: date }),
    onSuccess: () => qc.invalidateQueries({ queryKey: todoKeys.day(date) }),
  });
}
