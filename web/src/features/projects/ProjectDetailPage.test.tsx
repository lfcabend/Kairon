import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";

import { seedProjectTasks, seedProjects } from "@/test/msw/handlers";

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

test("renders the project header and its task tree", async () => {
  const project = seedProjects([{ name: "Home network overhaul" }])[0];
  seedProjectTasks([{ projectId: project.id, name: "Run new drops" }]);
  renderApp(`/projects/${project.id}`);

  expect(await screen.findByRole("heading", { name: "Home network overhaul" })).toBeInTheDocument();
  expect(await screen.findByText("Run new drops")).toBeInTheDocument();
});

test("Tabs switch between tree and board without losing task state", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Home network overhaul" }])[0];
  seedProjectTasks([{ projectId: project.id, name: "Run new drops" }]);
  renderApp(`/projects/${project.id}`);

  expect(await screen.findByText("Run new drops")).toBeInTheDocument();

  await user.click(screen.getByRole("tab", { name: "Board" }));
  expect(await screen.findByTestId("task-card")).toHaveTextContent("Run new drops");

  await user.click(screen.getByRole("tab", { name: "List / Tree" }));
  expect(await screen.findByTestId("task-row")).toHaveTextContent("Run new drops");
});

test("the quick-add row creates a top-level task", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Home network overhaul" }])[0];
  renderApp(`/projects/${project.id}`);

  const input = await screen.findByLabelText("Add a task");
  await user.type(input, "Buy a switch{Enter}");

  expect(await screen.findByText("Buy a switch")).toBeInTheDocument();
  expect(input).toHaveValue("");
});

test("shows the empty state when the project has no tasks", async () => {
  const project = seedProjects([{ name: "Empty project" }])[0];
  renderApp(`/projects/${project.id}`);

  expect(await screen.findByText("No tasks yet.")).toBeInTheDocument();
});
