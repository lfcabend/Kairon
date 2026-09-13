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

test("add a journal entry, bold some text, save, then find it via search", async ({ page }) => {
  await login(page);

  await page.getByRole("link", { name: "Journal" }).click();
  await page.getByRole("button", { name: /New entry/i }).click();

  const editor = page.locator('[contenteditable="true"]');
  await editor.click();
  await editor.pressSequentially("Ship the M3 milestone");

  // Bold the whole line, then blur to save (save-on-blur, no explicit Save button).
  await page.keyboard.press("ControlOrMeta+a");
  await page.getByRole("button", { name: "Bold" }).click();
  await page.getByRole("button", { name: "Done" }).click();

  await expect(page.getByText("Ship the M3 milestone")).toBeVisible();

  // Find it again via full-text search.
  await page.getByRole("link", { name: "Search" }).click();
  await page.getByLabel("Search journal").fill("milestone");
  await expect(page.getByText("Ship the M3 milestone", { exact: false })).toBeVisible();

  await page.getByText("Ship the M3 milestone", { exact: false }).click();
  await expect(page.getByText("Ship the M3 milestone")).toBeVisible();
});
