import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";
import { todayInZone } from "@/lib/date";

import { seedProjectTasks, seedProjects } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";

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

test('"Add to today" promotes the task and flips the button to "Added"', async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Kitchen remodel" }])[0];
  seedProjectTasks([
    { projectId: project.id, name: "Order cabinets", plannedStart: TODAY, plannedEnd: TODAY },
  ]);
  renderApp("/today");

  const button = await screen.findByRole("button", { name: "Add to today" });
  await user.click(button);

  expect(await screen.findByRole("button", { name: "Added" })).toBeDisabled();
  expect(await screen.findAllByText("Order cabinets")).not.toHaveLength(0);
});

test("a 404 from the server surfaces as an inline error, not a thrown exception", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Kitchen remodel" }])[0];
  seedProjectTasks([
    { projectId: project.id, name: "Order cabinets", plannedStart: TODAY, plannedEnd: TODAY },
  ]);
  server.use(
    http.post(/\/api\/v1\/planning\/today:promote$/, () =>
      HttpResponse.json({ status: 404, detail: "Task not found." }, { status: 404 }),
    ),
  );
  renderApp("/today");

  await user.click(await screen.findByRole("button", { name: "Add to today" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("Task not found.");
});
