import type { AssistantRunListFilter } from "@/lib/api/types";

/** Query-key factory for the assistant feature. */
export const assistantKeys = {
  run: (id: string) => ["assistant", "run", id] as const,
  runs: {
    list: (filter: AssistantRunListFilter = {}) => ["assistant", "runs", "list", filter] as const,
  },
};
