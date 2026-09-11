import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";
import { renderWithProviders } from "@/test/renderWithProviders";

import { seedJournalEntries } from "@/test/msw/handlers";

import { JournalSearchPage } from "./JournalSearchPage";

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

test("typing a query lists ranked hits with a highlighted snippet", async () => {
  seedJournalEntries([
    { day: "2026-09-05", title: "Evening reflection", content: "Decided to ship the plan before dinner." },
    { day: "2026-09-06", title: "Unrelated", content: "Nothing to see here." },
  ]);
  const user = userEvent.setup();
  renderWithProviders(<JournalSearchPage />);

  await user.type(screen.getByLabelText("Search journal"), "ship");

  expect(await screen.findByText("Evening reflection")).toBeInTheDocument();
  expect(screen.queryByText("Unrelated")).not.toBeInTheDocument();
  expect(screen.getByText("ship", { selector: "strong" })).toBeInTheDocument();
});

test("shows a no-results message when nothing matches", async () => {
  const user = userEvent.setup();
  renderWithProviders(<JournalSearchPage />);

  await user.type(screen.getByLabelText("Search journal"), "nonexistent");

  expect(await screen.findByText(/No entries match/)).toBeInTheDocument();
});

test("clicking a hit navigates to its day", async () => {
  seedJournalEntries([{ day: "2026-09-05", title: "Evening reflection", content: "ship the plan" }]);
  const user = userEvent.setup();
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/journal/search"]}>
        <Routes>
          <Route path="/journal/search" element={<JournalSearchPage />} />
          <Route path="/journal/:date" element={<div>Day view for the clicked date</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

  await user.type(screen.getByLabelText("Search journal"), "ship");
  const hit = await screen.findByText("Evening reflection");
  await user.click(hit);

  expect(await screen.findByText("Day view for the clicked date")).toBeInTheDocument();
});
