import { useQuery } from "@tanstack/react-query";
import { Gantt, ViewMode, type Task as GanttTask } from "gantt-task-react";
import "gantt-task-react/dist/index.css";
import { useMemo, useState } from "react";

import { authApi } from "@/lib/api/auth";
import type { Me, ProjectTask, ProjectTaskStatus } from "@/lib/api/types";
import { todayInZone } from "@/lib/date";

import { STATUS_LABEL } from "./TaskRow";
import { TaskFormDialog } from "./TaskFormDialog";
import { useProjectTasks, usePatchTask } from "./useProjectTasks";
import { useTaskDependencies } from "./useTaskDependencies";

interface Props {
  projectId: string;
}

/** Same status semantics as `TaskRow`'s `STATUS_DOT`, as hex — the library's bar styling needs real colors, not Tailwind classes. */
const STATUS_COLORS: Record<ProjectTaskStatus, { backgroundColor: string; backgroundSelectedColor: string }> = {
  TODO: { backgroundColor: "#a1a1aa", backgroundSelectedColor: "#71717a" },
  IN_PROGRESS: { backgroundColor: "#38bdf8", backgroundSelectedColor: "#0ea5e9" },
  BLOCKED: { backgroundColor: "#f87171", backgroundSelectedColor: "#ef4444" },
  DONE: { backgroundColor: "#34d399", backgroundSelectedColor: "#10b981" },
};

const VIOLATION_COLORS = { backgroundColor: "#f59e0b", backgroundSelectedColor: "#d97706" };

function toLocalDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function patchBodyFor(task: ProjectTask, start: string, end: string) {
  return {
    name: task.name,
    description: task.description,
    status: task.status,
    parentTaskId: task.parentTaskId,
    plannedStart: start,
    plannedEnd: end,
    estimateHours: task.estimateHours,
    actualHours: task.actualHours,
    progressPercent: task.progressPercent,
    isMilestone: task.isMilestone,
    expectedVersion: task.version,
  };
}

/** Replaces the library's default hover tooltip so it shows the task's real name/status, not the indented, warning-suffixed display name. */
function makeTooltipContent(byId: Map<string, ProjectTask>) {
  return function TooltipContent({ task }: { task: GanttTask }) {
    const real = byId.get(task.id);
    if (!real) return null;
    return (
      <div className="space-y-1 rounded-md border bg-popover p-2 text-sm text-popover-foreground shadow-md">
        <p className="font-medium">{real.name}</p>
        <p className="text-xs text-muted-foreground">{STATUS_LABEL[real.status]}</p>
        <p className="text-xs text-muted-foreground">
          {real.plannedStart} — {real.plannedEnd}
        </p>
        <p className="text-xs text-muted-foreground">{real.progressPercent}% complete</p>
      </div>
    );
  };
}

/**
 * The Gantt tab (D15): bars, milestone diamonds, dependency arrows, progress
 * fill, and a soft FS-violation warning list — all rendered from the same
 * `project_task` data the Tree/Board tabs show (D9, no computed schedule).
 * Bars are colored by status (TODO/IN_PROGRESS/BLOCKED/DONE), overridden by
 * an amber warning color when the task has a violated incoming dependency.
 * Double-clicking a bar opens `TaskFormDialog` for that task — distinct from
 * a single drag, which reschedules it via `onDateChange` below.
 * `gantt-task-react`'s today-shading always uses the browser's system clock
 * (no prop to override it, discovered during the D12 library spike) — we use
 * `todayInZone` only to center the initial `viewDate` on the user's today.
 */
