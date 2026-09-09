import "@testing-library/jest-dom/vitest";

import { afterAll, afterEach, beforeAll } from "vitest";
import { cleanup } from "@testing-library/react";

import { useAuthStore } from "@/features/auth/authStore";

import { resetMeResponse, resetTodoStore } from "./msw/handlers";
import { server } from "./msw/server";

// jsdom lacks the Pointer Capture API that Radix/sonner call on pointer events.
for (const method of ["setPointerCapture", "releasePointerCapture", "hasPointerCapture"] as const) {
  if (!(method in Element.prototype)) {
    Object.defineProperty(Element.prototype, method, { value: () => false, writable: true });
  }
}
if (!("scrollIntoView" in Element.prototype)) {
  Object.defineProperty(Element.prototype, "scrollIntoView", { value: () => {}, writable: true });
}

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetTodoStore();
  resetMeResponse();
  useAuthStore.setState({ accessToken: null, user: null });
});

afterAll(() => server.close());
