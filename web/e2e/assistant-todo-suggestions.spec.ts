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
 * The `local` profile (backend/src/main/resources/application-local.yml)
 * enables the assistant module with `FakeAnthropicClient` standing in for the
 * real SDK client, so this never makes a real network call to Anthropic
 * (docs/milestones/M8-assistant-foundations.md §9 Q3) — the per-user opt-in
 * still has to happen for real, through Settings, same as production.
 */
test("enable todo suggestions, request them from Today, and accept one", async ({ page }) => {
  await login(page);

  await page.getByRole("link", { name: "Account" }).click();
  // Radix's Checkbox is a custom-ARIA button, not a native <input type="checkbox">
  // — .check() verifies via the DOM checked property, which it doesn't have, so
  // use a plain click and assert the resulting aria-checked state instead.
  await page.getByRole("checkbox", { name: "Todo suggestions" }).click();
  await expect(page.getByRole("checkbox", { name: "Todo suggestions" })).toBeChecked();

  // The model override select — covered here rather than in the Vitest unit
  // test, where Radix Select's popover flaked under jsdom (see
  // AssistantSettings.test.tsx).
  await page.getByRole("combobox", { name: "Model" }).click();
  await page.getByRole("option", { name: "Claude Opus 5" }).click();
  await expect(page.getByRole("combobox", { name: "Model" })).toHaveText("Claude Opus 5");

  await page.getByRole("link", { name: "Today" }).click();
  await page.getByRole("button", { name: "Suggest todos" }).click();
  await page.getByRole("button", { name: "Suggest" }).click();

  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText("Order cabinet hardware")).toBeVisible();

  const row = dialog.locator("li").filter({ hasText: "Order cabinet hardware" });
  await row.getByRole("button", { name: "Accept" }).click();
  await expect(dialog.getByText("Order cabinet hardware")).not.toBeVisible();

  await dialog.getByRole("button", { name: "Done" }).click();
  await expect(page.getByText("Order cabinet hardware")).toBeVisible();
});
