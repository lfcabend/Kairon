import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";
import { todayInZone } from "@/lib/date";

import { meResponse, seedAssistantRun, seedJournalEntries } from "@/test/msw/handlers";

function renderApp(route: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter
        initialEntries={[route]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

function optIn() {
  Object.assign(meResponse, { preferences: { assistant: { executionSummaries: { enabled: true } } } });
}

function optIntoReflection() {
  Object.assign(meResponse, { preferences: { assistant: { journalReflection: { enabled: true } } } });
}

test("renders history and clicking a row opens its detail", async () => {
  const user = userEvent.setup();
  seedAssistantRun({
    kind: "WEEKLY_SUMMARY",
    status: "SUCCEEDED",
    periodStart: "2026-09-21",
    periodEnd: "2026-09-27",
    outputMarkdown: "## This week\n\nA finished narrative.",
  });
  renderApp("/summaries");

  await user.click(await screen.findByText("2026-09-21 – 2026-09-27"));

  expect(await screen.findByText("A finished narrative.")).toBeInTheDocument();
});

test("generating a weekly summary adds a new row and opens it in the PENDING/RUNNING state", async () => {
  const user = userEvent.setup();
  optIn();
  renderApp("/summaries");

  await user.click(await screen.findByRole("button", { name: "Generate weekly summary" }));

  expect(await screen.findByText("Generating your summary…")).toBeInTheDocument();
});

test("generating without opting in shows the backend's 403 message", async () => {
  const user = userEvent.setup();
  renderApp("/summaries");

  await user.click(await screen.findByRole("button", { name: "Generate weekly summary" }));

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "You haven't enabled execution summaries in Settings.",
  );
});

test("the reflection button is absent when journal reflection is not opted into", async () => {
  renderApp("/summaries");

  await screen.findByRole("button", { name: "Generate weekly summary" });
  expect(screen.queryByRole("button", { name: "Generate weekly reflection" })).not.toBeInTheDocument();
});

test("the reflection button is present and generates a reflection run when opted in", async () => {
  const user = userEvent.setup();
  optIntoReflection();
  seedJournalEntries([{ day: todayInZone("UTC"), content: "Felt good about the week." }]);
  renderApp("/summaries");

  await user.click(await screen.findByRole("button", { name: "Generate weekly reflection" }));

  expect(await screen.findByText("Generating your summary…")).toBeInTheDocument();
});

test("a JOURNAL_REFLECTION row renders with the right kind label", async () => {
  const user = userEvent.setup();
  optIntoReflection();
  seedAssistantRun({
    kind: "JOURNAL_REFLECTION",
    status: "SUCCEEDED",
    periodStart: "2026-09-21",
    periodEnd: "2026-09-27",
    outputMarkdown: "### Patterns\n\nA finished reflection.",
  });
  renderApp("/summaries");

  expect(await screen.findByText("Weekly reflection")).toBeInTheDocument();
  await user.click(await screen.findByText("2026-09-21 – 2026-09-27"));
  expect(await screen.findByText("A finished reflection.")).toBeInTheDocument();
});
