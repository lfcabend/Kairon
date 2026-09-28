import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, expect, test } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";

import { seedAssistantRun } from "@/test/msw/handlers";

import { SummaryDetail } from "./SummaryDetail";

function renderDetail(runId: string | null) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SummaryDetail runId={runId} onOpenChange={() => {}} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

test("renders a GFM pipe table as an actual table, not raw pipe-syntax text", async () => {
  const run = seedAssistantRun({
    kind: "WEEKLY_SUMMARY",
    status: "SUCCEEDED",
    periodStart: "2026-09-21",
    periodEnd: "2026-09-27",
    outputMarkdown:
      "## This week\n\nA finished narrative.\n\n## Stats\n\n" +
      "| Metric | Value |\n| --- | --- |\n| Todos completed | 3 |\n",
  });

  renderDetail(run.id);

  const table = await screen.findByRole("table");
  expect(table).toBeInTheDocument();
  expect(screen.getByRole("columnheader", { name: "Metric" })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "Todos completed" })).toBeInTheDocument();
  expect(screen.queryByText(/\| Metric \| Value \|/)).not.toBeInTheDocument();
});

test("shows a spinner while the run is still PENDING", async () => {
  const run = seedAssistantRun({ kind: "WEEKLY_SUMMARY", status: "PENDING" });

  renderDetail(run.id);

  expect(await screen.findByRole("status")).toHaveTextContent("Generating your summary…");
});

test("shows the error detail when the run FAILED", async () => {
  const run = seedAssistantRun({
    kind: "WEEKLY_SUMMARY",
    status: "FAILED",
    error: "The assistant could not complete this request.",
  });

  renderDetail(run.id);

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "The assistant could not complete this request.",
  );
});
