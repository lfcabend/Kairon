import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, test, vi } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";
import { renderWithProviders } from "@/test/renderWithProviders";

import { ProjectTaskPicker } from "./ProjectTaskPicker";

// Opening the picker's popover exercises Radix Select's popper positioning,
// which hangs under jsdom's zero-size layout (see TaskDependencySection.test.tsx) —
// that flow is covered by the Playwright e2e spec instead. These tests only
// cover the picker's collapsed/linked states, not the cascading selects.

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

test("shows a trigger button when nothing is linked", () => {
  renderWithProviders(<ProjectTaskPicker value={null} onChange={vi.fn()} />);

  expect(screen.getByRole("button", { name: "Link to a project task" })).toBeInTheDocument();
});

test("shows the linked task name and a clear control once a link is picked", async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  renderWithProviders(
    <ProjectTaskPicker value={{ taskId: "t1", taskName: "Design the schema" }} onChange={onChange} />,
  );

  expect(screen.getByText("Design the schema")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Remove project link" }));

  expect(onChange).toHaveBeenCalledWith(null);
});
