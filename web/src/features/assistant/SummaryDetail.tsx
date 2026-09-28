import type { Components } from "react-markdown";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

import { useAssistantRun } from "./useAssistantRuns";

// No `@tailwindcss/typography` plugin in this app (keep dependencies
// minimal, M9 D17) — style the handful of markdown elements a summary
// narrative + stats table actually uses directly, instead of pulling in
// `prose` classes that would otherwise silently no-op.
const MARKDOWN_COMPONENTS: Components = {
  h2: ({ children }) => <h2 className="mt-4 text-base font-semibold first:mt-0">{children}</h2>,
  h3: ({ children }) => <h3 className="mt-3 text-sm font-semibold">{children}</h3>,
  p: ({ children }) => <p className="mt-2 text-sm leading-relaxed first:mt-0">{children}</p>,
  ul: ({ children }) => <ul className="mt-2 list-disc space-y-1 pl-5 text-sm">{children}</ul>,
  li: ({ children }) => <li>{children}</li>,
  strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
  table: ({ children }) => (
    <div className="mt-3 overflow-x-auto">
      <table className="w-full border-collapse text-xs">{children}</table>
    </div>
  ),
  thead: ({ children }) => <thead className="border-b">{children}</thead>,
  th: ({ children }) => <th className="px-2 py-1 text-left font-medium text-muted-foreground">{children}</th>,
  td: ({ children }) => <td className="border-t px-2 py-1">{children}</td>,
};

interface Props {
  runId: string | null;
  onOpenChange: (open: boolean) => void;
}

const KIND_LABEL: Record<string, string> = {
  WEEKLY_SUMMARY: "Weekly summary",
  MONTHLY_SUMMARY: "Monthly summary",
};

function periodLabel(run: { periodStart?: string; periodEnd?: string }): string {
  if (!run.periodStart || !run.periodEnd) return "";
  return run.periodStart === run.periodEnd ? run.periodStart : `${run.periodStart} – ${run.periodEnd}`;
}

/**
 * Renders one run's `outputMarkdown` (narrative + the deterministic stats
 * table) read-only, via `react-markdown` + `remark-gfm` (M9 D17) — the first
 * read-only markdown/GFM-table render in this app. While the run is still
 * `PENDING`/`RUNNING`, polls instead (`useAssistantRun`'s default) and shows
 * a spinner.
 */
export function SummaryDetail({ runId, onOpenChange }: Props) {
  const { data: run, isLoading } = useAssistantRun(runId, { poll: true });

  return (
    <Dialog open={runId !== null} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{run ? `${KIND_LABEL[run.kind] ?? run.kind} — ${periodLabel(run)}` : "Summary"}</DialogTitle>
        </DialogHeader>

        {isLoading && !run ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : !run ? (
          <p className="text-sm text-muted-foreground">Not found.</p>
        ) : run.status === "PENDING" || run.status === "RUNNING" ? (
          <div className="flex items-center gap-3 py-8 text-sm text-muted-foreground" role="status">
            <span
              className="h-4 w-4 animate-spin rounded-full border-2 border-muted-foreground border-t-transparent"
              aria-hidden="true"
            />
            Generating your summary…
          </div>
        ) : run.status === "FAILED" ? (
          <p className="text-sm text-destructive" role="alert">
            {run.error || "This summary could not be generated."}
          </p>
        ) : (
          <div className="max-h-[70vh] overflow-y-auto">
            <ReactMarkdown remarkPlugins={[remarkGfm]} components={MARKDOWN_COMPONENTS}>
              {run.outputMarkdown ?? ""}
            </ReactMarkdown>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
