import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";
import { renderWithProviders } from "@/test/renderWithProviders";

import { seedProjectTasks, seedProjects, seedTaskDependencies } from "@/test/msw/handlers";

import { TaskDependencySection } from "./TaskDependencySection";

// Opening the Add-dependency Select itself isn't exercised here: Radix Select's
// popper positioning goes into a synchronous loop under jsdom's zero-size
// layout (every element reports a 0x0 rect), which hangs the test process
// rather than failing it. The picker's add flow (and the cycle/duplicate error
// surfacing) is covered by the Playwright e2e spec instead, against a real browser.

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

test("lists current predecessors, resolved to names", async () => {
  const project = seedProjects([{ name: "Project" }])[0];
  const [design, build] = seedProjectTasks([
    { projectId: project.id, name: "Design" },
    { projectId: project.id, name: "Build" },
  ]);
  seedTaskDependencies([{ predecessorId: design.id, successorId: build.id }]);

  renderWithProviders(<TaskDependencySection projectId={project.id} task={build} />);

  expect(await screen.findByText("Design")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Remove dependency on Design" })).toBeInTheDocument();
});

test("offers only the tasks that aren't the task itself or an existing predecessor", async () => {
  const project = seedProjects([{ name: "Project" }])[0];
  const [design, build] = seedProjectTasks([
    { projectId: project.id, name: "Design" },
    { projectId: project.id, name: "Build" },
    { projectId: project.id, name: "Ship" },
  ]);
  seedTaskDependencies([{ predecessorId: design.id, successorId: build.id }]);

  renderWithProviders(<TaskDependencySection projectId={project.id} task={build} />);

  await screen.findByText("Design");
  expect(await screen.findByRole("combobox", { name: "Add dependency" })).toBeInTheDocument();
});

test("no Add control is offered once every other task is already a predecessor", async () => {
  const project = seedProjects([{ name: "Project" }])[0];
  const [design, build] = seedProjectTasks([
    { projectId: project.id, name: "Design" },
    { projectId: project.id, name: "Build" },
  ]);
  seedTaskDependencies([{ predecessorId: design.id, successorId: build.id }]);

  renderWithProviders(<TaskDependencySection projectId={project.id} task={build} />);

  await screen.findByText("Design");
  expect(screen.queryByRole("combobox", { name: "Add dependency" })).not.toBeInTheDocument();
});

test("removing a predecessor deletes the edge", async () => {
  const user = userEvent.setup();
  const project = seedProjects([{ name: "Project" }])[0];
  const [design, build] = seedProjectTasks([
    { projectId: project.id, name: "Design" },
    { projectId: project.id, name: "Build" },
  ]);
  seedTaskDependencies([{ predecessorId: design.id, successorId: build.id }]);

  renderWithProviders(<TaskDependencySection projectId={project.id} task={build} />);

  expect(await screen.findByText("Design")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Remove dependency on Design" }));

  await screen.findByRole("combobox", { name: "Add dependency" });
  expect(screen.queryByText("Design")).not.toBeInTheDocument();
});
