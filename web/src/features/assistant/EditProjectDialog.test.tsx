import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";

import { meResponse, seedProjectEdit, seedProjects, seedProjectTasks } from "@/test/msw/handlers";

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
  Object.assign(meResponse, { preferences: { assistant: { projectEditing: { enabled: true } } } });
}

async function openAndGenerate(user: ReturnType<typeof userEvent.setup>, description = "add an inspection task") {
  await user.click(await screen.findByRole("button", { name: "Edit with AI" }));
  await user.type(screen.getByLabelText("Describe the change"), description);
  await user.click(screen.getByRole("button", { name: "Generate changes" }));
}

test("requesting an edit without opting in shows the backend's 403 message", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Kitchen remodel" }])[0];
  renderApp(`/projects/${project.id}`);

  await openAndGenerate(user);

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "You haven't enabled project editing in Settings.",
  );
});

test("reviewing a diff shows added/updated/removed tasks, cascades exclusion, and applies the change", async () => {
  const user = userEvent.setup();
  optIn();
  const project = seedProjects([{ name: "Kitchen remodel" }])[0];
  const [design, oldPlumbing] = seedProjectTasks([
    { projectId: project.id, name: "Design" },
    { projectId: project.id, name: "Old plumbing" },
  ]);
  seedProjectEdit({
    projectChanges: { name: null, description: null, size: null, startDate: null, endDate: null,
      categoryId: null, categoryName: "Renovation" },
    taskOperations: [
      { op: "ADD", existingTaskId: null, key: "n1", parentRef: null, name: "Final inspection",
        description: null, isMilestone: false, plannedStart: null, plannedEnd: null, estimateHours: null },
      { op: "ADD", existingTaskId: null, key: "n2", parentRef: "n1", name: "Sub-inspection",
        description: null, isMilestone: false, plannedStart: null, plannedEnd: null, estimateHours: null },
      { op: "UPDATE", existingTaskId: design.id, key: null, parentRef: null, name: "Design (revised)",
        description: null, isMilestone: false, plannedStart: null, plannedEnd: null, estimateHours: null },
      { op: "REMOVE", existingTaskId: oldPlumbing.id, key: null, parentRef: null, name: null,
        description: null, isMilestone: false, plannedStart: null, plannedEnd: null, estimateHours: null },
    ],
    dependencyOperations: [],
    reorderOperations: [],
  });
  renderApp(`/projects/${project.id}`);

  await openAndGenerate(user, "add inspection, rename design, drop plumbing");

  const dialog = await screen.findByRole("dialog");
  const finalInspection = within(dialog).getByRole("checkbox", { name: /Final inspection/ });
  const subInspection = within(dialog).getByRole("checkbox", { name: /Sub-inspection/ });
  expect(within(dialog).getByRole("checkbox", { name: /Design.*Design \(revised\)/ })).toBeInTheDocument();
  expect(within(dialog).getByRole("checkbox", { name: /Old plumbing/ })).toBeInTheDocument();
  expect(within(dialog).getByRole("checkbox", { name: /New category: Renovation/ })).toBeInTheDocument();

  // Excluding the new top-level task disables the new sub-task that parents under it.
  expect(subInspection).not.toBeDisabled();
  await user.click(finalInspection);
  expect(subInspection).toBeDisabled();

  await user.click(within(dialog).getByRole("button", { name: "Apply changes" }));

  expect(await screen.findByText("Design (revised)")).toBeInTheDocument();
  expect(screen.queryByText("Old plumbing")).not.toBeInTheDocument();
  expect(screen.queryByText("Final inspection")).not.toBeInTheDocument();
});

test("dismissing the review discards the diff without changing the project", async () => {
  const user = userEvent.setup();
  optIn();
  const project = seedProjects([{ name: "Kitchen remodel" }])[0];
  seedProjectTasks([{ projectId: project.id, name: "Design" }]);
  renderApp(`/projects/${project.id}`);

  await openAndGenerate(user);

  const dialog = await screen.findByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

  expect(screen.queryByText("Final inspection")).not.toBeInTheDocument();
  expect(await screen.findByText("Design")).toBeInTheDocument();
});
