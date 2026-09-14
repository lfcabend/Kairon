import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { ProjectTask, ProjectTaskStatus } from "@/lib/api/types";

import { TaskDependencySection } from "./TaskDependencySection";
import { useCreateTask, usePatchTask } from "./useProjectTasks";

const NO_PARENT = "none";

const schema = z.object({
  name: z.string().min(1, "Name is required").max(300),
  description: z.string(),
  status: z.string(),
  parentTaskId: z.string(),
  isMilestone: z.boolean(),
  date: z.string(),
  plannedStart: z.string(),
  plannedEnd: z.string(),
  estimateHours: z.string(),
  actualHours: z.string(),
  progressPercent: z.coerce.number().min(0).max(100),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string;
  task?: ProjectTask;
  /** Candidate parent tasks — top-level tasks in the project, excluding this task if it has children. */
  parentCandidates: ProjectTask[];
}

const STATUS_OPTIONS: ProjectTaskStatus[] = ["TODO", "IN_PROGRESS", "BLOCKED", "DONE"];

function toValues(task: ProjectTask | undefined): FormValues {
  return {
    name: task?.name ?? "",
    description: task?.description ?? "",
    status: task?.status ?? "TODO",
    parentTaskId: task?.parentTaskId ?? NO_PARENT,
    isMilestone: task?.isMilestone ?? false,
    date: task?.isMilestone ? (task?.plannedStart ?? "") : "",
    plannedStart: !task?.isMilestone ? (task?.plannedStart ?? "") : "",
    plannedEnd: !task?.isMilestone ? (task?.plannedEnd ?? "") : "",
    estimateHours: task?.estimateHours != null ? String(task.estimateHours) : "",
    actualHours: task?.actualHours != null ? String(task.actualHours) : "",
    progressPercent: task?.progressPercent ?? 0,
  };
}

/** Create/edit task dialog (D5's reparent dropdown, D9's milestone date collapse). */
export function TaskFormDialog({ open, onOpenChange, projectId, task, parentCandidates }: Props) {
  const createTask = useCreateTask(projectId);
  const patchTask = usePatchTask(projectId);

  const { register, handleSubmit, control, reset, watch, formState: { errors, isSubmitting } } =
    useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toValues(task) });

  useEffect(() => {
    if (open) reset(toValues(task));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, task]);

  const isMilestone = watch("isMilestone");

  const onSubmit = handleSubmit(async (values) => {
    const parentTaskId = values.parentTaskId === NO_PARENT ? null : values.parentTaskId;
    const estimateHours = values.estimateHours.trim() ? Number(values.estimateHours) : null;
    const plannedStart = values.isMilestone ? values.date || null : values.plannedStart || null;
    const plannedEnd = values.isMilestone ? values.date || null : values.plannedEnd || null;

    if (task) {
      const actualHours = values.actualHours.trim() ? Number(values.actualHours) : null;
      await patchTask.mutateAsync({
        id: task.id,
        body: {
          name: values.name,
          description: values.description || null,
          status: values.status as ProjectTaskStatus,
          parentTaskId,
          plannedStart,
          plannedEnd,
          estimateHours,
          actualHours,
          progressPercent: values.progressPercent,
          isMilestone: values.isMilestone,
          expectedVersion: task.version,
        },
      });
    } else {
      await createTask.mutateAsync({
        name: values.name,
        description: values.description || null,
        parentTaskId,
        plannedStart,
        plannedEnd,
        estimateHours,
        isMilestone: values.isMilestone,
      });
    }
    onOpenChange(false);
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{task ? "Edit task" : "New task"}</DialogTitle>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="task-name">Name</Label>
            <Input id="task-name" {...register("name")} />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="task-description">Description</Label>
            <textarea
              id="task-description"
              className="min-h-[4rem] w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
              {...register("description")}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            {task && (
              <div className="space-y-1.5">
                <Label>Status</Label>
                <Controller
                  control={control}
                  name="status"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger aria-label="Status">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {STATUS_OPTIONS.map((s) => (
                          <SelectItem key={s} value={s}>
                            {s.replace("_", " ")}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
            )}

            <div className="space-y-1.5">
              <Label>Parent task</Label>
              <Controller
                control={control}
                name="parentTaskId"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Parent task">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_PARENT}>None (top-level)</SelectItem>
                      {parentCandidates
                        .filter((c) => c.id !== task?.id)
                        .map((c) => (
                          <SelectItem key={c.id} value={c.id}>
                            {c.name}
                          </SelectItem>
                        ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </div>

          <Controller
            control={control}
            name="isMilestone"
            render={({ field }) => (
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={field.value}
                  onChange={(e) => field.onChange(e.target.checked)}
                />
                Milestone
              </label>
            )}
          />

          {isMilestone ? (
            <div className="space-y-1.5">
              <Label htmlFor="task-date">Date</Label>
              <Input id="task-date" type="date" {...register("date")} />
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="task-start">Planned start</Label>
                <Input id="task-start" type="date" {...register("plannedStart")} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="task-end">Planned end</Label>
                <Input id="task-end" type="date" {...register("plannedEnd")} />
              </div>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="task-estimate">Estimate (hours)</Label>
              <Input id="task-estimate" type="number" min="0" step="0.5" {...register("estimateHours")} />
            </div>
            {task && (
              <div className="space-y-1.5">
                <Label htmlFor="task-actual">Actual (hours)</Label>
                <Input id="task-actual" type="number" min="0" step="0.5" {...register("actualHours")} />
              </div>
            )}
          </div>

          {task && (
            <div className="space-y-1.5">
              <Label htmlFor="task-progress">Progress (%)</Label>
              <Input id="task-progress" type="number" min="0" max="100" {...register("progressPercent")} />
              {errors.progressPercent && (
                <p className="text-xs text-destructive">{errors.progressPercent.message}</p>
              )}
            </div>
          )}

          {task && <TaskDependencySection projectId={projectId} task={task} />}

          <DialogFooter>
            <Button type="submit" disabled={isSubmitting}>
              {task ? "Save" : "Create task"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
