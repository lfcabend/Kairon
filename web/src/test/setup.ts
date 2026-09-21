import "@testing-library/jest-dom/vitest";

// Keep the `log` seam quiet under test; individual tests can spy on `console` if
// they need to assert on a specific message.
import.meta.env.VITE_LOG_LEVEL = "silent";

import { afterAll, afterEach, beforeAll } from "vitest";
import { cleanup } from "@testing-library/react";

import { useAuthStore } from "@/features/auth/authStore";

import {
  resetAssistantStore,
  resetJournalStore,
  resetMeResponse,
  resetProjectsStore,
  resetTodoStore,
} from "./msw/handlers";
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

// jsdom's layout engine is a no-op, so ProseMirror's (Tiptap's) coordinate math —
// used e.g. to scroll the selection into view on focus — needs `getClientRects`/
// `getBoundingClientRect` on both `Range` and `Element` to exist and not throw.
const emptyRect: DOMRect = {
  x: 0,
  y: 0,
  top: 0,
  left: 0,
  right: 0,
  bottom: 0,
  width: 0,
  height: 0,
  toJSON: () => ({}),
};
for (const proto of [Range.prototype, Element.prototype]) {
  Object.defineProperty(proto, "getClientRects", {
    value: () => [emptyRect],
    writable: true,
  });
  Object.defineProperty(proto, "getBoundingClientRect", {
    value: () => emptyRect,
    writable: true,
  });
}

// jsdom has no ResizeObserver; Radix's Popover (via react-popper) needs one to
// exist to position its content at all.
if (!("ResizeObserver" in globalThis)) {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  Object.defineProperty(globalThis, "ResizeObserver", { value: ResizeObserverStub, writable: true });
}

// jsdom doesn't implement SVG geometry APIs; gantt-task-react's drag handling
// calls createSVGPoint() on mount to translate pointer coordinates, and each
// task bar's label measures itself with getBBox() to decide inside/outside placement.
if (!("createSVGPoint" in SVGSVGElement.prototype)) {
  Object.defineProperty(SVGSVGElement.prototype, "createSVGPoint", {
    value: () => ({
      x: 0,
      y: 0,
      matrixTransform() {
        return this;
      },
    }),
    writable: true,
  });
}
if (!("getBBox" in SVGElement.prototype)) {
  Object.defineProperty(SVGElement.prototype, "getBBox", {
    value: () => ({ x: 0, y: 0, width: 0, height: 0 }),
    writable: true,
  });
}

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetTodoStore();
  resetJournalStore();
  resetProjectsStore();
  resetAssistantStore();
  resetMeResponse();
  useAuthStore.setState({ accessToken: null, user: null });
});

afterAll(() => server.close());
