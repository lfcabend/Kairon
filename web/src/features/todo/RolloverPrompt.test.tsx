import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";
import type { RolloverMode } from "@/lib/api/types";
import { addDays, todayInZone } from "@/lib/date";

import { allTodos, meResponse, seedTodos } from "@/test/msw/handlers";

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

function setMode(mode: RolloverMode) {
  meResponse.preferences = { todo: { rollover: mode } };
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

test("manual mode shows the banner and rolls everything on click", async () => {
  setMode("manual");
  seedTodos([
    { day: addDays(TODAY, -2), title: "Stale one", status: "OPEN" },
    { day: addDays(TODAY, -2), title: "Stale two", status: "OPEN" },
  ]);
  const user = userEvent.setup();
  renderApp(`/day/${TODAY}`);

  await user.click(await screen.findByRole("button", { name: "Roll over" }));

  expect(await screen.findByText("Stale one")).toBeInTheDocument();
  expect(screen.getByText("Stale two")).toBeInTheDocument();
  // Sources were cancelled; two carried copies now exist on today.
  await waitFor(() =>
    expect(allTodos().filter((t) => t.status === "CANCELLED")).toHaveLength(2),
  );
});

test("pick mode opens the dialog and rolls only the checked subset", async () => {
  setMode("pick");
  seedTodos([
    { day: addDays(TODAY, -1), title: "Keep me", status: "OPEN" },
    { day: addDays(TODAY, -1), title: "Not this one", status: "OPEN" },
  ]);
  const user = userEvent.setup();
  renderApp(`/day/${TODAY}`);

  await user.click(await screen.findByRole("button", { name: "Roll over" }));
  await screen.findByText("Roll over unfinished tasks");

  // Uncheck "Not this one".
  const notThis = screen.getByText("Not this one").closest("label")!;
  await user.click(notThis.querySelector('[role="checkbox"]')!);

  await user.click(screen.getByRole("button", { name: /Roll over 1/ }));

  await waitFor(() => {
    const cancelled = allTodos().filter((t) => t.status === "CANCELLED");
    expect(cancelled).toHaveLength(1);
    expect(cancelled[0].title).toBe("Keep me");
  });
});

test("auto mode rolls on mount for today and offers an Undo", async () => {
  setMode("auto");
  seedTodos([{ day: addDays(TODAY, -3), title: "Auto carried", status: "OPEN" }]);
  renderApp(`/day/${TODAY}`);

  expect(await screen.findByText(/Rolled 1 item from 1 earlier day/)).toBeInTheDocument();
  await waitFor(() =>
    expect(allTodos().filter((t) => t.status === "CANCELLED")).toHaveLength(1),
  );

  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Undo" }));

  await waitFor(() =>
    expect(allTodos().filter((t) => t.status === "CANCELLED")).toHaveLength(0),
  );
});

test("auto mode does not roll while browsing a past day", async () => {
  setMode("auto");
  seedTodos([{ day: addDays(TODAY, -3), title: "Should stay open", status: "OPEN" }]);
  renderApp(`/day/${addDays(TODAY, -1)}`);

  // Give any effect a chance to run.
  await screen.findByLabelText("Add a task");
  await new Promise((r) => setTimeout(r, 50));

  expect(screen.queryByText(/Rolled/)).not.toBeInTheDocument();
  expect(allTodos().every((t) => t.status === "OPEN")).toBe(true);
});
