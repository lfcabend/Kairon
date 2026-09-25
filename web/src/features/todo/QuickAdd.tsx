import { forwardRef, useState } from "react";

import { Input } from "@/components/ui/input";

import { ProjectTaskPicker, type ProjectTaskLink } from "./ProjectTaskPicker";

interface Props {
  onAdd: (title: string, sourceProjectTaskId?: string) => void;
  disabled?: boolean;
}

/** Always-visible input; Enter creates and keeps focus. */
export const QuickAdd = forwardRef<HTMLInputElement, Props>(function QuickAdd(
  { onAdd, disabled },
  ref,
) {
  const [value, setValue] = useState("");
  const [link, setLink] = useState<ProjectTaskLink | null>(null);

  const pickLink = (next: ProjectTaskLink | null) => {
    setLink(next);
    if (next && !value.trim()) {
      setValue(next.taskName);
    }
  };

  const submit = () => {
    const title = value.trim();
    if (!title) return;
    onAdd(title, link?.taskId);
    setValue("");
    setLink(null);
  };

  return (
    <div className="flex items-center gap-2">
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
            submit();
          }
        }}
      />
      <ProjectTaskPicker value={link} onChange={pickLink} />
    </div>
  );
});
