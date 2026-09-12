import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";

import { seedCategories, seedProjects } from "@/test/msw/handlers";

function renderApp(route: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
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

test("creating a category from the manager dialog", async () => {
  const user = userEvent.setup();
  renderApp("/projects");

  await user.click(await screen.findByRole("button", { name: "Manage categories" }));
  await user.type(screen.getByLabelText("New category name"), "Home");
  await user.click(screen.getByRole("button", { name: "Add" }));

  expect(await screen.findByText("Home")).toBeInTheDocument();
});

test("renaming a category in place", async () => {
  const user = userEvent.setup();
  seedCategories([{ name: "Home" }]);
  renderApp("/projects");

  await user.click(await screen.findByRole("button", { name: "Manage categories" }));
  const label = await screen.findByText("Home");
  await user.dblClick(label);
  const input = screen.getByDisplayValue("Home");
  await user.clear(input);
  await user.type(input, "Household{Enter}");

  expect(await screen.findByText("Household")).toBeInTheDocument();
});

test("deleting a category shows the affected project count", async () => {
  const user = userEvent.setup();
  const category = seedCategories([{ name: "Home" }])[0];
  seedProjects([{ name: "Alpha", categoryId: category.id }]);
  renderApp("/projects");

  await user.click(await screen.findByRole("button", { name: "Manage categories" }));
  await user.click(screen.getByRole("button", { name: "Delete Home" }));

  expect(await screen.findByText("1 project will become uncategorized.")).toBeInTheDocument();
});
