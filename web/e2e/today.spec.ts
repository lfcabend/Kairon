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

const today = new Date().toISOString().slice(0, 10);

test("log in to the Today landing page, add a due task, and quick-journal", async ({ page }) => {
  await login(page);
  await expect(page).toHaveURL(/\/today$/);

  // Seed a project task due today via the Projects UI.
  await page.getByRole("link", { name: "Projects" }).click();
  await page.getByRole("button", { name: "+ New project" }).click();
  await page.getByLabel("Name").fill("Kitchen remodel");
  await page.getByRole("button", { name: "Create project" }).click();
  await page.getByText("Kitchen remodel").click();

  const quickAdd = page.getByLabel("Add a task");
  await quickAdd.fill("Order cabinets");
  await quickAdd.press("Enter");
  await expect(page.getByText("Order cabinets")).toBeVisible();

  await page.getByTestId("task-row").filter({ hasText: "Order cabinets" }).getByLabel("More actions").click();
  await page.getByRole("menuitem", { name: "Edit" }).click();
  await page.getByLabel("Planned start").fill(today);
  await page.getByLabel("Planned end").fill(today);
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();

  // Back on Today, promote the due task into today's list.
  await page.getByRole("link", { name: "Today" }).click();
  await expect(page.getByText("Order cabinets")).toBeVisible();
  await page.getByRole("button", { name: "Add to today" }).click();
  await expect(page.getByRole("button", { name: "Added" })).toBeVisible();

  // "Order cabinets" now shows twice: the promoted todo row, plus the due-task panel entry.
  await expect(page.getByText("Order cabinets")).toHaveCount(2);

  // Quick-journal from Today, then confirm it landed on the day view.
  const journalInput = page.getByLabel("Quick journal entry");
  await journalInput.fill("A quick note from Today.");
  await journalInput.press("Enter");
  await expect(page.getByRole("link", { name: "Continue in Journal →" })).toBeVisible();

  await page.getByRole("link", { name: "Continue in Journal →" }).click();
  await expect(page.getByText("A quick note from Today.")).toBeVisible();
});
