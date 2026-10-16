import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, expect, test } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";
import { server } from "@/test/msw/server";

import { DeveloperAccessSection } from "./DeveloperAccessSection";

function renderSection() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <DeveloperAccessSection />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

test("generating a token shows the raw value once, and it disappears on next render", async () => {
  const user = userEvent.setup();
  renderSection();

  await user.type(screen.getByLabelText("Name"), "Claude Code");
  await user.click(screen.getByRole("button", { name: "Generate token" }));

  const revealed = await screen.findByText(/^kairon_pat_/);
  expect(revealed).toBeInTheDocument();

  await user.click(screen.getByRole("button", { name: "Done" }));
  expect(screen.queryByText(/^kairon_pat_/)).not.toBeInTheDocument();

  // The list renders the newly created token (name only — never the raw value again).
  await waitFor(() => expect(screen.getByText("Claude Code")).toBeInTheDocument());
});

test("a failure generating a token shows an inline error and reveals nothing", async () => {
  const user = userEvent.setup();
  server.use(
    http.post("/kairon/api/v1/me/personal-access-tokens", () =>
      HttpResponse.json({ status: 500, detail: "Something went wrong." }, { status: 500 }),
    ),
  );
  renderSection();

  await user.type(screen.getByLabelText("Name"), "Claude Code");
  await user.click(screen.getByRole("button", { name: "Generate token" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("Could not generate a token. Try again.");
  expect(screen.queryByText(/^kairon_pat_/)).not.toBeInTheDocument();
});

test("the empty state renders when there are no tokens yet", async () => {
  renderSection();

  expect(await screen.findByText("No personal access tokens yet.")).toBeInTheDocument();
});

test("revoke removes the row after confirming", async () => {
  const user = userEvent.setup();
  renderSection();

  await user.type(screen.getByLabelText("Name"), "Old laptop");
  await user.click(screen.getByRole("button", { name: "Generate token" }));
  await screen.findByText("Old laptop");

  const row = screen.getByText("Old laptop").closest("li")!;
  await user.click(within(row).getByRole("button", { name: "Revoke" }));
  await user.click(within(row).getByRole("button", { name: "Confirm" }));

  await waitFor(() => expect(screen.queryByText("Old laptop")).not.toBeInTheDocument());
});
