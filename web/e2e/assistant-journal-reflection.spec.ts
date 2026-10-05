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
 * Same `FakeAnthropicClient` local-profile stand-in as M8/M8.5/M9's own e2e
 * specs (docs/milestones/M8-assistant-foundations.md §9 Q3), extended here
 * with a canned `generateReflection` response — never a real network call to
 * Anthropic. Enabling the feature goes through the confirmation dialog (M10
 * D13), unlike every other checkbox on this page.
 */
test("enable journal reflection via the confirmation dialog, seed an entry, generate a weekly reflection, and see it resolve via polling", async ({ page }) => {
  await login(page);

  // A journal entry for the week `POST /assistant/journal-reflection` will
  // default to ("this week", via todayInZone("UTC")) — write to today's entry.
  await page.goto("/kairon/journal");
  await page.getByRole("button", { name: /New entry/i }).click();
  const editor = page.locator('[contenteditable="true"]');
  await editor.click();
  await editor.pressSequentially("Felt good about this week's progress.");
  await page.getByRole("button", { name: "Done" }).click();
  await expect(page.getByText("Felt good about this week's progress.")).toBeVisible();

  await page.getByRole("link", { name: "Account" }).click();
  await page.getByRole("checkbox", { name: "Journal reflection" }).click();
  await expect(page.getByText(/full text/)).toBeVisible();
  await page.getByRole("button", { name: "Enable journal reflection" }).click();
  await expect(page.getByRole("checkbox", { name: "Journal reflection" })).toBeChecked();

  await page.getByRole("link", { name: "Summaries" }).click();
  await page.getByRole("button", { name: "Generate weekly reflection" }).click();

  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText("Generating your summary…")).toBeVisible();

  // Polling resolves this once the async run completes — the fake client's
  // canned reflection narrative.
  await expect(dialog.getByText(/canned e2e/i)).toBeVisible();

  await page.getByRole("button", { name: "Close" }).click();
  await expect(page.getByText("Weekly reflection").first()).toBeVisible();
  await expect(page.getByText("SUCCEEDED").first()).toBeVisible();
});
