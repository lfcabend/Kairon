import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";

import { seedCategories, seedProjects } from "@/test/msw/handlers";

import { ProjectPriorityList } from "./ProjectPriorityList";

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

test("renders projects grouped into category sections", async () => {
  const category = seedCategories([{ name: "Home" }])[0];
  seedProjects([
    { name: "Alpha", categoryId: category.id },
    { name: "Beta" },
  ]);
  renderApp("/projects");

  expect(await screen.findByText("Home")).toBeInTheDocument();
  expect(screen.getByText("Alpha")).toBeInTheDocument();
  expect(await screen.findByText("Uncategorized")).toBeInTheDocument();
  expect(screen.getByText("Beta")).toBeInTheDocument();
});

test("shows the empty state when there are no projects", async () => {
  renderApp("/projects");
  expect(await screen.findByText("No projects yet.")).toBeInTheDocument();
});

test("creating a project via the dialog adds it to the list", async () => {
  const user = userEvent.setup();
  renderApp("/projects");

  await user.click(await screen.findByRole("button", { name: "+ New project" }));
  await user.type(screen.getByLabelText("Name"), "New venture");
  await user.click(screen.getByRole("button", { name: "Create project" }));

  expect(await screen.findByText("New venture")).toBeInTheDocument();
});

test("the quick filter narrows the rendered projects by name without refetching", async () => {
  const category = seedCategories([{ name: "Home" }])[0];
  seedProjects([
    { name: "Alpha", categoryId: category.id },
    { name: "Beta" },
  ]);
  const user = userEvent.setup();
  renderApp("/projects");

  expect(await screen.findByText("Alpha")).toBeInTheDocument();
  expect(screen.getByText("Beta")).toBeInTheDocument();

  await user.type(screen.getByLabelText("Filter projects"), "alp");

  expect(screen.getByText("Alpha")).toBeInTheDocument();
  expect(screen.queryByText("Beta")).not.toBeInTheDocument();
});

test("a project past the old 50-item default page size still renders in its category section", async () => {
  const category = seedCategories([{ name: "Investing" }])[0];
  seedProjects(Array.from({ length: 54 }, (_, i) => ({ name: `Filler ${i + 1}` })));
  seedProjects([{ name: "Lijfrente polis", categoryId: category.id }]);
  renderApp("/projects");

  expect(await screen.findByText("Investing")).toBeInTheDocument();
  expect(screen.getByText("Lijfrente polis")).toBeInTheDocument();
});

test("the priority list renders every non-archived project flat, without category grouping", async () => {
  const category = seedCategories([{ name: "Home" }])[0];
  seedProjects([{ name: "Alpha", categoryId: category.id }, { name: "Beta" }]);
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}
    >
      <MemoryRouter>
        <ProjectPriorityList draggable />
      </MemoryRouter>
    </QueryClientProvider>,
  );

  expect(await screen.findByText("Alpha")).toBeInTheDocument();
  expect(screen.getByText("Beta")).toBeInTheDocument();
  expect(screen.queryByText("Home")).not.toBeInTheDocument();
});
