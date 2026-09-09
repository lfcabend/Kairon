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
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:8080",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
