import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";
import { todayInZone } from "@/lib/date";

import { meResponse, seedAssistantSuggestions } from "@/test/msw/handlers";

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

function optIn() {
  Object.assign(meResponse, { preferences: { assistant: { todoSuggestions: { enabled: true } } } });
}

test("requesting suggestions without opting in shows the backend's 403 message", async () => {
  const user = userEvent.setup();
  renderApp("/today");

  await user.click(await screen.findByRole("button", { name: "Suggest todos" }));
  await user.click(await screen.findByRole("button", { name: "Suggest" }));

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "You haven't enabled todo suggestions in Settings.",
  );
});

test("accepting a suggestion adds it to the day's todo list and removes it from the review dialog", async () => {
  const user = userEvent.setup();
  optIn();
  seedAssistantSuggestions([
    { title: "Order cabinet hardware", rationale: "Overdue kitchen-remodel task", suggestedForDay: TODAY },
    { title: "Write homepage copy", rationale: "Open but not urgent", suggestedForDay: TODAY },
  ]);
  renderApp("/today");

  await user.click(await screen.findByRole("button", { name: "Suggest todos" }));
  await user.click(await screen.findByRole("button", { name: "Suggest" }));

  const dialog = screen.getByRole("dialog");
  const row = (await within(dialog).findByText("Order cabinet hardware")).closest("li")!;
  await user.click(within(row).getByRole("button", { name: "Accept" }));

  expect(await within(dialog).findByText("Done")).toBeInTheDocument();
  expect(within(dialog).queryByText("Order cabinet hardware")).not.toBeInTheDocument();
  await user.click(within(dialog).getByRole("button", { name: "Done" }));

  expect(await screen.findAllByText("Order cabinet hardware")).not.toHaveLength(0);
});

test("dismissing a suggestion removes it from the dialog without creating a todo", async () => {
  const user = userEvent.setup();
  optIn();
  seedAssistantSuggestions([
    { title: "Write homepage copy", rationale: "Open but not urgent", suggestedForDay: TODAY },
  ]);
  renderApp("/today");

  await user.click(await screen.findByRole("button", { name: "Suggest todos" }));
  await user.click(await screen.findByRole("button", { name: "Suggest" }));

  const row = (await screen.findByText("Write homepage copy")).closest("li")!;
  await user.click(within(row).getByRole("button", { name: "Dismiss" }));

  expect(screen.queryByText("Write homepage copy")).not.toBeInTheDocument();
});
