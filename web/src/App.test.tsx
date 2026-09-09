import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";

import App from "@/App";

afterEach(() => {
  vi.restoreAllMocks();
});

test("renders the ping result once the API responds", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(
    new Response(JSON.stringify({ pong: true, version: "1.2.3" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );

  render(<App />);

  await waitFor(() => {
    expect(screen.getByTestId("ping-status")).toHaveTextContent("pong = true");
  });
  expect(screen.getByTestId("ping-status")).toHaveTextContent("version 1.2.3");
});

test("shows an error when the API cannot be reached", async () => {
  vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("network down"));

  render(<App />);

  await waitFor(() => {
    expect(screen.getByTestId("ping-status")).toHaveTextContent(
      "Could not reach the API: network down",
    );
  });
});
