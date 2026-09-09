import { screen } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { expect, test } from "vitest";

import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useAuthStore } from "@/features/auth/authStore";
import { renderWithProviders } from "@/test/renderWithProviders";

const tree = (
  <Routes>
    <Route path="/login" element={<div>sign in screen</div>} />
    <Route element={<ProtectedRoute />}>
      <Route path="/" element={<div>protected content</div>} />
    </Route>
  </Routes>
);

test("redirects to /login when there is no session", () => {
  renderWithProviders(tree, { route: "/" });
  expect(screen.getByText("sign in screen")).toBeInTheDocument();
});

test("renders the protected content when a token is present", () => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "a@b.c", displayName: "A", timezone: "UTC" },
  });
  renderWithProviders(tree, { route: "/" });
  expect(screen.getByText("protected content")).toBeInTheDocument();
});
