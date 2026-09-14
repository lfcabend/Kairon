import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Diamond, Pencil } from "lucide-react";

import { Progress } from "@/components/ui/progress";
import type { ProjectTask } from "@/lib/api/types";
import { cn } from "@/lib/utils";

interface Props {
  task: ProjectTask;
  parentName?: string;
}

/** Board card: name, "in <parent>" chip if a subtask (D11), estimate, progress. */
export function TaskCard({ task, parentName, onEdit }: Props & { onEdit: () => void }) {
  const sortable = useSortable({ id: task.id });
  const style = {
    transform: CSS.Transform.toString(sortable.transform),
    transition: sortable.transition,
  };

  return (
    <div
      ref={sortable.setNodeRef}
      style={style}
      {...sortable.attributes}
      {...sortable.listeners}
      data-testid="task-card"
      className={cn(
        "group relative cursor-grab space-y-1.5 rounded-md border bg-background p-2 text-sm shadow-sm",
        sortable.isDragging && "opacity-40",
      )}
    >
      <button
        type="button"
        aria-label="Edit task"
        className="absolute right-1.5 top-1.5 text-muted-foreground opacity-0 hover:text-foreground group-hover:opacity-100"
        // Stops dnd-kit's sortable listeners (attached above, on this same element) from
        // treating the click as a drag activation — without this the button never fires.
        onPointerDown={(e) => e.stopPropagation()}
        onClick={(e) => {
          e.stopPropagation();
          onEdit();
        }}
      >
        <Pencil className="h-3.5 w-3.5" />
      </button>
      <CardBody task={task} parentName={parentName} />
    </div>
  );
}

/**
 * Static clone rendered inside the board's `DragOverlay`. dnd-kit portals the overlay to
 * `document.body`, so it floats above the board's `overflow-x-auto` column grid and tracks the
 * pointer directly, instead of the real card's transform getting clipped/misplaced by that
 * ancestor overflow as it crosses into another column.
 */
export function TaskCardOverlay({ task, parentName }: Props) {
  return (
    <div
      data-testid="task-card-overlay"
      className="cursor-grabbing space-y-1.5 rounded-md border bg-background p-2 text-sm shadow-lg ring-2 ring-ring"
    >
      <CardBody task={task} parentName={parentName} />
    </div>
  );
}

function CardBody({ task, parentName }: Props) {
  return (
    <>
      <div className="flex items-center gap-1.5 pr-5">
        {task.isMilestone && <Diamond className="h-3 w-3 shrink-0 text-amber-500" aria-label="Milestone" />}
        <span className="flex-1">{task.name}</span>
      </div>
      {parentName && (
        <span className="inline-block rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">
          in {parentName}
        </span>
      )}
      <div className="flex items-center gap-2">
        {task.estimateHours != null && (
          <span className="text-xs tabular-nums text-muted-foreground">{task.estimateHours}h</span>
        )}
        {task.progressPercent > 0 && <Progress value={task.progressPercent} className="h-1.5 flex-1" />}
      </div>
    </>
  );
}
