import { Link } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import type { Project } from "@/lib/api/types";
import { cn } from "@/lib/utils";

import { useProjectTasks } from "./useProjectTasks";

const STATUS_LABEL: Record<Project["status"], string> = {
  PLANNING: "Planning",
  ACTIVE: "Active",
  ON_HOLD: "On hold",
  DONE: "Done",
  ARCHIVED: "Archived",
};

interface Props {
  project: Project;
  dragHandle?: React.ReactNode;
}

/** List row: color swatch, name, status badge, size badge (D16), date range, task count. No priority indicator (D18). */
export function ProjectCard({ project, dragHandle }: Props) {
  const { data } = useProjectTasks(project.id);
  const taskCount = data?.totalElements ?? 0;

  const dateRange =
    project.startDate || project.endDate
      ? `${project.startDate ?? "…"} – ${project.endDate ?? "…"}`
      : null;

  return (
    <div className="flex items-center gap-3 rounded-md border p-3">
      {dragHandle}
      <span
        className="h-3 w-3 shrink-0 rounded-full"
        style={{ backgroundColor: project.color }}
        aria-hidden
      />
      <Link to={`/projects/${project.id}`} className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate font-medium">{project.name}</span>
          <Badge variant="secondary">{STATUS_LABEL[project.status]}</Badge>
          {project.size && (
            <Badge variant="outline" className={cn("uppercase")}>
              {project.size}
            </Badge>
          )}
        </div>
        <div className="mt-0.5 flex gap-3 text-xs text-muted-foreground">
          {dateRange && <span>{dateRange}</span>}
          <span>
            {taskCount} task{taskCount === 1 ? "" : "s"}
          </span>
        </div>
      </Link>
    </div>
  );
}
