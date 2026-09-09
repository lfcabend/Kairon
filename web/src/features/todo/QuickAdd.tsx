import { forwardRef, useState } from "react";

import { Input } from "@/components/ui/input";

interface Props {
  onAdd: (title: string) => void;
  disabled?: boolean;
}

/** Always-visible input; Enter creates and keeps focus. */
export const QuickAdd = forwardRef<HTMLInputElement, Props>(function QuickAdd(
  { onAdd, disabled },
  ref,
) {
  const [value, setValue] = useState("");

  return (
    <Input
      ref={ref}
      value={value}
      disabled={disabled}
      placeholder="Add a task and press Enter"
      aria-label="Add a task"
      onChange={(e) => setValue(e.target.value)}
      onKeyDown={(e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          const title = value.trim();
          if (title) {
            onAdd(title);
            setValue("");
          }
        }
      }}
    />
  );
});
