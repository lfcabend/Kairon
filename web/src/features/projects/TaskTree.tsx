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
import { SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { useState } from "react";

import type { ProjectTask, ProjectTaskStatus } from "@/lib/api/types";

import { TaskFormDialog } from "./TaskFormDialog";
import { TaskQuickAdd } from "./TaskQuickAdd";
import { TaskRow } from "./TaskRow";
import { useCreateTask, useDeleteTask, usePatchTask, useReorderTasks } from "./useProjectTasks";

interface Props {
  projectId: string;
  topLevel: ProjectTask[];
  childrenByParentId: Map<string, ProjectTask[]>;
}

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

/** Because the hierarchy is <=2 levels, this is one `SortableContext` per level, not a general recursive tree. */
export function TaskTree({ projectId, topLevel, childrenByParentId }: Props) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [editing, setEditing] = useState<ProjectTask | undefined>(undefined);

  const createTask = useCreateTask(projectId);
  const patchTask = usePatchTask(projectId);
  const deleteTask = useDeleteTask(projectId);
  const reorderTasks = useReorderTasks(projectId);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const toggle = (id: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const reorderGroup = (ids: string[], parentTaskId: string | null) =>
    reorderTasks.mutate({ parentTaskId, orderedIds: ids });

  const handleDragEnd = (siblingIds: string[], parentTaskId: string | null) => (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const ids = [...siblingIds];
    const from = ids.indexOf(String(active.id));
    const to = ids.indexOf(String(over.id));
    if (from === -1 || to === -1) return;
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    reorderGroup(ids, parentTaskId);
  };

  const topLevelIds = topLevel.map((t) => t.id);

  return (
    <div className="space-y-1">
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        modifiers={[restrictToVerticalAxis]}
        onDragEnd={handleDragEnd(topLevelIds, null)}
      >
        <SortableContext items={topLevelIds} strategy={verticalListSortingStrategy}>
          <ul className="space-y-1">
            {topLevel.map((task) => {
              const children = childrenByParentId.get(task.id) ?? [];
              const isExpanded = expanded.has(task.id);
              return (
                <li key={task.id} className="space-y-1">
                  <ul>
                    <TaskRow
                      task={task}
                      draggable
                      hasChildren={children.length > 0}
                      expanded={isExpanded}
                      onToggleExpand={() => toggle(task.id)}
                      onChangeStatus={(status: ProjectTaskStatus) =>
                        patchTask.mutate({ id: task.id, body: toPatchBody(task, { status }) })
                      }
                      onRename={(name) => patchTask.mutate({ id: task.id, body: toPatchBody(task, { name }) })}
                      onEdit={() => setEditing(task)}
                      onDelete={() => deleteTask.mutate(task.id)}
                    />
                  </ul>

                  {isExpanded && (
                    <div className="ml-6 space-y-1">
                      <DndContext
                        sensors={sensors}
                        collisionDetection={closestCenter}
                        modifiers={[restrictToVerticalAxis]}
                        onDragEnd={handleDragEnd(
                          children.map((c) => c.id),
                          task.id,
                        )}
                      >
                        <SortableContext items={children.map((c) => c.id)} strategy={verticalListSortingStrategy}>
                          <ul className="space-y-1">
                            {children.map((child) => (
                              <TaskRow
                                key={child.id}
                                task={child}
                                draggable
                                onChangeStatus={(status: ProjectTaskStatus) =>
                                  patchTask.mutate({ id: child.id, body: toPatchBody(child, { status }) })
                                }
                                onRename={(name) =>
                                  patchTask.mutate({ id: child.id, body: toPatchBody(child, { name }) })
                                }
                                onEdit={() => setEditing(child)}
                                onDelete={() => deleteTask.mutate(child.id)}
                              />
                            ))}
                          </ul>
                        </SortableContext>
                      </DndContext>
                      <TaskQuickAdd onAdd={(name) => createTask.mutate({ name, parentTaskId: task.id })} />
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </SortableContext>
      </DndContext>

      <TaskQuickAdd onAdd={(name) => createTask.mutate({ name })} />

      {topLevel.length === 0 && <p className="text-sm text-muted-foreground">No tasks yet.</p>}

      {editing && (
        <TaskFormDialog
          open={Boolean(editing)}
          onOpenChange={(open) => !open && setEditing(undefined)}
          projectId={projectId}
          task={editing}
          parentCandidates={topLevel.filter((t) => (childrenByParentId.get(t.id) ?? []).length === 0)}
        />
      )}
    </div>
  );
}
