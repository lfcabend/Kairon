import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";
import { meResponse } from "@/test/msw/handlers";

import AccountPage from "./AccountPage";

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

test("selecting a rollover mode PATCHes preferences.todo.rollover", async () => {
  const user = userEvent.setup();
  renderPage();

  const automatic = await screen.findByLabelText(/Automatic/);
  await user.click(automatic);

  await waitFor(() =>
    expect((meResponse.preferences as { todo?: { rollover?: string } }).todo?.rollover).toBe(
      "auto",
    ),
  );
});
