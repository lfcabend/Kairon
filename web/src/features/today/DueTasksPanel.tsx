import { Diamond } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { ProjectTask } from "@/lib/api/types";
import { formatShortDate } from "@/lib/date";
import { cn } from "@/lib/utils";

import { useTodos } from "../todo/hooks";
import { usePromoteTask, useToday } from "./useToday";

/**
 * Due/overdue project tasks with one-click "add to today" (D9): the button
 * becomes "Added" once a todo linked to that task (`sourceProjectTaskId`)
 * already shows up in the `useTodos(date)` cache this panel shares with
 * `TodayTasks`.
 */
export function DueTasksPanel({ date }: { date: string }) {
  const todayQuery = useToday(date);
  const todosQuery = useTodos(date);
  const promote = usePromoteTask(date);

  const dueTasks = todayQuery.data?.dueProjectTasks ?? [];
  const promotedTaskIds = new Set(
    (todosQuery.data ?? []).map((t) => t.sourceProjectTaskId).filter((id): id is string => !!id),
  );

  if (todayQuery.isLoading) {
    return (
      <ul className="space-y-1.5" aria-hidden>
        {[0, 1].map((i) => (
          <li key={i} className="h-10 animate-pulse rounded-md bg-muted" />
        ))}
      </ul>
    );
  }

  if (dueTasks.length === 0) {
    return <p className="text-sm text-muted-foreground">Nothing due or overdue.</p>;
  }

  return (
    <ul className="space-y-1.5">
      {dueTasks.map((task) => (
        <DueTaskRow
          key={task.id}
          task={task}
          date={date}
          added={promotedTaskIds.has(task.id)}
          pending={promote.isPending && promote.variables === task.id}
          error={promote.isError && promote.variables === task.id ? promote.error : null}
          onAdd={() => promote.mutate(task.id)}
        />
      ))}
    </ul>
  );
}

function DueTaskRow({
  task,
  date,
  added,
  pending,
  error,
  onAdd,
}: {
  task: ProjectTask;
  date: string;
  added: boolean;
  pending: boolean;
  error: Error | null;
  onAdd: () => void;
}) {
  const overdue = !!task.plannedEnd && task.plannedEnd < date && task.status !== "DONE";

  return (
    <li className="rounded-md border px-2.5 py-1.5 text-sm">
      <div className="flex items-center gap-2">
        <span
          className="h-2 w-2 shrink-0 rounded-full"
          style={{ backgroundColor: task.projectColor ?? undefined }}
          aria-hidden
        />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            {task.isMilestone && <Diamond className="h-3 w-3 shrink-0 text-amber-500" aria-label="Milestone" />}
            <span className="truncate">{task.name}</span>
          </div>
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <span>{task.projectName}</span>
            {task.plannedEnd && (
              <span className={cn(overdue && "font-medium text-destructive")}>
                · {overdue ? "Overdue" : "Due"} {formatShortDate(task.plannedEnd)}
              </span>
            )}
          </div>
        </div>
        <Button size="sm" variant={added ? "secondary" : "default"} disabled={added || pending} onClick={onAdd}>
          {added ? "Added" : pending ? "Adding…" : "Add to today"}
        </Button>
      </div>
      {error && (
        <p className="mt-1 text-xs text-destructive" role="alert">
          {error instanceof Error ? error.message : "Could not add this task to today."}
        </p>
      )}
    </li>
  );
}
