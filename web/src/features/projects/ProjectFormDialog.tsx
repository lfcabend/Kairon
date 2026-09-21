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
import type { Project, ProjectCategory, ProjectSize, ProjectStatus } from "@/lib/api/types";
import { pickUniqueColor } from "@/lib/color";

import { useCreateProject, usePatchProject } from "./useProjects";

const NO_CATEGORY = "none";
const NO_SIZE = "none";

const schema = z.object({
  categoryId: z.string(),
  name: z.string().min(1, "Name is required").max(200),
  description: z.string(),
  status: z.string(),
  size: z.string(),
  color: z.string().regex(/^#[0-9a-fA-F]{6}$/, "Enter a valid hex color"),
  startDate: z.string(),
  endDate: z.string(),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  project?: Project;
  categories: ProjectCategory[];
  defaultCategoryId?: string;
  /** Other projects' colors, so a new project doesn't default to the same color as one already in use. */
  existingColors?: string[];
}

const STATUS_OPTIONS: ProjectStatus[] = ["PLANNING", "ACTIVE", "ON_HOLD", "DONE", "ARCHIVED"];
const SIZE_OPTIONS: ProjectSize[] = ["XS", "S", "M", "L", "XL"];

/** Create/edit dialog (D18: no priority field — priority only changes by dragging). */
export function ProjectFormDialog({
  open,
  onOpenChange,
  project,
  categories,
  defaultCategoryId,
  existingColors,
}: Props) {
  const createProject = useCreateProject();
  const patchProject = usePatchProject();

  const { register, handleSubmit, control, reset, formState: { errors, isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      categoryId: project?.categoryId ?? defaultCategoryId ?? NO_CATEGORY,
      name: project?.name ?? "",
      description: project?.description ?? "",
      status: project?.status ?? "PLANNING",
      size: project?.size ?? NO_SIZE,
      color: project?.color ?? pickUniqueColor(existingColors ?? []),
      startDate: project?.startDate ?? "",
      endDate: project?.endDate ?? "",
    },
  });

  useEffect(() => {
    if (open) {
      reset({
        categoryId: project?.categoryId ?? defaultCategoryId ?? NO_CATEGORY,
        name: project?.name ?? "",
        description: project?.description ?? "",
        status: project?.status ?? "PLANNING",
        size: project?.size ?? NO_SIZE,
        color: project?.color ?? pickUniqueColor(existingColors ?? []),
        startDate: project?.startDate ?? "",
        endDate: project?.endDate ?? "",
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, project]);

  const onSubmit = handleSubmit(async (values) => {
    const categoryId = values.categoryId === NO_CATEGORY ? null : values.categoryId;
    const size = values.size === NO_SIZE ? null : (values.size as ProjectSize);
    if (project) {
      await patchProject.mutateAsync({
        id: project.id,
        body: {
          categoryId,
          name: values.name,
          description: values.description || null,
          status: values.status as ProjectStatus,
          size,
          color: values.color,
          startDate: values.startDate || null,
          endDate: values.endDate || null,
          actualStart: project.actualStart,
          actualEnd: project.actualEnd,
          expectedVersion: project.version,
        },
      });
    } else {
      await createProject.mutateAsync({
        categoryId,
        name: values.name,
        description: values.description || null,
        size,
        color: values.color,
        startDate: values.startDate || null,
        endDate: values.endDate || null,
      });
    }
    onOpenChange(false);
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{project ? "Edit project" : "New project"}</DialogTitle>
        </DialogHeader>
        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="project-name">Name</Label>
            <Input id="project-name" {...register("name")} />
            {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="project-description">Description</Label>
            <textarea
              id="project-description"
              className="min-h-[4rem] w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
              {...register("description")}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Category</Label>
              <Controller
                control={control}
                name="categoryId"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Category">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_CATEGORY}>Uncategorized</SelectItem>
                      {categories.map((c) => (
                        <SelectItem key={c.id} value={c.id}>
                          {c.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>

            <div className="space-y-1.5">
              <Label>Size</Label>
              <Controller
                control={control}
                name="size"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger aria-label="Size">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NO_SIZE}>Any size</SelectItem>
                      {SIZE_OPTIONS.map((s) => (
                        <SelectItem key={s} value={s}>
                          {s}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>
          </div>

          {project && (
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

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="project-start">Start date</Label>
              <Input id="project-start" type="date" {...register("startDate")} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="project-end">End date</Label>
              <Input id="project-end" type="date" {...register("endDate")} />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="project-color">Color</Label>
            <Controller
              control={control}
              name="color"
              render={({ field }) => (
                <div className="flex items-center gap-2">
                  <input
                    id="project-color"
                    type="color"
                    className="h-10 w-14 rounded border border-input bg-background"
                    value={/^#[0-9a-fA-F]{6}$/.test(field.value) ? field.value : "#6366f1"}
                    onChange={(e) => field.onChange(e.target.value)}
                  />
                  <Input className="flex-1" value={field.value} onChange={field.onChange} />
                </div>
              )}
            />
            {errors.color && <p className="text-xs text-destructive">{errors.color.message}</p>}
          </div>

          <DialogFooter>
            <Button type="submit" disabled={isSubmitting}>
              {project ? "Save" : "Create project"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
