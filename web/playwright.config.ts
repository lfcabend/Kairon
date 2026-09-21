import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end config for the M2 happy path. Runs against a full stack — the packaged
 * jar (API + built SPA) plus a PostgreSQL — reachable at `E2E_BASE_URL`
 * (default `http://localhost:8080`). CI starts that stack (jar + Testcontainers
 * Postgres, `local` profile so `DevDataSeeder` creates the login) before invoking
 * `npm run test:e2e`.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  // All specs share one dev-user account (docs/milestones), so different spec
  // files running as separate workers race each other's writes (e.g. two
  // files both creating a project named the same thing). Must stay serial.
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // Locally, also write the interactive HTML report to playwright-report/ (view
  // it with `npx playwright show-report`); `open: "never"` so a script running
  // the suite (task e2e) doesn't get a browser tab popped mid-run.
  reporter: process.env.CI ? "github" : [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:8080",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
