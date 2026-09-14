import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";

import { seedProjectTasks, seedProjects, seedTaskDependencies } from "@/test/msw/handlers";

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

test("scheduled tasks render on the Gantt tab, a milestone included", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Home network overhaul" }])[0];
  seedProjectTasks([
    { projectId: project.id, name: "Run new drops", plannedStart: "2026-09-01", plannedEnd: "2026-09-10" },
    {
      projectId: project.id,
      name: "Cutover",
      isMilestone: true,
      plannedStart: "2026-09-12",
      plannedEnd: "2026-09-12",
    },
  ]);
  renderApp(`/projects/${project.id}`);

  await user.click(await screen.findByRole("tab", { name: "Gantt" }));

  expect((await screen.findAllByText("Run new drops")).length).toBeGreaterThan(0);
  expect((await screen.findAllByText("Cutover")).length).toBeGreaterThan(0);
});

test("double-clicking a bar opens the task form", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Home network overhaul" }])[0];
  seedProjectTasks([
    { projectId: project.id, name: "Run new drops", plannedStart: "2026-09-01", plannedEnd: "2026-09-10" },
  ]);
  renderApp(`/projects/${project.id}`);

  await user.click(await screen.findByRole("tab", { name: "Gantt" }));
  const [, barLabel] = await screen.findAllByText("Run new drops");
  // The SVG label has `pointer-events: none` (the bar underneath is the real hit target),
  // so double-click its enclosing `<g>` (where gantt-task-react's onDoubleClick lives) directly.
  fireEvent.doubleClick(barLabel.closest("g")!);

  expect(await screen.findByRole("heading", { name: "Edit task" })).toBeInTheDocument();
  expect(screen.getByLabelText("Name")).toHaveValue("Run new drops");
});

test("bars are colored by task status, overridden by the violation color", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Home network overhaul" }])[0];
  seedProjectTasks([
    {
      projectId: project.id,
      name: "Design",
      status: "DONE",
      plannedStart: "2026-09-01",
      plannedEnd: "2026-09-10",
    },
    {
      projectId: project.id,
      name: "Build",
      status: "IN_PROGRESS",
      plannedStart: "2026-09-11",
      plannedEnd: "2026-09-20",
    },
  ]);
  renderApp(`/projects/${project.id}`);

  await user.click(await screen.findByRole("tab", { name: "Gantt" }));
  await screen.findAllByText("Design");

  expect(document.body.querySelector('rect[fill="#34d399"]')).not.toBeNull(); // DONE
  expect(document.body.querySelector('rect[fill="#38bdf8"]')).not.toBeNull(); // IN_PROGRESS
});

test("an unscheduled task appears in the Unscheduled panel, not as a bar", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Home network overhaul" }])[0];
  seedProjectTasks([{ projectId: project.id, name: "Someday task" }]);
  renderApp(`/projects/${project.id}`);

  await user.click(await screen.findByRole("tab", { name: "Gantt" }));

  expect(await screen.findByText("Unscheduled")).toBeInTheDocument();
  expect(await screen.findByText("Someday task")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Set dates" })).toBeInTheDocument();
});

test("a violated FS dependency shows a schedule warning", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Home network overhaul" }])[0];
  const [predecessor, successor] = seedProjectTasks([
    { projectId: project.id, name: "Design", plannedStart: "2026-09-01", plannedEnd: "2026-09-10" },
    { projectId: project.id, name: "Build", plannedStart: "2026-09-05", plannedEnd: "2026-09-15" },
  ]);
  seedTaskDependencies([{ predecessorId: predecessor.id, successorId: successor.id, type: "FS", lagDays: 0 }]);
  renderApp(`/projects/${project.id}`);

  await user.click(await screen.findByRole("tab", { name: "Gantt" }));

  expect(await screen.findByText("Schedule warnings")).toBeInTheDocument();
  expect(screen.getByText(/starts before/)).toHaveTextContent('"Build" starts before "Design" finishes.');
});
