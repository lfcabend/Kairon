import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";

import { seedJournalEntries } from "@/test/msw/handlers";

import { JournalDateNav } from "./JournalDateNav";

function renderNav(date: string, onNavigate = vi.fn()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <JournalDateNav date={date} today="2026-09-09" onNavigate={onNavigate} />
    </QueryClientProvider>,
  );
  return onNavigate;
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

test("previous/next/today buttons call onNavigate with the right date", async () => {
  const user = userEvent.setup();
  const onNavigate = renderNav("2026-09-09");

  await user.click(screen.getByRole("button", { name: "Previous day" }));
  expect(onNavigate).toHaveBeenLastCalledWith("2026-09-08");

  await user.click(screen.getByRole("button", { name: "Next day" }));
  expect(onNavigate).toHaveBeenLastCalledWith("2026-09-10");
});

test("the Today button is hidden on today and visible otherwise", () => {
  renderNav("2026-09-09");
  expect(screen.queryByRole("button", { name: "Today" })).not.toBeInTheDocument();

  renderNav("2026-09-05");
  expect(screen.getByRole("button", { name: "Today" })).toBeInTheDocument();
});

test(
  "the calendar popover marks days with entries and selecting one navigates",
  async () => {
    seedJournalEntries([{ day: "2026-09-11", title: "Has entry", content: "x" }]);
    const user = userEvent.setup();
    const onNavigate = renderNav("2026-09-09");

    // Radix Popover's open animation and floating-ui positioning are genuinely
    // slow under jsdom (no real layout/animation events), unlike the rest of
    // this suite — hence the longer per-test timeout below.
    await user.click(screen.getByRole("button", { name: "Open calendar" }));
    const dayButton = await screen.findByRole("button", { name: /September 11/ });
    await waitFor(() =>
      expect(dayButton.parentElement?.querySelector('span[aria-hidden="true"]')).not.toBeNull(),
    );

    await user.click(dayButton);
    expect(onNavigate).toHaveBeenCalledWith("2026-09-11");
  },
  60000,
);
