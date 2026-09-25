import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { DialogFooter } from "@/components/ui/dialog";
import type { PlannedTask, SuggestedProjectPlan } from "@/lib/api/types";
import { formatShortDate } from "@/lib/date";

interface Props {
  plan: SuggestedProjectPlan;
  excludedKeys: Set<string>;
  onToggle: (key: string) => void;
  onCreate: () => void;
  onCancel: () => void;
  pending: boolean;
  error: Error | null;
}

/** Excluding a parent cascades to its children (mirrors the backend's accept-time cascade, D6). */
function effectiveExcluded(tasks: PlannedTask[], excludedKeys: Set<string>): Set<string> {
  const result = new Set(excludedKeys);
  for (const t of tasks) {
    if (t.parentKey && result.has(t.parentKey)) result.add(t.key);
  }
  return result;
}

/**
 * Read-only project header + task tree with milestone markers and per-task
 * checkboxes to exclude — no field editing (D5). "Create project" is wired by
 * the caller (`GenerateProjectDialog`) via `onCreate`.
 */
export function ProjectPlanReview({ plan, excludedKeys, onToggle, onCreate, onCancel, pending, error }: Props) {
  const excluded = effectiveExcluded(plan.tasks, excludedKeys);
  const topLevel = plan.tasks.filter((t) => !t.parentKey);
  const childrenByParentKey = new Map<string, PlannedTask[]>();
  for (const t of plan.tasks) {
    if (t.parentKey) childrenByParentKey.set(t.parentKey, [...(childrenByParentKey.get(t.parentKey) ?? []), t]);
  }

  return (
    <div className="space-y-4">
      <div>
        <p className="font-medium">{plan.name}</p>
        {plan.description && <p className="text-sm text-muted-foreground">{plan.description}</p>}
        <p className="mt-1 text-xs text-muted-foreground">
          {plan.startDate && formatShortDate(plan.startDate)}
          {plan.endDate && ` – ${formatShortDate(plan.endDate)}`}
          {plan.size && ` · ${plan.size}`}
        </p>
      </div>

      <ul className="max-h-[50vh] space-y-1 overflow-y-auto">
        {topLevel.map((task) => (
          <li key={task.key}>
            <TaskRow
              task={task}
              checked={!excludedKeys.has(task.key)}
              disabled={false}
              onToggle={() => onToggle(task.key)}
            />
            {(childrenByParentKey.get(task.key) ?? []).map((child) => (
              <div key={child.key} className="ml-6">
                <TaskRow
                  task={child}
                  checked={!excluded.has(child.key)}
                  disabled={excludedKeys.has(task.key)}
                  onToggle={() => onToggle(child.key)}
                />
              </div>
            ))}
          </li>
        ))}
      </ul>

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error.message || "Could not create the project."}
        </p>
      )}

      <DialogFooter>
        <Button variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button disabled={pending} onClick={onCreate}>
          {pending ? "Creating…" : "Create project"}
        </Button>
      </DialogFooter>
    </div>
  );
}

function TaskRow({
  task,
  checked,
  disabled,
  onToggle,
}: {
  task: PlannedTask;
  checked: boolean;
  disabled: boolean;
  onToggle: () => void;
}) {
  return (
    <label className="flex items-start gap-2.5 rounded-md px-1.5 py-1.5 text-sm hover:bg-muted/50">
      <Checkbox className="mt-0.5" checked={checked} disabled={disabled} onCheckedChange={onToggle} />
      <span className="min-w-0 flex-1">
        <span className={disabled ? "text-muted-foreground" : ""}>
          {task.isMilestone && "◆ "}
          {task.name}
        </span>
        {(task.plannedStart || task.plannedEnd) && (
          <span className="ml-1.5 text-xs text-muted-foreground">
            {task.plannedStart && formatShortDate(task.plannedStart)}
            {task.plannedEnd && task.plannedEnd !== task.plannedStart && ` – ${formatShortDate(task.plannedEnd)}`}
          </span>
        )}
      </span>
    </label>
  );
}
