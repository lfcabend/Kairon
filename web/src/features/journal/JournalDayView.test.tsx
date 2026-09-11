import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
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

test("renders the seeded entries for the day", async () => {
  seedJournalEntries([
    { day: TODAY, title: "Morning plan", content: "Ship the plan", position: 100 },
    { day: TODAY, title: "Evening reflection", content: "Went well", position: 200 },
  ]);
  renderApp(`/journal/${TODAY}`);

  expect(await screen.findByText("Morning plan")).toBeInTheDocument();
  expect(screen.getByText("Evening reflection")).toBeInTheDocument();
});

test("shows the empty state when the day has no entries", async () => {
  renderApp(`/journal/${TODAY}`);
  expect(await screen.findByText("Nothing written for this day yet.")).toBeInTheDocument();
});

test("new entry adds a row and expands it for editing", async () => {
  const user = userEvent.setup();
  renderApp(`/journal/${TODAY}`);

  await screen.findByText("Nothing written for this day yet.");
  await user.click(screen.getByRole("button", { name: /New entry/i }));

  expect(await screen.findByLabelText("Entry title")).toBeInTheDocument();
});

test("delete removes the entry", async () => {
  const user = userEvent.setup();
  seedJournalEntries([{ day: TODAY, title: "Gone soon", content: "bye", position: 100 }]);
  renderApp(`/journal/${TODAY}`);

  await user.click(await screen.findByText("Gone soon"));
  await user.click(screen.getByRole("button", { name: "Delete entry" }));

  await waitFor(() => expect(screen.queryByText("Gone soon")).not.toBeInTheDocument());
});
