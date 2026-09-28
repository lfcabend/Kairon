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
 * Same `FakeAnthropicClient` local-profile stand-in as M8/M8.5's own e2e specs
 * (docs/milestones/M8-assistant-foundations.md §9 Q3), extended for M9 with a
 * canned `generateSummary` response — never a real network call to Anthropic.
 * Unlike those two, `POST /assistant/summaries` returns `PENDING` immediately
 * and the real `@Async` path (SummaryGenerationService, real DB aggregation,
 * real `renderStatsTable()`) completes it in the background — this spec is
 * also the one end-to-end check that the table-rendering path itself works,
 * not just the LLM stub (docs/milestones/M9-execution-summaries.md §7).
 */
test("enable execution summaries, generate a weekly summary, and see it resolve via polling", async ({ page }) => {
  await login(page);

  await page.getByRole("link", { name: "Account" }).click();
  await page.getByRole("checkbox", { name: "Execution summaries" }).click();
  await expect(page.getByRole("checkbox", { name: "Execution summaries" })).toBeChecked();

  await page.getByRole("link", { name: "Summaries" }).click();
  await page.getByRole("button", { name: "Generate weekly summary" }).click();

  const dialog = page.getByRole("dialog");
  await expect(dialog.getByText("Generating your summary…")).toBeVisible();

  // Polling resolves this once the async run completes — the fake client's
  // canned narrative plus the real, deterministically-rendered stats table.
  await expect(dialog.getByText(/canned e2e summary/i)).toBeVisible();
  await expect(dialog.getByRole("table")).toBeVisible();
  await expect(dialog.getByRole("columnheader", { name: "Metric" })).toBeVisible();

  await page.getByRole("button", { name: "Close" }).click();
  await expect(page.getByText("Weekly").first()).toBeVisible();
  await expect(page.getByText("SUCCEEDED").first()).toBeVisible();
});
