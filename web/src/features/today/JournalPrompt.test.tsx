import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";
import { todayInZone } from "@/lib/date";

import { seedJournalEntries } from "@/test/msw/handlers";

const TODAY = todayInZone("UTC");

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

test("quick-add on Enter creates an entry and switches to the continue link", async () => {
  const user = userEvent.setup();
  renderApp("/today");

  const input = await screen.findByLabelText("Quick journal entry");
  await user.type(input, "A quick note.{Enter}");

  expect(await screen.findByRole("link", { name: "Continue in Journal →" })).toBeInTheDocument();
  expect(screen.queryByLabelText("Quick journal entry")).not.toBeInTheDocument();
});

test("with an existing entry, no input is shown and a preview of it is shown instead", async () => {
  seedJournalEntries([{ day: TODAY, content: "Already wrote today." }]);
  renderApp("/today");

  expect(await screen.findByText("Already wrote today.")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Continue in Journal →" })).toBeInTheDocument();
  expect(screen.queryByLabelText("Quick journal entry")).not.toBeInTheDocument();
});
