import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { SuggestedProjectPlan } from "@/lib/api/types";

import { ProjectPlanReview } from "./ProjectPlanReview";
import { useAcceptProjectPlan, useDismissProjectPlan, useGenerateProjectPlan } from "./useAssistant";

const schema = z.object({
  description: z.string().min(1, "Description is required"),
  startDate: z.string().min(1, "Start date is required"),
  targetDeadline: z.string(),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Two-step dialog (M8.5 §6.1): a free-text description + dates form, then
 * `ProjectPlanReview` in the same dialog once a plan comes back. "Create
 * project" lands on the new project's normal detail page (D5) — no inline
 * editing here, adjustments happen there afterward.
 */
export function GenerateProjectDialog({ open, onOpenChange }: Props) {
  const navigate = useNavigate();
  const [plan, setPlan] = useState<SuggestedProjectPlan | null>(null);
  const [excludedKeys, setExcludedKeys] = useState<Set<string>>(new Set());
  const generate = useGenerateProjectPlan();
  const accept = useAcceptProjectPlan();
  const dismiss = useDismissProjectPlan();

  const { register, handleSubmit, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { description: "", startDate: "", targetDeadline: "" },
  });

  const handleOpenChange = (next: boolean) => {
    onOpenChange(next);
    if (!next) {
      generate.reset();
      accept.reset();
      setPlan(null);
      setExcludedKeys(new Set());
      reset();
    }
  };

  const onSubmit = handleSubmit((values) => {
    generate.mutate(
      {
        description: values.description,
        startDate: values.startDate,
        targetDeadline: values.targetDeadline || null,
      },
      { onSuccess: (run) => setPlan(run.suggestedProject ?? null) },
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

  const handleCreate = () => {
    if (!plan) return;
    accept.mutate(
      { id: plan.id, excludedTaskKeys: [...excludedKeys] },
      {
        onSuccess: (created) => {
          handleOpenChange(false);
          navigate(`/projects/${created.id}`);
        },
      },
    );
  };

  const handleCancelReview = () => {
    if (plan) dismiss.mutate(plan.id);
    handleOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>New project from description</DialogTitle>
          <DialogDescription>
            {plan === null
              ? "Describe the project — Kairon sends this description and your dates to Anthropic to propose a task tree."
              : "Review the proposed plan. Uncheck anything you don't want; you can adjust the rest after creating it."}
          </DialogDescription>
        </DialogHeader>

        {plan === null ? (
          <form className="space-y-4" onSubmit={onSubmit} noValidate>
            <div className="space-y-1.5">
              <Label htmlFor="plan-description">Description</Label>
              <textarea
                id="plan-description"
                className="min-h-[6rem] w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
                {...register("description")}
              />
              {errors.description && (
                <p className="text-xs text-destructive">{errors.description.message}</p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="plan-start">Start date</Label>
                <Input id="plan-start" type="date" {...register("startDate")} />
                {errors.startDate && (
                  <p className="text-xs text-destructive">{errors.startDate.message}</p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="plan-deadline">Target deadline (optional)</Label>
                <Input id="plan-deadline" type="date" {...register("targetDeadline")} />
              </div>
            </div>

            {generate.isError && (
              <p className="text-sm text-destructive" role="alert">
                {generate.error.message || "Could not generate a plan."}
              </p>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={generate.isPending}>
                {generate.isPending ? "Thinking…" : "Generate plan"}
              </Button>
            </DialogFooter>
          </form>
        ) : (
          <ProjectPlanReview
            plan={plan}
            excludedKeys={excludedKeys}
            onToggle={handleToggle}
            onCreate={handleCreate}
            onCancel={handleCancelReview}
            pending={accept.isPending}
            error={accept.error}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}
