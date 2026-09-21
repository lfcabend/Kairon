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

test("add a task, complete it, navigate a day, and roll it over", async ({ page }) => {
  await login(page);

  // M6 made Today (not Day) the post-login landing page; this test exercises
  // the Day view's date-nav specifically, so go there explicitly.
  await page.getByRole("link", { name: "Todo" }).click();

  // Add a task on the previous day, then roll it forward to today.
  await page.getByRole("button", { name: "Previous day" }).click();
  const quickAdd = page.getByLabel("Add a task");
  await quickAdd.fill("Ship the M2 milestone");
  await quickAdd.press("Enter");
  await expect(page.getByText("Ship the M2 milestone")).toBeVisible();

  // Complete it, then reopen so it is eligible for rollover.
  const row = page.getByTestId("todo-row").filter({ hasText: "Ship the M2 milestone" });
  await row.getByRole("checkbox").click();
  await expect(row.getByText("Ship the M2 milestone")).toHaveClass(/line-through/);
  await row.getByRole("checkbox").click();

  // Back to today and roll the unfinished item over (manual mode default).
  await page.getByRole("button", { name: "Today" }).click();
  await page.getByRole("button", { name: "Roll over" }).click();
  await expect(page.getByText("Ship the M2 milestone")).toBeVisible();
});
