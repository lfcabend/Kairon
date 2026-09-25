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

/**
 * Same `FakeAnthropicClient` local-profile stand-in as M8's todo-suggestions
 * e2e spec (docs/milestones/M8-assistant-foundations.md §9 Q3), extended for
 * M8.5 with a canned `generateProjectPlan` response — never a real network
 * call to Anthropic. The per-user opt-in still happens for real, through
 * Settings, same as production.
 */
test("enable project generation, generate a plan from Projects, and create it", async ({ page }) => {
  await login(page);

  await page.getByRole("link", { name: "Account" }).click();
  await page.getByRole("checkbox", { name: "Project generation" }).click();
  await expect(page.getByRole("checkbox", { name: "Project generation" })).toBeChecked();

  await page.getByRole("link", { name: "Projects" }).click();
  await page.getByRole("button", { name: "New project from description" }).click();

  // Playwright's getByLabel matches substrings case-insensitively by default,
  // and the dialog's own accessible name ("New project from description")
  // contains "description" — exact: true avoids that false match.
  await page.getByLabel("Description", { exact: true }).fill("Remodel the kitchen: cabinets, countertops, and paint.");
  await page.getByLabel("Start date", { exact: true }).fill("2026-10-01");
  await page.getByRole("button", { name: "Generate plan" }).click();

  const dialog = page.getByRole("dialog");
  // Three tasks, each with its own exclude checkbox.
  await expect(dialog.getByRole("checkbox")).toHaveCount(3);
  // Role-based lookup for the unambiguous names, not getByText: Playwright's
  // text matching is a case-insensitive substring by default, and "Design"
  // alone would also match the milestone's "◆ Design approved" row.
  await expect(dialog.getByRole("checkbox", { name: /Pick materials/ })).toBeVisible();
  await expect(dialog.getByRole("checkbox", { name: /Design approved/ })).toBeVisible();

  await dialog.getByRole("button", { name: "Create project" }).click();

  await expect(page.getByRole("heading", { name: "Generated project" })).toBeVisible();
  await expect(page.getByText("Design").first()).toBeVisible();

  await page.getByRole("tab", { name: "Gantt" }).click();
  await expect(page.getByText("Design").first()).toBeVisible();
  await expect(page.getByText("Design approved").first()).toBeVisible();
});
