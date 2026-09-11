import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";

import type { JournalEntry } from "@/lib/api/types";

import { EntryEditor } from "./EntryEditor";

function entry(overrides: Partial<JournalEntry> = {}): JournalEntry {
  return {
    id: "e1",
    day: "2026-09-09",
    position: 100,
    title: null,
    content: "Hello world",
    mood: null,
    createdAt: "2026-09-09T08:00:00Z",
    updatedAt: "2026-09-09T08:00:00Z",
    version: 0,
    ...overrides,
  };
}

test("content round-trips through the editor on blur", async () => {
  const onSave = vi.fn();
  render(<EntryEditor entry={entry({ content: "Hello world" })} onSave={onSave} />);

  const editorEl = (await screen.findByText("Hello world")).closest('[contenteditable="true"]')!;
  fireEvent.blur(editorEl);

  await waitFor(() => expect(onSave).toHaveBeenCalled());
  expect(onSave.mock.calls[0][0].content).toBe("Hello world");
});

test("the bold toggle reflects active state after clicking", async () => {
  const user = userEvent.setup();
  render(<EntryEditor entry={entry()} onSave={vi.fn()} />);

  const boldButton = screen.getByRole("button", { name: "Bold" });
  expect(boldButton).toHaveAttribute("aria-pressed", "false");

  await user.click(boldButton);
  await waitFor(() => expect(boldButton).toHaveAttribute("aria-pressed", "true"));

  await user.click(boldButton);
  await waitFor(() => expect(boldButton).toHaveAttribute("aria-pressed", "false"));
});

test("mood picker sets and clears the mood, saving on each click", async () => {
  const user = userEvent.setup();
  const onSave = vi.fn();
  render(<EntryEditor entry={entry()} onSave={onSave} />);

  await user.click(screen.getByRole("button", { name: "Mood 3" }));
  expect(onSave).toHaveBeenLastCalledWith(expect.objectContaining({ mood: 3 }));

  await user.click(screen.getByRole("button", { name: "Mood 3" }));
  expect(onSave).toHaveBeenLastCalledWith(expect.objectContaining({ mood: null }));
});

test("editing the title saves on blur", async () => {
  const user = userEvent.setup();
  const onSave = vi.fn();
  render(<EntryEditor entry={entry()} onSave={onSave} />);

  const titleInput = screen.getByLabelText("Entry title");
  await user.type(titleInput, "Morning plan");
  await user.tab();

  await waitFor(() =>
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ title: "Morning plan" })),
  );
});
