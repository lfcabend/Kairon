import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";
import { addDays, todayInZone } from "@/lib/date";

import { seedTodos } from "@/test/msw/handlers";

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

test("renders the seeded list for the day", async () => {
  seedTodos([
    { day: TODAY, title: "First task", position: 100 },
    { day: TODAY, title: "Second task", position: 200 },
  ]);
  renderApp(`/day/${TODAY}`);

  expect(await screen.findByText("First task")).toBeInTheDocument();
  expect(screen.getByText("Second task")).toBeInTheDocument();
});

test("quick-add with Enter appends a row", async () => {
  const user = userEvent.setup();
  renderApp(`/day/${TODAY}`);

  const input = await screen.findByLabelText("Add a task");
  await user.type(input, "Buy milk{Enter}");

  expect(await screen.findByText("Buy milk")).toBeInTheDocument();
  expect(input).toHaveValue("");
});

test("the checkbox toggles completion", async () => {
  const user = userEvent.setup();
  seedTodos([{ day: TODAY, title: "Finish report", position: 100 }]);
  renderApp(`/day/${TODAY}`);

  const row = (await screen.findByText("Finish report")).closest("li")!;
  await user.click(within(row).getByRole("checkbox"));

  await waitFor(() => expect(screen.getByText("Finish report")).toHaveClass("line-through"));
});

test("the next-day control changes the fetched day", async () => {
  const user = userEvent.setup();
  seedTodos([
    { day: TODAY, title: "Today item", position: 100 },
    { day: addDays(TODAY, 1), title: "Tomorrow item", position: 100 },
  ]);
  renderApp(`/day/${TODAY}`);

  expect(await screen.findByText("Today item")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Next day" }));

  expect(await screen.findByText("Tomorrow item")).toBeInTheDocument();
  expect(screen.queryByText("Today item")).not.toBeInTheDocument();
});

test("shows the empty state when the day has no tasks", async () => {
  renderApp(`/day/${TODAY}`);
  expect(await screen.findByText("Nothing planned for this day yet.")).toBeInTheDocument();
});
