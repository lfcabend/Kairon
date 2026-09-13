import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  closestCorners,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { useState } from "react";

import type { ProjectTask, ProjectTaskStatus } from "@/lib/api/types";

import { TaskCard, TaskCardOverlay } from "./TaskCard";
import { usePatchTask, useReorderTasks } from "./useProjectTasks";

interface Props {
  projectId: string;
  tasks: ProjectTask[];
}

const COLUMNS: { status: ProjectTaskStatus; label: string }[] = [
  { status: "TODO", label: "To do" },
  { status: "IN_PROGRESS", label: "In progress" },
  { status: "BLOCKED", label: "Blocked" },
  { status: "DONE", label: "Done" },
];

const STATUSES = COLUMNS.map((c) => c.status) as string[];

function toPatchBody(task: ProjectTask, overrides: Partial<ProjectTask>) {
  const merged = { ...task, ...overrides };
  return {
    name: merged.name,
    description: merged.description,
    status: merged.status,
    parentTaskId: merged.parentTaskId,
    plannedStart: merged.plannedStart,
    plannedEnd: merged.plannedEnd,
    estimateHours: merged.estimateHours,
    actualHours: merged.actualHours,
    progressPercent: merged.progressPercent,
    isMilestone: merged.isMilestone,
    expectedVersion: task.version,
  };
}

/** Every task, top-level and subtask alike, as its own card grouped by status (D11). */
export function TaskBoard({ projectId, tasks }: Props) {
  const patchTask = usePatchTask(projectId);
  const reorderTasks = useReorderTasks(projectId);
  const [activeId, setActiveId] = useState<string | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const byId = new Map(tasks.map((t) => [t.id, t]));
  const nameById = new Map(tasks.map((t) => [t.id, t.name]));

  const columnTasks = (status: ProjectTaskStatus) =>
    tasks.filter((t) => t.status === status).sort((a, b) => a.position - b.position || a.name.localeCompare(b.name));

  const handleDragStart = (event: DragStartEvent) => setActiveId(String(event.active.id));

  const handleDragEnd = (event: DragEndEvent) => {
    setActiveId(null);
    const { active, over } = event;
    if (!over) return;
    const activeTask = byId.get(String(active.id));
    if (!activeTask) return;
    const overId = String(over.id);
    const destStatus = STATUSES.includes(overId) ? (overId as ProjectTaskStatus) : byId.get(overId)?.status;
    if (!destStatus) return;

    if (destStatus !== activeTask.status) {
      // Cross-column drop: PATCHes { status } only, no position change (D4).
      patchTask.mutate({ id: activeTask.id, body: toPatchBody(activeTask, { status: destStatus }) });
      return;
    }

    // Within-column drop: reorders the dragged task's sibling group (D2) only when the
    // drop neighbor shares the same parent — a status column can mix tasks from different
    // sibling groups, and `position` is scoped per (project, parentTaskId), not per column.
    const overTask = byId.get(overId);
    if (!overTask || overTask.id === activeTask.id || overTask.parentTaskId !== activeTask.parentTaskId) return;
    const siblings = tasks.filter((t) => t.parentTaskId === activeTask.parentTaskId);
    const ids = siblings.map((t) => t.id);
    const from = ids.indexOf(activeTask.id);
    const to = ids.indexOf(overTask.id);
    if (from === -1 || to === -1) return;
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    reorderTasks.mutate({ parentTaskId: activeTask.parentTaskId, orderedIds: ids });
  };

  const activeTask = activeId ? byId.get(activeId) : undefined;

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
      onDragCancel={() => setActiveId(null)}
    >
      <div className="grid grid-cols-4 gap-3 overflow-x-auto">
        {COLUMNS.map(({ status, label }) => (
          <Column key={status} status={status} label={label}>
            <SortableContext
              items={columnTasks(status).map((t) => t.id)}
              strategy={verticalListSortingStrategy}
            >
              {columnTasks(status).map((task) => (
                <TaskCard
                  key={task.id}
                  task={task}
                  parentName={task.parentTaskId ? nameById.get(task.parentTaskId) : undefined}
                />
              ))}
            </SortableContext>
          </Column>
        ))}
      </div>
      <DragOverlay>
        {activeTask ? (
          <TaskCardOverlay
            task={activeTask}
            parentName={activeTask.parentTaskId ? nameById.get(activeTask.parentTaskId) : undefined}
          />
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}

function Column({
  status,
  label,
  children,
}: {
  status: ProjectTaskStatus;
  label: string;
  children: React.ReactNode;
}) {
  const { setNodeRef } = useDroppable({ id: status });
  return (
    <div className="min-w-[180px] space-y-2 rounded-md bg-muted/40 p-2">
      <h3 className="text-xs font-semibold text-muted-foreground">{label}</h3>
      <div ref={setNodeRef} className="min-h-[2rem] space-y-2">
        {children}
      </div>
    </div>
  );
}
