import path from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// The SPA is served from the backend origin in every real environment; only the
// dev server needs to reach the API, which it proxies to the Spring Boot app.
export default defineConfig({
  // The backend mounts the whole app under this path (server.servlet.context-path
  // in application.yml) so several personal apps can share one host/Tailscale
  // Funnel node. Must match that value and the Helm chart's ingress.path.
  base: "/kairon/",
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/kairon": "http://localhost:8080",
    },
  },
  build: {
    outDir: "dist",
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: true,
    // Playwright specs under e2e/ are run by `npm run test:e2e`, not Vitest.
    exclude: ["e2e/**", "node_modules/**", "dist/**"],
  },
});
