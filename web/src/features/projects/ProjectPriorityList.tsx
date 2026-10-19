import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
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

import { useMemo } from "react";

import type { Project } from "@/lib/api/types";

import { ProjectCard } from "./ProjectCard";
import { matchesFilter } from "./projectFilter";
import { useProjectCategories } from "./useProjectCategories";
import { useProjectsByPriority, useReorderProjects } from "./useProjects";

interface Props {
  /** D21 — dragging is only offered when the status/size filters are at their defaults. */
  draggable: boolean;
  /** The Projects page's quick filter (local name match, applied client-side — no refetch). */
  filterText?: string;
}

/** Flat, cross-category, drag-orderable list for "Sort by: Priority" (D19). */
export function ProjectPriorityList({ draggable, filterText = "" }: Props) {
  const { data: allProjects = [], isLoading } = useProjectsByPriority();
  const { data: categories = [] } = useProjectCategories();
  const projects = allProjects.filter((p) => matchesFilter(p, filterText));
  const reorder = useReorderProjects();

  const categoryNameById = useMemo(() => new Map(categories.map((c) => [c.id, c.name])), [categories]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const ids = projects.map((p) => p.id);
    const from = ids.indexOf(String(active.id));
    const to = ids.indexOf(String(over.id));
    if (from === -1 || to === -1) return;
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    reorder.mutate(ids);
  };

  if (isLoading) return null;

  if (projects.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        {filterText.trim() === "" ? "No projects yet." : "No projects match your filter."}
      </p>
    );
  }

  return (
    <div className="space-y-2">
      {!draggable && (
        <p className="text-xs text-muted-foreground">Clear filters to reorder by priority.</p>
      )}
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        modifiers={[restrictToVerticalAxis]}
        onDragEnd={handleDragEnd}
      >
        <SortableContext items={projects.map((p) => p.id)} strategy={verticalListSortingStrategy}>
          <ul className="space-y-1">
            {projects.map((project) => (
              <PriorityRow
                key={project.id}
                project={project}
                draggable={draggable}
                categoryName={project.categoryId ? categoryNameById.get(project.categoryId) : "Uncategorized"}
              />
            ))}
          </ul>
        </SortableContext>
      </DndContext>
    </div>
  );
}

function PriorityRow({
  project,
  draggable,
  categoryName,
}: {
  project: Project;
  draggable: boolean;
  categoryName?: string;
}) {
  const sortable = useSortable({ id: project.id, disabled: !draggable });
  const style = {
    transform: CSS.Transform.toString(sortable.transform),
    transition: sortable.transition,
  };

  return (
    <li ref={sortable.setNodeRef} style={style} className={sortable.isDragging ? "opacity-60" : undefined}>
      <ProjectCard
        project={project}
        categoryName={categoryName}
        dragHandle={
          draggable ? (
            <button
              type="button"
              aria-label="Drag to reorder"
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
    </li>
  );
}
