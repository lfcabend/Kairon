import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { Project, ProjectSize, ProjectStatus } from "@/lib/api/types";

import { CategoryManagerDialog } from "./CategoryManagerDialog";
import { ProjectCard } from "./ProjectCard";
import { ProjectFormDialog } from "./ProjectFormDialog";
import { ProjectPriorityList } from "./ProjectPriorityList";
import { useProjectCategories } from "./useProjectCategories";
import { useProjects } from "./useProjects";

type SortMode = "status" | "recent" | "name" | "priority";

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: "any", label: "Active (not archived)" },
  { value: "PLANNING", label: "Planning" },
  { value: "ACTIVE", label: "Active" },
  { value: "ON_HOLD", label: "On hold" },
  { value: "DONE", label: "Done" },
  { value: "ARCHIVED", label: "Archived" },
];

const SIZE_OPTIONS: { value: string; label: string }[] = [
  { value: "any", label: "Any size" },
  { value: "XS", label: "XS" },
  { value: "S", label: "S" },
  { value: "M", label: "M" },
  { value: "L", label: "L" },
  { value: "XL", label: "XL" },
];

function comparator(mode: SortMode): (a: Project, b: Project) => number {
  switch (mode) {
    case "name":
      return (a, b) => a.name.localeCompare(b.name) || b.updatedAt.localeCompare(a.updatedAt);
    case "recent":
      return (a, b) => b.updatedAt.localeCompare(a.updatedAt);
    case "status":
    default:
      return (a, b) => a.status.localeCompare(b.status) || b.updatedAt.localeCompare(a.updatedAt);
  }
}

export function ProjectListPage() {
  const [sortMode, setSortMode] = useState<SortMode>("status");
  const [status, setStatus] = useState("any");
  const [size, setSize] = useState("any");
  const [formOpen, setFormOpen] = useState(false);
  const [formDefaultCategoryId, setFormDefaultCategoryId] = useState<string | undefined>(undefined);
  const [managingCategories, setManagingCategories] = useState(false);

  const { data: categories = [] } = useProjectCategories();
  const isDefaultFilter = status === "any" && size === "any";
  const statusParam = status === "any" ? undefined : (status as ProjectStatus);
  const sizeParam = size === "any" ? undefined : (size as ProjectSize);

  const { data: page, isLoading } = useProjects(statusParam, undefined, sizeParam, 0);
  const projects = page?.content ?? [];

  const sections = useMemo(() => {
    if (sortMode === "priority") return [];
    const byCategory = new Map<string | null, Project[]>();
    for (const project of projects) {
      const key = project.categoryId;
      if (!byCategory.has(key)) byCategory.set(key, []);
      byCategory.get(key)!.push(project);
    }
    const cmp = comparator(sortMode);
    const ordered = [...categories]
      .sort((a, b) => a.position - b.position)
      .map((c) => ({ id: c.id, name: c.name, projects: [...(byCategory.get(c.id) ?? [])].sort(cmp) }))
      .filter((s) => s.projects.length > 0);
    const uncategorized = [...(byCategory.get(null) ?? [])].sort(cmp);
    if (uncategorized.length > 0) {
      ordered.push({ id: "uncategorized", name: "Uncategorized", projects: uncategorized });
    }
    return ordered;
  }, [projects, categories, sortMode]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold">Projects</h1>
        <div className="flex flex-wrap items-center gap-2">
          <Select value={sortMode} onValueChange={(v) => setSortMode(v as SortMode)}>
            <SelectTrigger aria-label="Sort by" className="w-[170px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="status">Sort by: Status</SelectItem>
              <SelectItem value="recent">Sort by: Recently updated</SelectItem>
              <SelectItem value="name">Sort by: Name</SelectItem>
              <SelectItem value="priority">Sort by: Priority</SelectItem>
            </SelectContent>
          </Select>

          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger aria-label="Status filter" className="w-[170px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STATUS_OPTIONS.map((o) => (
                <SelectItem key={o.value} value={o.value}>
                  {o.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Select value={size} onValueChange={setSize}>
            <SelectTrigger aria-label="Size filter" className="w-[120px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {SIZE_OPTIONS.map((o) => (
                <SelectItem key={o.value} value={o.value}>
                  {o.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Button variant="outline" onClick={() => setManagingCategories(true)}>
            Manage categories
          </Button>
          <Button
            onClick={() => {
              setFormDefaultCategoryId(undefined);
              setFormOpen(true);
            }}
          >
            + New project
          </Button>
        </div>
      </div>

      {sortMode === "priority" ? (
        <ProjectPriorityList draggable={isDefaultFilter} />
      ) : isLoading ? null : sections.length === 0 ? (
        <p className="text-sm text-muted-foreground">No projects yet.</p>
      ) : (
        <div className="space-y-6">
          {sections.map((section) => (
            <div key={section.id} className="space-y-2">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-muted-foreground">{section.name}</h2>
                <button
                  type="button"
                  className="text-xs text-muted-foreground hover:text-foreground"
                  onClick={() => {
                    setFormDefaultCategoryId(section.id === "uncategorized" ? undefined : section.id);
                    setFormOpen(true);
                  }}
                >
                  + New
                </button>
              </div>
              <div className="space-y-1">
                {section.projects.map((project) => (
                  <ProjectCard key={project.id} project={project} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      <ProjectFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        categories={categories}
        defaultCategoryId={formDefaultCategoryId}
      />
      <CategoryManagerDialog open={managingCategories} onOpenChange={setManagingCategories} categories={categories} />
    </div>
  );
}
