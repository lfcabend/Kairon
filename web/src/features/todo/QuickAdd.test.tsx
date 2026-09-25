import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";
import { renderWithProviders } from "@/test/renderWithProviders";

import { QuickAdd } from "./QuickAdd";

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

test("Enter with no project link calls onAdd with just the title and clears the input", async () => {
  const user = userEvent.setup();
  const onAdd = vi.fn();
  renderWithProviders(<QuickAdd onAdd={onAdd} />);

  const input = screen.getByLabelText("Add a task");
  await user.type(input, "Buy milk{Enter}");

  expect(onAdd).toHaveBeenCalledWith("Buy milk", undefined);
  expect(input).toHaveValue("");
});

test("Enter with a blank title does not call onAdd", async () => {
  const user = userEvent.setup();
  const onAdd = vi.fn();
  renderWithProviders(<QuickAdd onAdd={onAdd} />);

  await user.type(screen.getByLabelText("Add a task"), "   {Enter}");

  expect(onAdd).not.toHaveBeenCalled();
});

test("renders the project-link picker trigger alongside the input", () => {
  renderWithProviders(<QuickAdd onAdd={vi.fn()} />);

  expect(screen.getByRole("button", { name: "Link to a project task" })).toBeInTheDocument();
});
