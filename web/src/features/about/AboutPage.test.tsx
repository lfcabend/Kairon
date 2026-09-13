import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { expect, test } from "vitest";

import { server } from "@/test/msw/server";

import AboutPage from "./AboutPage";

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AboutPage />
    </QueryClientProvider>,
  );
}

test("renders the build and deploy facts from /actuator/info", async () => {
  renderPage();

  const info = await screen.findByTestId("about-info");
  await waitFor(() => expect(info).toHaveTextContent("test"));
  expect(info).toHaveTextContent("abc1234");
  expect(info).toHaveTextContent("kairon:abc1234");
});

test("shows an error when the info endpoint cannot be reached", async () => {
  server.use(http.get("/kairon/actuator/info", () => HttpResponse.error()));

  renderPage();

  await waitFor(() => {
    expect(screen.getByRole("alert")).toHaveTextContent("Could not load build info.");
  });
});
