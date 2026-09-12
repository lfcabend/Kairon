import { useState } from "react";

import { Input } from "@/components/ui/input";

interface Props {
  onAdd: (name: string) => void;
  disabled?: boolean;
}

/** Always-visible title-only input; Enter creates and refocuses (D23) — mirrors `todo/QuickAdd.tsx`. */
export function TaskQuickAdd({ onAdd, disabled }: Props) {
  const [value, setValue] = useState("");

  return (
    <Input
      value={value}
      disabled={disabled}
      placeholder="Add a task and press Enter"
      aria-label="Add a task"
      onChange={(e) => setValue(e.target.value)}
      onKeyDown={(e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          const name = value.trim();
          if (name) {
            onAdd(name);
            setValue("");
          }
        }
      }}
    />
  );
}
