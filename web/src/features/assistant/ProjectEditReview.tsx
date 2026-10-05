import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { DialogFooter } from "@/components/ui/dialog";
import type { ProjectTask, SuggestedProjectEdit, TaskDependency, TaskOperation } from "@/lib/api/types";
import { formatShortDate } from "@/lib/date";

interface Props {
  diff: SuggestedProjectEdit;
  currentTasks: ProjectTask[];
  currentDependencies: TaskDependency[];
  excludedKeys: Set<string>;
  onToggle: (key: string) => void;
  onApply: () => void;
  onCancel: () => void;
  pending: boolean;
  error: Error | null;
}

const taskKey = (i: number) => `task:${i}`;
const dependencyKey = (i: number) => `dependency:${i}`;

/** New tasks (ADD ops) excluded directly, for cascading to anything else that references their key. */
function excludedNewTaskKeys(diff: SuggestedProjectEdit, excludedKeys: Set<string>): Set<string> {
  const result = new Set<string>();
  diff.taskOperations.forEach((op, i) => {
    if (op.op === "ADD" && op.key && excludedKeys.has(taskKey(i))) result.add(op.key);
  });
  return result;
}

/**
 * Read-only diff grouped by change type, with per-change exclude checkboxes
 * (D8) — no field editing. "Apply" is wired by the caller (`EditProjectDialog`)
 * via `onApply`. Excluding a new task cascades (visually) to anything else in
 * the diff that references its key, mirroring the backend's accept-time
 * cascade (M9.5 D1).
 */
export function ProjectEditReview({
  diff,
  currentTasks,
  currentDependencies,
  excludedKeys,
  onToggle,
  onApply,
  onCancel,
  pending,
  error,
}: Props) {
  const excludedNewKeys = excludedNewTaskKeys(diff, excludedKeys);

  const taskById = new Map(currentTasks.map((t) => [t.id, t]));
  const addedNameByKey = new Map(
    diff.taskOperations.filter((op) => op.op === "ADD" && op.key).map((op) => [op.key as string, op.name ?? ""]),
  );
  function refLabel(ref: string | null): string {
    if (!ref) return "top level";
    return taskById.get(ref)?.name ?? addedNameByKey.get(ref) ?? ref;
  }

  function isTaskExcluded(op: TaskOperation, i: number): boolean {
    if (excludedKeys.has(taskKey(i))) return true;
    return Boolean(op.parentRef && excludedNewKeys.has(op.parentRef));
  }

  const hasChanges =
    diff.projectChanges != null ||
    diff.taskOperations.length > 0 ||
    diff.dependencyOperations.length > 0 ||
    diff.reorderOperations.length > 0;

  return (
    <div className="space-y-4">
      {!hasChanges && (
        <p className="text-sm text-muted-foreground">The assistant didn't propose any changes.</p>
      )}

      {diff.projectChanges && (
        <section>
          <h3 className="text-sm font-medium">Project</h3>
          <label className="mt-1 flex items-start gap-2.5 rounded-md px-1.5 py-1.5 text-sm hover:bg-muted/50">
            <Checkbox
              className="mt-0.5"
              checked={!excludedKeys.has("project")}
              onCheckedChange={() => onToggle("project")}
            />
            <span className="min-w-0 flex-1">
              {diff.projectChanges.name && <span>Rename to "{diff.projectChanges.name}"</span>}
              {diff.projectChanges.categoryName && (
                <span className="ml-1.5 inline-block rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  {diff.projectChanges.categoryId
                    ? diff.projectChanges.categoryName
                    : `New category: ${diff.projectChanges.categoryName}`}
                </span>
              )}
            </span>
          </label>
        </section>
      )}

      {diff.taskOperations.length > 0 && (
        <section>
          <h3 className="text-sm font-medium">Tasks</h3>
          <ul className="mt-1 max-h-[40vh] space-y-1 overflow-y-auto">
            {diff.taskOperations.map((op, i) => {
              const excluded = isTaskExcluded(op, i);
              const disabled = Boolean(op.parentRef && excludedNewKeys.has(op.parentRef));
              const label =
                op.op === "ADD"
                  ? op.name ?? ""
                  : op.op === "UPDATE"
                    ? `${taskById.get(op.existingTaskId ?? "")?.name ?? "Task"} → ${op.name}`
                    : taskById.get(op.existingTaskId ?? "")?.name ?? "Task";
              const prefix = op.op === "ADD" ? "+ " : op.op === "REMOVE" ? "– " : "~ ";
              return (
                <li key={taskKey(i)}>
                  <label className="flex items-start gap-2.5 rounded-md px-1.5 py-1.5 text-sm hover:bg-muted/50">
                    <Checkbox
                      className="mt-0.5"
                      checked={!excluded}
                      disabled={disabled}
                      onCheckedChange={() => onToggle(taskKey(i))}
                    />
                    <span className={disabled ? "min-w-0 flex-1 text-muted-foreground" : "min-w-0 flex-1"}>
                      {prefix}
                      {op.isMilestone && "◆ "}
                      {label}
                      {(op.plannedStart || op.plannedEnd) && (
                        <span className="ml-1.5 text-xs text-muted-foreground">
                          {op.plannedStart && formatShortDate(op.plannedStart)}
                          {op.plannedEnd && op.plannedEnd !== op.plannedStart && ` – ${formatShortDate(op.plannedEnd)}`}
                        </span>
                      )}
                    </span>
                  </label>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {diff.dependencyOperations.length > 0 && (
        <section>
          <h3 className="text-sm font-medium">Dependencies</h3>
          <ul className="mt-1 space-y-1">
            {diff.dependencyOperations.map((op, i) => {
              const disabled =
                (op.predecessorRef && excludedNewKeys.has(op.predecessorRef)) ||
                (op.successorRef && excludedNewKeys.has(op.successorRef));
              const label =
                op.op === "ADD"
                  ? `${refLabel(op.predecessorRef)} → ${refLabel(op.successorRef)} (${op.type ?? "FS"})`
                  : (() => {
                      const edge = currentDependencies.find((d) => d.id === op.existingDependencyId);
                      return edge
                        ? `${refLabel(edge.predecessorId)} → ${refLabel(edge.successorId)}`
                        : "an existing dependency";
                    })();
              return (
                <li key={dependencyKey(i)}>
                  <label className="flex items-start gap-2.5 rounded-md px-1.5 py-1.5 text-sm hover:bg-muted/50">
                    <Checkbox
                      className="mt-0.5"
                      checked={!excludedKeys.has(dependencyKey(i)) && !disabled}
                      disabled={Boolean(disabled)}
                      onCheckedChange={() => onToggle(dependencyKey(i))}
                    />
                    <span className={disabled ? "min-w-0 flex-1 text-muted-foreground" : "min-w-0 flex-1"}>
                      {op.op === "ADD" ? "+ " : "– "}
                      {label}
                    </span>
                  </label>
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {diff.reorderOperations.length > 0 && (
        <section>
          <h3 className="text-sm font-medium">Reordering</h3>
          <ul className="mt-1 space-y-1 text-sm text-muted-foreground">
            {diff.reorderOperations.map((op, i) => (
              <li key={i}>
                Reorder under {refLabel(op.parentRef)}: {op.orderedRefs.map((r) => refLabel(r)).join(", ")}
              </li>
            ))}
          </ul>
        </section>
      )}

      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error.message || "Could not apply the changes."}
        </p>
      )}

      <DialogFooter>
        <Button variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button disabled={pending || !hasChanges} onClick={onApply}>
          {pending ? "Applying…" : "Apply changes"}
        </Button>
      </DialogFooter>
    </div>
  );
}
