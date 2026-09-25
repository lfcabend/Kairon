import { FolderKanban, X } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useProjectsByPriority } from "@/features/projects/useProjects";
import { useProjectTasks } from "@/features/projects/useProjectTasks";

export interface ProjectTaskLink {
  taskId: string;
  taskName: string;
}

interface Props {
  value: ProjectTaskLink | null;
  onChange: (value: ProjectTaskLink | null) => void;
}

/**
 * A small "link to a project task" picker for {@link QuickAdd}: a Project
 * select cascading into that project's (non-`DONE`) Task select, mirroring
 * `projects/TaskDependencySection`'s picker shape. Selecting a task closes
 * the popover; the trigger shows an active state and a clear (×) once a
 * link is picked.
 */
export function ProjectTaskPicker({ value, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const [projectId, setProjectId] = useState<string | null>(null);

  const { data: projects = [] } = useProjectsByPriority(open);
  const { tasks } = useProjectTasks(projectId ?? "");
  const candidates = tasks.filter((t) => t.status !== "DONE");

  const pickTask = (taskId: string) => {
    const task = candidates.find((t) => t.id === taskId);
    if (!task) return;
    onChange({ taskId: task.id, taskName: task.name });
    setOpen(false);
    setProjectId(null);
  };

  if (value) {
    return (
      <span className="flex items-center gap-1 rounded-md border bg-muted px-2 py-1.5 text-xs text-muted-foreground">
        <FolderKanban className="h-3.5 w-3.5" aria-hidden />
        {value.taskName}
        <button
          type="button"
          aria-label="Remove project link"
          className="hover:text-destructive"
          onClick={() => onChange(null)}
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </span>
    );
  }

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setProjectId(null);
      }}
    >
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" size="icon" aria-label="Link to a project task">
          <FolderKanban className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-64 space-y-2 p-3" align="end">
        <Select value={projectId ?? ""} onValueChange={setProjectId}>
          <SelectTrigger aria-label="Project">
            <SelectValue placeholder="Choose a project…" />
          </SelectTrigger>
          <SelectContent>
            {projects.map((p) => (
              <SelectItem key={p.id} value={p.id}>
                {p.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {projectId && (
          <Select value="" onValueChange={pickTask}>
            <SelectTrigger aria-label="Task">
              <SelectValue
                placeholder={candidates.length === 0 ? "No open tasks" : "Choose a task…"}
              />
            </SelectTrigger>
            <SelectContent>
              {candidates.map((t) => (
                <SelectItem key={t.id} value={t.id}>
                  {t.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </PopoverContent>
    </Popover>
  );
}
