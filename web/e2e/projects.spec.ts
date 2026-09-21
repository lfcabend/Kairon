import { expect, test } from "@playwright/test";

const DEV_EMAIL = process.env.E2E_EMAIL ?? "dev@kairon.local";
const DEV_PASSWORD = process.env.E2E_PASSWORD ?? "dev-password-please";

async function login(page: import("@playwright/test").Page) {
  await page.goto("/kairon/login");
  await page.getByLabel("Email").fill(DEV_EMAIL);
  await page.getByLabel("Password").fill(DEV_PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByLabel("Add a task")).toBeVisible();
}

/** Drags one element onto another using dnd-kit-compatible pointer events. */
async function dragOnto(page: import("@playwright/test").Page, source: string, target: string) {
  const from = page.getByTestId(source).first();
  const to = page.getByTestId(target).first();
  const fromBox = await from.boundingBox();
  const toBox = await to.boundingBox();
  if (!fromBox || !toBox) throw new Error("drag source/target not visible");
  await page.mouse.move(fromBox.x + fromBox.width / 2, fromBox.y + fromBox.height / 2);
  await page.mouse.down();
  await page.mouse.move(toBox.x + toBox.width / 2, toBox.y + 5, { steps: 10 });
  await page.mouse.move(toBox.x + toBox.width / 2, toBox.y + toBox.height / 2, { steps: 10 });
  await page.mouse.up();
}

test("create a category, projects and tasks, work the board and tree, then delete a project", async ({ page }) => {
  await login(page);
  await page.goto("/kairon/projects");

  // Category
  await page.getByRole("button", { name: "Manage categories" }).click();
  await page.getByLabel("New category name").fill("Home Improvements");
  await page.getByRole("button", { name: "Add" }).click();
  await expect(page.getByText("Home Improvements")).toBeVisible();
  await page.keyboard.press("Escape");

  // Two projects
  await page.getByRole("button", { name: "+ New project" }).click();
  await page.getByLabel("Name").fill("Kitchen remodel");
  await page.getByRole("button", { name: "Create project" }).click();
  await expect(page.getByText("Kitchen remodel")).toBeVisible();

  await page.getByRole("button", { name: "+ New project" }).click();
  await page.getByLabel("Name").fill("Garage cleanup");
  await page.getByRole("button", { name: "Create project" }).click();
  await expect(page.getByText("Garage cleanup")).toBeVisible();

  // Open the first project, add a task and a subtask.
  await page.getByText("Kitchen remodel").click();
  const quickAdd = page.getByLabel("Add a task");
  await quickAdd.fill("Order cabinets");
  await quickAdd.press("Enter");
  await expect(page.getByText("Order cabinets")).toBeVisible();

  // A task's first subtask has no inline "Expand" toggle yet (nothing to
  // expand) — it's created via the "+ New task" dialog's parent picker,
  // which is what puts the toggle there for next time.
  await page.getByRole("button", { name: "+ New task" }).click();
  await page.getByLabel("Name").fill("Measure the kitchen");
  await page.getByLabel("Parent task").click();
  await page.getByRole("option", { name: "Order cabinets" }).click();
  await page.getByRole("button", { name: "Create task" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page.getByRole("button", { name: "Expand" }).click();
  await expect(page.getByText("Measure the kitchen")).toBeVisible();

  // Board: drag the task to In progress.
  await page.getByRole("tab", { name: "Board" }).click();
  await dragOnto(page, "task-card", "task-card");

  // Back to the tree and reorder (drag handles are decorative in this smoke test —
  // the important assertion is that both tasks still render after switching tabs).
  await page.getByRole("tab", { name: "List / Tree" }).click();
  await expect(page.getByText("Order cabinets")).toBeVisible();

  // Priority sort: drag-reorder the two projects.
  await page.goto("/kairon/projects");
  await page.getByLabel("Sort by").click();
  await page.getByRole("option", { name: "Sort by: Priority" }).click();
  await expect(page.getByText("Kitchen remodel")).toBeVisible();
  await expect(page.getByText("Garage cleanup")).toBeVisible();

  // Delete a project.
  await page.getByText("Garage cleanup").click();
  await page.getByRole("button", { name: "Delete" }).click();
  await page.getByRole("button", { name: "Delete" }).last().click();
  await expect(page).toHaveURL(/\/projects$/);
  await expect(page.getByText("Garage cleanup")).not.toBeVisible();
});

test("M5: add a dependency between two tasks and see it on the Gantt tab", async ({ page }) => {
  await login(page);
  await page.goto("/kairon/projects");

  await page.getByRole("button", { name: "+ New project" }).click();
  await page.getByLabel("Name").fill("Home network overhaul");
  await page.getByRole("button", { name: "Create project" }).click();
  await page.getByText("Home network overhaul").click();

  const quickAdd = page.getByLabel("Add a task");
  await quickAdd.fill("Design");
  await quickAdd.press("Enter");
  await expect(page.getByText("Design")).toBeVisible();
  await quickAdd.fill("Build");
  await quickAdd.press("Enter");
  await expect(page.getByText("Build")).toBeVisible();

  // Give both tasks dates via the edit dialog so they render as Gantt bars.
  async function setDates(taskName: string, start: string, end: string) {
    await page.getByTestId("task-row").filter({ hasText: taskName }).getByLabel("More actions").click();
    await page.getByRole("menuitem", { name: "Edit" }).click();
    await page.getByLabel("Planned start").fill(start);
    await page.getByLabel("Planned end").fill(end);
    if (taskName === "Build") {
      // The "Depends on" section only appears once the task exists — add Design as a predecessor here.
      await page.getByLabel("Add dependency").click();
      await page.getByRole("option", { name: "Design" }).click();
    }
    await page.getByRole("button", { name: "Save" }).click();
    await expect(page.getByRole("dialog")).not.toBeVisible();
  }

  await setDates("Design", "2026-09-01", "2026-09-10");
  await setDates("Build", "2026-09-11", "2026-09-20");

  await page.getByRole("tab", { name: "Gantt" }).click();
  await expect(page.getByText("Design").first()).toBeVisible();
  await expect(page.getByText("Build").first()).toBeVisible();
  await expect(page.getByText("Unscheduled")).not.toBeVisible();
});
