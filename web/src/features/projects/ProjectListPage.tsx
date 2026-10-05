import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { restrictToVerticalAxis } from "@dnd-kit/modifiers";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical } from "lucide-react";
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { Project, ProjectSize, ProjectStatus } from "@/lib/api/types";
import { cn } from "@/lib/utils";

import { GenerateProjectDialog } from "../assistant/GenerateProjectDialog";
import { CategoryManagerDialog } from "./CategoryManagerDialog";
import { ProjectCard } from "./ProjectCard";
import { ProjectFormDialog } from "./ProjectFormDialog";
import { ProjectPriorityList } from "./ProjectPriorityList";
import { useProjectCategories } from "./useProjectCategories";
import { useMoveProjectCategory, useProjects, useReorderProjectsInCategory } from "./useProjects";

type SortMode = "status" | "recent" | "name" | "priority" | "custom";

type Section = { id: string; name: string; projects: Project[] };

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
    case "custom":
      return (a, b) => a.categoryRank - b.categoryRank;
    case "status":
    default:
      return (a, b) => a.status.localeCompare(b.status) || b.updatedAt.localeCompare(a.updatedAt);
  }
}

/** A category section (or "Uncategorized") as a drop target; highlights while a card is dragged over it. */
function CategorySection({
  id,
  children,
}: {
  id: string;
  children: React.ReactNode;
}) {
  const { setNodeRef, isOver } = useDroppable({ id });
  return (
    <div
      ref={setNodeRef}
      className={cn(
        "space-y-2 rounded-md p-2 ring-2 ring-transparent transition-colors",
        isOver && "bg-primary/10 ring-primary",
      )}
    >
      {children}
    </div>
  );
}

function DraggableProjectCard({ project }: { project: Project }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: project.id });
  return (
    <div ref={setNodeRef} className={isDragging ? "opacity-40" : undefined}>
      <ProjectCard
        project={project}
        dragHandle={
          <button
            type="button"
            aria-label="Drag to move between categories"
            className="cursor-grab text-muted-foreground"
            {...attributes}
            {...listeners}
          >
            <GripVertical className="h-4 w-4" />
          </button>
        }
      />
    </div>
  );
}

/**
 * "Sort by: Custom order" — unlike the Status/Recent/Name sections above, dragging
 * here never moves a project to a different category (that's D19/D21's established
 * territory via the other sort modes); it only reorders within one category's own
 * `categoryRank` bucket, so each section gets its own independent sortable list.
 */
function CustomOrderSections({
  sections,
  draggable,
  onAddClick,
}: {
  sections: Section[];
  draggable: boolean;
  onAddClick: (categoryId: string | undefined) => void;
}) {
  return (
    <div className="space-y-6">
      {!draggable && (
        <p className="text-xs text-muted-foreground">Clear filters to reorder within a category.</p>
      )}
      {sections.map((section) => (
        <CustomOrderCategorySection
          key={section.id}
          section={section}
          draggable={draggable}
          onAddClick={onAddClick}
        />
      ))}
    </div>
  );
}

function CustomOrderCategorySection({
  section,
  draggable,
  onAddClick,
}: {
  section: Section;
  draggable: boolean;
  onAddClick: (categoryId: string | undefined) => void;
}) {
  const reorder = useReorderProjectsInCategory();
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const ids = section.projects.map((p) => p.id);
    const from = ids.indexOf(String(active.id));
    const to = ids.indexOf(String(over.id));
    if (from === -1 || to === -1) return;
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    reorder.mutate({ categoryId: section.id === "uncategorized" ? null : section.id, orderedIds: ids });
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-muted-foreground">{section.name}</h2>
        <button
          type="button"
          className="text-xs text-muted-foreground hover:text-foreground"
          onClick={() => onAddClick(section.id === "uncategorized" ? undefined : section.id)}
        >
          + New
        </button>
      </div>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        modifiers={[restrictToVerticalAxis]}
        onDragEnd={handleDragEnd}
      >
        <SortableContext items={section.projects.map((p) => p.id)} strategy={verticalListSortingStrategy}>
          <div className="space-y-1">
            {section.projects.map((project) => (
              <SortableProjectRow key={project.id} project={project} draggable={draggable} />
            ))}
          </div>
        </SortableContext>
      </DndContext>
    </div>
  );
}

function SortableProjectRow({ project, draggable }: { project: Project; draggable: boolean }) {
  const sortable = useSortable({ id: project.id, disabled: !draggable });
  const style = {
    transform: CSS.Transform.toString(sortable.transform),
    transition: sortable.transition,
  };
  return (
    <div ref={sortable.setNodeRef} style={style} className={sortable.isDragging ? "opacity-60" : undefined}>
      <ProjectCard
        project={project}
        dragHandle={
          draggable ? (
            <button
              type="button"
              aria-label="Drag to reorder within this category"
              className="cursor-grab text-muted-foreground"
              {...sortable.attributes}
              {...sortable.listeners}
            >
              <GripVertical className="h-4 w-4" />
            </button>
          ) : (
            <span className="w-4" />
          )
        }
      />
    </div>
  );
}

export function ProjectListPage() {
  const [sortMode, setSortMode] = useState<SortMode>("status");
  const [status, setStatus] = useState("any");
  const [size, setSize] = useState("any");
  const [formOpen, setFormOpen] = useState(false);
  const [formDefaultCategoryId, setFormDefaultCategoryId] = useState<string | undefined>(undefined);
  const [managingCategories, setManagingCategories] = useState(false);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [draggingProjectId, setDraggingProjectId] = useState<string | null>(null);

  const { data: categories = [] } = useProjectCategories();
  const moveCategory = useMoveProjectCategory();
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor),
  );
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

  const draggingProject = projects.find((p) => p.id === draggingProjectId) ?? null;

  const handleDragStart = (event: DragStartEvent) => {
    setDraggingProjectId(String(event.active.id));
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    setDraggingProjectId(null);
    if (!over) return;
    const project = projects.find((p) => p.id === active.id);
    if (!project) return;
    const currentSectionId = project.categoryId ?? "uncategorized";
    if (currentSectionId === over.id) return;
    const targetCategoryId = over.id === "uncategorized" ? null : String(over.id);
    moveCategory.mutate({ project, categoryId: targetCategoryId });
  };

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
              <SelectItem value="custom">Sort by: Custom order</SelectItem>
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
          <Button variant="outline" onClick={() => setGenerateOpen(true)}>
            New project from description
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
      ) : sortMode === "custom" ? (
        <CustomOrderSections
          sections={sections}
          draggable={isDefaultFilter}
          onAddClick={(categoryId) => {
            setFormDefaultCategoryId(categoryId);
            setFormOpen(true);
          }}
        />
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCenter}
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
        >
          <div className="space-y-6">
            {sections.map((section) => (
              <CategorySection key={section.id} id={section.id}>
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
                    <DraggableProjectCard key={project.id} project={project} />
                  ))}
                </div>
              </CategorySection>
            ))}
          </div>
          <DragOverlay>
            {draggingProject ? (
              <div className="rounded-md shadow-lg">
                <ProjectCard project={draggingProject} />
              </div>
            ) : null}
          </DragOverlay>
        </DndContext>
      )}

      <ProjectFormDialog
        open={formOpen}
        onOpenChange={setFormOpen}
        categories={categories}
        defaultCategoryId={formDefaultCategoryId}
        existingColors={projects.map((p) => p.color)}
      />
      <CategoryManagerDialog open={managingCategories} onOpenChange={setManagingCategories} categories={categories} />
      <GenerateProjectDialog open={generateOpen} onOpenChange={setGenerateOpen} />
    </div>
  );
}
