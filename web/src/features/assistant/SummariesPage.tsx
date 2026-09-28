import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { todayInZone } from "@/lib/date";

import { SummaryDetail } from "./SummaryDetail";
import { useAssistantRunsList, useRequestSummary } from "./useAssistantRuns";

const SUMMARY_KINDS = ["WEEKLY_SUMMARY", "MONTHLY_SUMMARY"];

const KIND_LABEL: Record<string, string> = {
  WEEKLY_SUMMARY: "Weekly",
  MONTHLY_SUMMARY: "Monthly",
};

const STATUS_VARIANT: Record<string, "default" | "secondary" | "outline"> = {
  PENDING: "outline",
  RUNNING: "outline",
  SUCCEEDED: "secondary",
  FAILED: "outline",
};

function periodLabel(periodStart?: string, periodEnd?: string): string {
  if (!periodStart || !periodEnd) return "";
  return periodStart === periodEnd ? periodStart : `${periodStart} – ${periodEnd}`;
}

/**
 * The execution-summaries history screen (M9 D18) — a standalone top-level
 * page (unlike M8/M8.5's "embed a button in an existing screen" pattern):
 * a "Generate weekly/monthly summary now" action and a history list, most
 * recent first. Selecting a row opens `SummaryDetail`, which polls while the
 * run is still in flight.
 */
export function SummariesPage() {
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const { data: page, isLoading } = useAssistantRunsList({ kind: SUMMARY_KINDS });
  const requestSummary = useRequestSummary();

  const runs = page?.content ?? [];

  const generate = (period: "WEEK" | "MONTH") => {
    requestSummary.mutate(
      { period, date: todayInZone("UTC") },
      { onSuccess: (run) => setSelectedRunId(run.id) },
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold">Summaries</h1>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" disabled={requestSummary.isPending} onClick={() => generate("WEEK")}>
            Generate weekly summary
          </Button>
          <Button variant="outline" disabled={requestSummary.isPending} onClick={() => generate("MONTH")}>
            Generate monthly summary
          </Button>
        </div>
      </div>

      {requestSummary.isError && (
        <p className="text-sm text-destructive" role="alert">
          {requestSummary.error.message || "Could not start a summary run."}
        </p>
      )}

      {isLoading ? null : runs.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No summaries yet — generate one above, or enable execution summaries in Account settings for
          automatic weekly/monthly generation.
        </p>
      ) : (
        <div className="divide-y rounded-lg border">
          {runs.map((run) => (
            <button
              key={run.id}
              type="button"
              className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left text-sm hover:bg-accent/50"
              onClick={() => setSelectedRunId(run.id)}
            >
              <span className="flex items-center gap-3">
                <span className="font-medium">{KIND_LABEL[run.kind] ?? run.kind}</span>
                <span className="text-muted-foreground">{periodLabel(run.periodStart, run.periodEnd)}</span>
              </span>
              <Badge variant={STATUS_VARIANT[run.status] ?? "outline"}>{run.status}</Badge>
            </button>
          ))}
        </div>
      )}

      <SummaryDetail runId={selectedRunId} onOpenChange={(open) => !open && setSelectedRunId(null)} />
    </div>
  );
}
