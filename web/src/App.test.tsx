import { QueryClientProvider, QueryClient } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { expect, test } from "vitest";

import App from "@/App";
import { useAuthStore } from "@/features/auth/authStore";

const renderAt = (route: string) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter
        initialEntries={[route]}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

test("an unauthenticated visit to a protected route lands on the sign-in screen", async () => {
  renderAt("/");
  expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
});

test("an authenticated visit to the account route shows the account screen", async () => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
  renderAt("/");
  expect(await screen.findByRole("heading", { name: "Your account" })).toBeInTheDocument();
});
