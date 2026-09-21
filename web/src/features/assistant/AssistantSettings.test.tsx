import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import AccountPage from "@/features/auth/AccountPage";
import { useAuthStore } from "@/features/auth/authStore";
import { meResponse } from "@/test/msw/handlers";

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AccountPage />
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

test("toggling todo suggestions PATCHes preferences.assistant.todoSuggestions without touching other keys", async () => {
  const user = userEvent.setup();
  Object.assign(meResponse, { preferences: { todo: { rollover: "manual" } } });
  renderPage();

  const toggle = await screen.findByRole("checkbox", { name: /Todo suggestions/ });
  await user.click(toggle);

  await waitFor(() => {
    const prefs = meResponse.preferences as {
      todo?: { rollover?: string };
      assistant?: { todoSuggestions?: { enabled?: boolean } };
    };
    expect(prefs.assistant?.todoSuggestions?.enabled).toBe(true);
    expect(prefs.todo?.rollover).toBe("manual");
  });
});

// The full open-dropdown-and-pick-an-option interaction is exercised in the
// Playwright e2e spec instead of here — Radix Select's popover positioning
// depends on real layout/pointer-events computation that jsdom doesn't provide
// reliably, which made that interaction flake in this environment regardless
// of the fixes documented for it (pointerEventsCheck: 0 included). This test
// stays a non-interactive assertion that the trigger reflects the saved value.
test("the model select shows the currently saved override", async () => {
  Object.assign(meResponse, { preferences: { assistant: { modelOverride: "claude-opus-5" } } });
  renderPage();

  await waitFor(async () =>
    expect(await screen.findByRole("combobox", { name: /Model/ })).toHaveTextContent("Claude Opus 5"),
  );
});

test("data-sharing notice is visible before the toggle is ever touched", async () => {
  renderPage();

  expect(await screen.findByText(/sent to Anthropic/)).toBeInTheDocument();
});
