import { Link } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { Project, ProjectSize, ProjectStatus } from "@/lib/api/types";
import { cn } from "@/lib/utils";

import { useChangeProjectSize, useChangeProjectStatus } from "./useProjects";
import { useProjectTasks } from "./useProjectTasks";

const STATUS_LABEL: Record<ProjectStatus, string> = {
  PLANNING: "Planning",
  ACTIVE: "Active",
  ON_HOLD: "On hold",
  DONE: "Done",
  ARCHIVED: "Archived",
};

const STATUS_OPTIONS = Object.keys(STATUS_LABEL) as ProjectStatus[];

const SIZE_OPTIONS: ProjectSize[] = ["XS", "S", "M", "L", "XL"];

interface Props {
  project: Project;
  dragHandle?: React.ReactNode;
}

/**
 * Status badge as a dropdown trigger (same idiom as `TaskRow`'s task-status dot) so
 * the status can be changed without opening the edit dialog. Kept out of the `Link`
 * below rather than nested inside its anchor, so the two interactive elements don't
 * overlap.
 */
function StatusBadge({ project }: { project: Project }) {
  const changeStatus = useChangeProjectStatus();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`Status: ${STATUS_LABEL[project.status]}`}
        onClick={(e) => e.stopPropagation()}
      >
        <Badge variant="secondary" className="cursor-pointer hover:opacity-80">
          {STATUS_LABEL[project.status]}
        </Badge>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        {STATUS_OPTIONS.map((s) => (
          <DropdownMenuItem
            key={s}
            disabled={s === project.status}
            onClick={() => changeStatus.mutate({ project, status: s })}
          >
            {STATUS_LABEL[s]}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * Size badge as a dropdown trigger, same idiom as `StatusBadge` above. When unset,
 * renders a dashed "Set size" placeholder so the size is still reachable without
 * opening the edit dialog; a "Clear" item lets it go back to unset.
 */
function SizeBadge({ project }: { project: Project }) {
  const changeSize = useChangeProjectSize();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`Size: ${project.size ?? "not set"}`}
        onClick={(e) => e.stopPropagation()}
      >
        <Badge
          variant="outline"
          className={cn("uppercase cursor-pointer hover:opacity-80", !project.size && "border-dashed text-muted-foreground")}
        >
          {project.size ?? "Set size"}
        </Badge>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        {SIZE_OPTIONS.map((s) => (
          <DropdownMenuItem
            key={s}
            disabled={s === project.size}
            onClick={() => changeSize.mutate({ project, size: s })}
          >
            {s}
          </DropdownMenuItem>
        ))}
        <DropdownMenuItem disabled={!project.size} onClick={() => changeSize.mutate({ project, size: null })}>
          Clear
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** List row: color swatch, name, status badge (quick-change dropdown), size badge (quick-change dropdown, D16), date range, task count. No priority indicator (D18). */
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
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <Link to={`/projects/${project.id}`} className="truncate font-medium hover:underline">
            {project.name}
          </Link>
          <StatusBadge project={project} />
          <SizeBadge project={project} />
        </div>
        <Link to={`/projects/${project.id}`} className="mt-0.5 flex gap-3 text-xs text-muted-foreground">
          {dateRange && <span>{dateRange}</span>}
          <span>
            {taskCount} task{taskCount === 1 ? "" : "s"}
          </span>
        </Link>
      </div>
    </div>
  );
}
