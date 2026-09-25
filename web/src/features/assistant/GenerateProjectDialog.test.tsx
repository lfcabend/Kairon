import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";

import { meResponse, seedProjectPlan } from "@/test/msw/handlers";

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
  Object.assign(meResponse, { preferences: { assistant: { projectGeneration: { enabled: true } } } });
}

async function openAndGenerate(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: "New project from description" }));
  await user.type(screen.getByLabelText("Description"), "Kitchen remodel");
  await user.type(screen.getByLabelText("Start date"), "2026-10-01");
  await user.click(screen.getByRole("button", { name: "Generate plan" }));
}

test("generating a plan without opting in shows the backend's 403 message", async () => {
  const user = userEvent.setup();
  renderApp("/projects");

  await openAndGenerate(user);

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "You haven't enabled project generation in Settings.",
  );
});

test("reviewing a plan shows its task tree with milestone markers, cascades exclusion to children, and creates the project", async () => {
  const user = userEvent.setup();
  optIn();
  seedProjectPlan({
    name: "Kitchen remodel",
    tasks: [
      { key: "t1", parentKey: null, name: "Design", description: null, isMilestone: false,
        plannedStart: null, plannedEnd: null, estimateHours: null },
      { key: "t2", parentKey: "t1", name: "Pick materials", description: null, isMilestone: false,
        plannedStart: null, plannedEnd: null, estimateHours: null },
      { key: "m1", parentKey: null, name: "Design approved", description: null, isMilestone: true,
        plannedStart: null, plannedEnd: null, estimateHours: null },
    ],
    dependencies: [{ predecessorKey: "t1", successorKey: "m1", type: "FS", lagDays: 0 }],
  });
  renderApp("/projects");

  await openAndGenerate(user);

  const dialog = await screen.findByRole("dialog");
  expect(within(dialog).getByRole("checkbox", { name: "Design" })).toBeInTheDocument();
  expect(within(dialog).getByRole("checkbox", { name: "Pick materials" })).toBeInTheDocument();
  expect(within(dialog).getByRole("checkbox", { name: "◆ Design approved" })).toBeInTheDocument();

  // Excluding the parent ("Design") disables its child's checkbox too.
  await user.click(within(dialog).getByRole("checkbox", { name: "Design" }));
  expect(within(dialog).getByRole("checkbox", { name: "Pick materials" })).toBeDisabled();

  await user.click(within(dialog).getByRole("button", { name: "Create project" }));

  expect(await screen.findByRole("heading", { name: "Kitchen remodel" })).toBeInTheDocument();
});

test("dismissing the review discards the plan without creating a project", async () => {
  const user = userEvent.setup();
  optIn();
  seedProjectPlan({ name: "Kitchen remodel" });
  renderApp("/projects");

  await openAndGenerate(user);

  const dialog = await screen.findByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

  expect(screen.queryByText("Kitchen remodel")).not.toBeInTheDocument();
});
