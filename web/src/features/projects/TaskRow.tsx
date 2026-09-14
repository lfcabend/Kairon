import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { ChevronDown, ChevronRight, Diamond, GripVertical, MoreHorizontal } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Progress } from "@/components/ui/progress";
import type { ProjectTask, ProjectTaskStatus } from "@/lib/api/types";
import { cn } from "@/lib/utils";

export const STATUS_LABEL: Record<ProjectTaskStatus, string> = {
  TODO: "To do",
  IN_PROGRESS: "In progress",
  BLOCKED: "Blocked",
  DONE: "Done",
};

const STATUS_DOT: Record<ProjectTaskStatus, string> = {
  TODO: "bg-muted-foreground",
  IN_PROGRESS: "bg-sky-500",
  BLOCKED: "bg-red-500",
  DONE: "bg-emerald-500",
};

interface Props {
  task: ProjectTask;
  draggable: boolean;
  hasChildren?: boolean;
  expanded?: boolean;
  onToggleExpand?: () => void;
  onChangeStatus: (status: ProjectTaskStatus) => void;
  onRename: (name: string) => void;
  onEdit: () => void;
  onDelete: () => void;
}

export function TaskRow({
  task,
  draggable,
  hasChildren,
  expanded,
  onToggleExpand,
  onChangeStatus,
  onRename,
  onEdit,
  onDelete,
}: Props) {
  const sortable = useSortable({ id: task.id, disabled: !draggable });
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(task.name);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => setName(task.name), [task.name]);
  useEffect(() => {
    if (editing) inputRef.current?.select();
  }, [editing]);

  const style = {
    transform: CSS.Transform.toString(sortable.transform),
    transition: sortable.transition,
  };

  const commit = () => {
    setEditing(false);
    const trimmed = name.trim();
    if (trimmed && trimmed !== task.name) onRename(trimmed);
    else setName(task.name);
  };

  return (
    <li
      ref={sortable.setNodeRef}
      style={style}
      data-testid="task-row"
      className={cn(
        "group flex flex-col gap-1.5 rounded-md border px-2 py-1.5",
        sortable.isDragging && "opacity-60",
      )}
    >
      <div className="flex items-center gap-2">
        {draggable ? (
          <button
            type="button"
            aria-label="Drag to reorder"
            className="cursor-grab text-muted-foreground opacity-0 group-hover:opacity-100"
            {...sortable.attributes}
            {...sortable.listeners}
          >
            <GripVertical className="h-4 w-4" />
          </button>
        ) : (
          <span className="w-4" />
        )}

        {hasChildren ? (
          <button type="button" aria-label={expanded ? "Collapse" : "Expand"} onClick={onToggleExpand}>
            {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          </button>
        ) : (
          <span className="w-4" />
        )}

        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label={`Status: ${STATUS_LABEL[task.status]}`}
            className="flex shrink-0 items-center gap-1.5 rounded px-1 py-0.5 text-xs text-muted-foreground hover:bg-muted"
          >
            <span className={cn("inline-block h-2.5 w-2.5 rounded-full", STATUS_DOT[task.status])} />
            {STATUS_LABEL[task.status]}
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            {(Object.keys(STATUS_LABEL) as ProjectTaskStatus[]).map((s) => (
              <DropdownMenuItem key={s} onClick={() => onChangeStatus(s)}>
                {STATUS_LABEL[s]}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>

        {task.isMilestone && <Diamond className="h-3.5 w-3.5 text-amber-500" aria-label="Milestone" />}

        {editing ? (
          <input
            ref={inputRef}
            className="flex-1 rounded border bg-background px-1 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onBlur={commit}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                commit();
              } else if (e.key === "Escape") {
                setName(task.name);
                setEditing(false);
              }
            }}
          />
        ) : (
          <span
            className={cn("flex-1 text-sm", task.status === "DONE" && "text-muted-foreground line-through")}
            onDoubleClick={() => setEditing(true)}
          >
            {task.name}
          </span>
        )}

        {task.estimateHours != null && (
          <span className="text-xs tabular-nums text-muted-foreground">{task.estimateHours}h</span>
        )}

        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label="More actions"
            className="text-muted-foreground opacity-0 group-hover:opacity-100 data-[state=open]:opacity-100"
          >
            <MoreHorizontal className="h-4 w-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={onEdit}>Edit</DropdownMenuItem>
            <DropdownMenuItem onClick={onDelete} className="text-destructive">
              Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {task.progressPercent > 0 && (
        <Progress value={task.progressPercent} className="ml-6 h-1.5" aria-label="Progress" />
      )}
    </li>
  );
}
