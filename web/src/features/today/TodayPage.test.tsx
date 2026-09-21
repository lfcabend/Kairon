import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";
import { todayInZone } from "@/lib/date";

import { seedProjectTasks, seedProjects, seedTodos } from "@/test/msw/handlers";

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

test("renders today's todos, due tasks, and the empty journal prompt", async () => {
  seedTodos([{ day: TODAY, title: "Existing todo", position: 100 }]);
  const project = seedProjects([{ name: "Kitchen remodel" }])[0];
  seedProjectTasks([
    { projectId: project.id, name: "Order cabinets", plannedStart: TODAY, plannedEnd: TODAY },
  ]);

  renderApp("/today");

  expect(await screen.findByText("Existing todo")).toBeInTheDocument();
  expect(await screen.findByText("Order cabinets")).toBeInTheDocument();
  expect(await screen.findByLabelText("Quick journal entry")).toBeInTheDocument();
});

test("is the default landing page", async () => {
  seedTodos([{ day: TODAY, title: "Land here", position: 100 }]);
  renderApp("/");

  expect(await screen.findByText("Land here")).toBeInTheDocument();
});

test("today's list is interactive: completing a task works exactly as on /day", async () => {
  const user = userEvent.setup();
  seedTodos([{ day: TODAY, title: "Finish report", position: 100 }]);
  renderApp("/today");

  const row = (await screen.findByText("Finish report")).closest("li")!;
  await user.click(within(row).getByRole("checkbox"));

  expect(await screen.findByText("Finish report")).toHaveClass("line-through");
});
