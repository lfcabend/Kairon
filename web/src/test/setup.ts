import "@testing-library/jest-dom/vitest";

import { afterAll, afterEach, beforeAll } from "vitest";
import { cleanup } from "@testing-library/react";

import { useAuthStore } from "@/features/auth/authStore";

import { server } from "./msw/server";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  useAuthStore.setState({ accessToken: null, user: null });
});

afterAll(() => server.close());
