import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { assistantApi } from "@/lib/api/assistant";
import type {
  AssistantRun,
  AssistantRunListFilter,
  JournalReflectionRequestBody,
  RequestSummaryBody,
} from "@/lib/api/types";

import { assistantKeys } from "./assistantKeys";

// A run stays in flight for at most a minute or two (cf. M8.5's observed
// ~90s project-plan latency) — a short fixed interval is the simplest viable
// choice; revisit only if real usage shows it's wasteful (M9 §9 Q1).
const POLL_INTERVAL_MS = 2000;

const IN_FLIGHT: AssistantRun["status"][] = ["PENDING", "RUNNING"];

/** The summaries history list — paginated, most recent first. */
export function useAssistantRunsList(filter: AssistantRunListFilter = {}) {
  return useQuery({
    queryKey: assistantKeys.runs.list(filter),
    queryFn: () => assistantApi.listRuns(filter),
  });
}

/**
 * One run, polled at a short fixed interval while it's `PENDING`/`RUNNING`
 * (the first `refetchInterval`-based polling hook in this app — M9's summary
 * runs are the first genuinely asynchronous, always-polled assistant
 * feature). Pass `poll: false` to fetch once without polling.
 */
export function useAssistantRun(id: string | null, options: { poll?: boolean } = {}) {
  const poll = options.poll ?? true;
  return useQuery({
    queryKey: assistantKeys.run(id ?? ""),
    queryFn: () => assistantApi.getRun(id!),
    enabled: id !== null,
    refetchInterval: (query) => {
      if (!poll) return false;
      const status = query.state.data?.status;
      return status && IN_FLIGHT.includes(status) ? POLL_INTERVAL_MS : false;
    },
  });
}

/** Invalidates the history list on success so the new PENDING row shows up immediately. */
export function useRequestSummary() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RequestSummaryBody) => assistantApi.requestSummary(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["assistant", "runs", "list"] }),
  });
}

/** Same shape as {@link useRequestSummary} — invalidates the history list on success (M10). */
export function useRequestJournalReflection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: JournalReflectionRequestBody) => assistantApi.requestJournalReflection(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["assistant", "runs", "list"] }),
  });
}
