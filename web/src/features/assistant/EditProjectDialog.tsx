import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import type { SuggestedProjectEdit } from "@/lib/api/types";

import { useProjectTasks } from "../projects/useProjectTasks";
import { useTaskDependencies } from "../projects/useTaskDependencies";
import { ProjectEditReview } from "./ProjectEditReview";
import { useAcceptProjectEdit, useDismissProjectEdit, useGenerateProjectEdit } from "./useAssistant";

const schema = z.object({
  description: z.string().min(1, "Description is required"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string;
}

/**
 * Two-step dialog, mirroring `GenerateProjectDialog`'s own shape (M8.5 §6.1)
 * but editing an existing project instead of creating one: a free-text
 * description of the desired change, then `ProjectEditReview` in the same
 * dialog once a diff comes back. "Apply changes" applies the accepted subset
 * in one action and closes — adjustments to anything excluded still happen
 * through the normal task dialog/Gantt drag afterward.
 */
export function EditProjectDialog({ open, onOpenChange, projectId }: Props) {
  const [diff, setDiff] = useState<SuggestedProjectEdit | null>(null);
  const [excludedKeys, setExcludedKeys] = useState<Set<string>>(new Set());
  const { tasks } = useProjectTasks(projectId);
  const { data: dependencies = [] } = useTaskDependencies(projectId);
  const generate = useGenerateProjectEdit();
  const accept = useAcceptProjectEdit(projectId);
  const dismiss = useDismissProjectEdit();

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { description: "" },
  });

  const handleOpenChange = (next: boolean) => {
    onOpenChange(next);
    if (!next) {
      generate.reset();
      accept.reset();
      setDiff(null);
      setExcludedKeys(new Set());
      reset();
    }
  };

  const onSubmit = handleSubmit((values) => {
    generate.mutate(
      { projectId, description: values.description },
      { onSuccess: (run) => setDiff(run.suggestedProjectEdit ?? null) },
    );
  });

  const handleToggle = (key: string) => {
    setExcludedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const handleApply = () => {
    if (!diff) return;
    accept.mutate(
      { id: diff.id, excludedOperationKeys: [...excludedKeys] },
      { onSuccess: () => handleOpenChange(false) },
    );
  };

  const handleCancelReview = () => {
    if (diff) dismiss.mutate(diff.id);
    handleOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Edit project with AI</DialogTitle>
          <DialogDescription>
            {diff === null
              ? "Describe the change — Kairon sends this project's current tasks, dependencies, "
                + "and your description to Anthropic to propose a diff."
              : "Review the proposed changes. Uncheck anything you don't want; the rest apply in one action."}
          </DialogDescription>
        </DialogHeader>

        {diff === null ? (
          <form className="space-y-4" onSubmit={onSubmit} noValidate>
            <div className="space-y-1.5">
              <Label htmlFor="edit-description">Describe the change</Label>
              <textarea
                id="edit-description"
                className="min-h-[6rem] w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
                {...register("description")}
              />
              {errors.description && (
                <p className="text-xs text-destructive">{errors.description.message}</p>
              )}
            </div>

            {generate.isError && (
              <p className="text-sm text-destructive" role="alert">
                {generate.error.message || "Could not generate a diff."}
              </p>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={generate.isPending}>
                {generate.isPending ? "Thinking…" : "Generate changes"}
              </Button>
            </DialogFooter>
          </form>
        ) : (
          <ProjectEditReview
            diff={diff}
            currentTasks={tasks}
            currentDependencies={dependencies}
            excludedKeys={excludedKeys}
            onToggle={handleToggle}
            onApply={handleApply}
            onCancel={handleCancelReview}
            pending={accept.isPending}
            error={accept.error}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
