import { render, screen, waitFor } from "@testing-library/react";
import { expect, test } from "vitest";

import { PingCard } from "@/components/PingCard";
import { server } from "@/test/msw/server";
import { HttpResponse, http } from "msw";

test("renders the ping result once the API responds", async () => {
  server.use(
    http.get("/kairon/api/v1/ping", () => HttpResponse.json({ pong: true, version: "1.2.3" })),
  );

  render(<PingCard />);

  await waitFor(() => {
    expect(screen.getByTestId("ping-status")).toHaveTextContent("pong = true");
  });
  expect(screen.getByTestId("ping-status")).toHaveTextContent("version 1.2.3");
});

test("shows an error when the API cannot be reached", async () => {
  server.use(http.get("/kairon/api/v1/ping", () => HttpResponse.error()));

  render(<PingCard />);

  await waitFor(() => {
    expect(screen.getByTestId("ping-status")).toHaveTextContent("Could not reach the API");
  });
});
