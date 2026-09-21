import { expect, test, type Locator, type Page } from "@playwright/test";

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

/**
 * The Radix `DropdownMenu` trigger occasionally opens then immediately
 * self-dismisses on a synthetic Playwright click (a known Radix + automated
 * click race, not an app bug — the same pattern is reliable under real
 * pointer input). Retry the trigger click once if the menu didn't open.
 */
async function openMoreActions(page: Page, trigger: Locator) {
  await trigger.click();
  const menu = page.getByRole("menu");
  if (!(await menu.isVisible().catch(() => false))) {
    await trigger.click();
  }
  await menu.waitFor({ state: "visible" });
}

test("log in to the Today landing page, add a due task, and quick-journal", async ({ page }) => {
  await login(page);
  await expect(page).toHaveURL(/\/today$/);

  // Seed a project task due today via the Projects UI. Distinct name from
  // projects.spec.ts's "Kitchen remodel" fixture — both persist for the rest
  // of the suite run (only "Garage cleanup" gets deleted there), and the
  // suite shares one dev-user account, so a same-named project here would
  // collide with getByText("Kitchen remodel") matching both.
  await page.getByRole("link", { name: "Projects" }).click();
  await page.getByRole("button", { name: "+ New project" }).click();
  await page.getByLabel("Name").fill("Deck rebuild");
  await page.getByRole("button", { name: "Create project" }).click();
  await page.getByText("Deck rebuild").click();

  const quickAdd = page.getByLabel("Add a task");
  await quickAdd.fill("Order cabinets");
  await quickAdd.press("Enter");
  await expect(page.getByText("Order cabinets")).toBeVisible();

  const taskRow = page.getByTestId("task-row").filter({ hasText: "Order cabinets" });
  await openMoreActions(page, taskRow.getByLabel("More actions"));
  await page.getByRole("menuitem", { name: "Edit" }).click();
  await page.getByLabel("Planned start").fill(today);
  await page.getByLabel("Planned end").fill(today);
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();

  // Back on Today, promote the due task into today's list. Scoped to its own
  // row: the M5 spec's "Design"/"Build" tasks are dated in the past, so
  // they're legitimately overdue here too, each with their own such button.
  await page.getByRole("link", { name: "Today" }).click();
  // Both text filters: the due-task row shows the project name too, unlike
  // the todo row this same task becomes once promoted below — needed so this
  // locator stays scoped to just the due-task row after that happens.
  const dueRow = page.locator("li").filter({ hasText: "Order cabinets" }).filter({ hasText: "Deck rebuild" });
  await expect(dueRow).toBeVisible();
  await dueRow.getByRole("button", { name: "Add to today" }).click();
  await expect(dueRow.getByRole("button", { name: "Added" })).toBeVisible();

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
