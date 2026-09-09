import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { expect, test } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";
import LoginPage from "@/features/auth/LoginPage";
import { renderWithProviders } from "@/test/renderWithProviders";

const renderLogin = () =>
  renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<div>account home</div>} />
    </Routes>,
    { route: "/login" },
  );

test("a successful sign-in stores the session and redirects home", async () => {
  const user = userEvent.setup();
  renderLogin();

  await user.type(screen.getByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "correct horse battery");
  await user.click(screen.getByRole("button", { name: "Sign in" }));

  expect(await screen.findByText("account home")).toBeInTheDocument();
  expect(useAuthStore.getState().accessToken).toBe("access-login");
});

test("a rejected sign-in shows an error and keeps the user on the page", async () => {
  const user = userEvent.setup();
  renderLogin();

  await user.type(screen.getByLabelText("Email"), "ada@example.com");
  await user.type(screen.getByLabelText("Password"), "wrong-password");
  await user.click(screen.getByRole("button", { name: "Sign in" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password.");
  await waitFor(() => expect(useAuthStore.getState().accessToken).toBeNull());
});

test("client-side validation blocks an empty submit", async () => {
  const user = userEvent.setup();
  renderLogin();

  await user.click(screen.getByRole("button", { name: "Sign in" }));

  expect(await screen.findByText("Email is required")).toBeInTheDocument();
});