export function GanttView({ projectId }: Props) {
  const { topLevel, childrenByParentId } = useProjectTasks(projectId);
  const { data: dependencies = [] } = useTaskDependencies(projectId);
  const patchTask = usePatchTask(projectId);
  const meQuery = useQuery<Me>({ queryKey: ["me"], queryFn: authApi.me });
  const [editing, setEditing] = useState<ProjectTask | undefined>(undefined);

  // D11: top-level tasks in position order, each immediately followed by its own subtasks.
  const ordered = useMemo(() => {
    const rows: ProjectTask[] = [];
    for (const top of topLevel) {
      rows.push(top);
      for (const child of childrenByParentId.get(top.id) ?? []) rows.push(child);
    }
    return rows;
  }, [topLevel, childrenByParentId]);

  const byId = useMemo(() => new Map(ordered.map((t) => [t.id, t])), [ordered]);

  // D10: a task without both dates gets no bar and shows in the panel below instead.
  const scheduled = ordered.filter((t) => t.plannedStart && t.plannedEnd);
  const unscheduled = ordered.filter((t) => !t.plannedStart || !t.plannedEnd);
  const scheduledIds = useMemo(() => new Set(scheduled.map((t) => t.id)), [scheduled]);

  const violatedPredecessorsBySuccessor = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const edge of dependencies) {
      if (!edge.violatesConstraint) continue;
      map.set(edge.successorId, [...(map.get(edge.successorId) ?? []), edge.predecessorId]);
    }
    return map;
  }, [dependencies]);

  const ganttTasks: GanttTask[] = scheduled.map((task) => {
    const violated = violatedPredecessorsBySuccessor.get(task.id) ?? [];
    const indent = task.parentTaskId != null ? "    " : "";
    return {
      id: task.id,
      name: `${indent}${task.name}${violated.length > 0 ? " ⚠" : ""}`,
      start: toLocalDate(task.plannedStart!),
      end: toLocalDate(task.plannedEnd!),
      type: task.isMilestone ? "milestone" : "task",
      progress: task.progressPercent,
      dependencies: dependencies
        .filter((d) => d.successorId === task.id && scheduledIds.has(d.predecessorId))
        .map((d) => d.predecessorId),
      styles: violated.length > 0 ? VIOLATION_COLORS : STATUS_COLORS[task.status],
    };
  });

  const violations = dependencies.filter((d) => d.violatesConstraint);
  const parentCandidates = topLevel.filter((t) => (childrenByParentId.get(t.id) ?? []).length === 0);
  const TooltipContent = useMemo(() => makeTooltipContent(byId), [byId]);

  if (ordered.length === 0) {
    return <p className="text-sm text-muted-foreground">No tasks yet.</p>;
  }

  return (
    <div className="space-y-4">
      {ganttTasks.length > 0 ? (
        <div className="overflow-x-auto rounded-md border">
          <Gantt
            tasks={ganttTasks}
            viewMode={ViewMode.Week}
            viewDate={meQuery.data ? toLocalDate(todayInZone(meQuery.data.timezone)) : undefined}
            TooltipContent={TooltipContent}
            onDoubleClick={(task) => {
              const real = byId.get(task.id);
              if (real) setEditing(real);
            }}
            onDateChange={(changed) => {
              const task = byId.get(changed.id);
              if (!task) return;
              const start = toIsoDate(changed.start);
              const end = changed.type === "milestone" ? start : toIsoDate(changed.end);
              patchTask.mutate({ id: task.id, body: patchBodyFor(task, start, end) });
            }}
          />
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">No scheduled tasks yet.</p>
      )}

      {violations.length > 0 && (
        <div className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
          <p className="font-medium">Schedule warnings</p>
          <ul className="mt-1 list-disc pl-5">
            {violations.map((edge) => (
              <li key={edge.id}>
                "{byId.get(edge.successorId)?.name ?? "Task"}" starts before "
                {byId.get(edge.predecessorId)?.name ?? "its predecessor"}" finishes.
              </li>
            ))}
          </ul>
        </div>
      )}

      {unscheduled.length > 0 && (
        <div>
          <p className="mb-1.5 text-sm font-medium">Unscheduled</p>
          <ul className="space-y-1">
            {unscheduled.map((task) => (
              <li
                key={task.id}
                className="flex items-center justify-between rounded-md border px-2 py-1.5 text-sm"
              >
                <span>{task.name}</span>
                <button
                  type="button"
                  className="text-xs text-primary underline"
                  onClick={() => setEditing(task)}
                >
                  Set dates
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {editing && (
        <TaskFormDialog
          open={Boolean(editing)}
          onOpenChange={(open) => !open && setEditing(undefined)}
          projectId={projectId}
          task={editing}
          parentCandidates={parentCandidates}
        />
      )}
    </div>
  );
}
