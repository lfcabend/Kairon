import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";

import { meResponse, seedAssistantRun } from "@/test/msw/handlers";

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
