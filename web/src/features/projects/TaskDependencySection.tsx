import { X } from "lucide-react";
import { useState } from "react";

import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ApiError } from "@/lib/api/client";
import type { ProjectTask } from "@/lib/api/types";

import { useProjectTasks } from "./useProjectTasks";
import { useCreateDependency, useDeleteDependency, useTaskDependencies } from "./useTaskDependencies";

interface Props {
  projectId: string;
  task: ProjectTask;
}

/**
 * The "Depends on" block embedded in {@link TaskFormDialog} (D1): lists this
 * task's current predecessors with a × to remove, plus an "Add" select of
 * every other task in the project. M5's form only creates `FS` edges (D2) —
 * the type/lag picker isn't exposed here.
 */
export function TaskDependencySection({ projectId, task }: Props) {
  const { tasks } = useProjectTasks(projectId);
  const { data: dependencies = [] } = useTaskDependencies(projectId);
  const createDependency = useCreateDependency(projectId);
  const deleteDependency = useDeleteDependency(projectId);
  const [error, setError] = useState<string | null>(null);

  const byId = new Map(tasks.map((t) => [t.id, t]));
  const predecessorEdges = dependencies.filter((d) => d.successorId === task.id);
  const predecessorIds = new Set(predecessorEdges.map((d) => d.predecessorId));
  const candidates = tasks.filter((t) => t.id !== task.id && !predecessorIds.has(t.id));

  const addPredecessor = async (predecessorId: string) => {
    setError(null);
    try {
      await createDependency.mutateAsync({ taskId: task.id, body: { predecessorId } });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't add that dependency.");
    }
  };

  return (
    <div className="space-y-1.5">
      <Label>Depends on</Label>
      {predecessorEdges.length > 0 && (
        <ul className="space-y-1">
          {predecessorEdges.map((edge) => (
            <li
              key={edge.id}
              className="flex items-center justify-between rounded-md border px-2 py-1 text-sm"
            >
              <span className={edge.violatesConstraint ? "text-amber-600" : undefined}>
                {byId.get(edge.predecessorId)?.name ?? "Unknown task"}
                {edge.violatesConstraint && " (starts too early)"}
              </span>
              <button
                type="button"
                aria-label={`Remove dependency on ${byId.get(edge.predecessorId)?.name ?? "task"}`}
                className="text-muted-foreground hover:text-destructive"
                onClick={() => deleteDependency.mutate(edge.id)}
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </li>
          ))}
        </ul>
      )}
      {candidates.length > 0 && (
        <Select value="" onValueChange={addPredecessor}>
          <SelectTrigger aria-label="Add dependency">
            <SelectValue placeholder="Add a predecessor task…" />
          </SelectTrigger>
          <SelectContent>
            {candidates.map((c) => (
              <SelectItem key={c.id} value={c.id}>
                {c.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}
