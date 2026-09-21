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
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { useState } from "react";

import type { TodoItem } from "@/lib/api/types";

import { TodoRow } from "./TodoRow";

interface Props {
  items: TodoItem[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onReorder: (orderedIds: string[]) => void;
  onToggleComplete: (item: TodoItem) => void;
  onRename: (id: string, title: string) => void;
  onEditNotes: (id: string, notes: string) => void;
  onCancel: (id: string) => void;
  onDelete: (id: string) => void;
}

export function TodoList({
  items,
  selectedId,
  onSelect,
  onReorder,
  onToggleComplete,
  onRename,
  onEditNotes,
  onCancel,
  onDelete,
}: Props) {
  const open = items.filter((t) => t.status === "OPEN");
  const done = items.filter((t) => t.status === "DONE");
  const cancelled = items.filter((t) => t.status === "CANCELLED");
  const [showCancelled, setShowCancelled] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const openIds = open.map((t) => t.id);
    const from = openIds.indexOf(String(active.id));
    const to = openIds.indexOf(String(over.id));
    if (from === -1 || to === -1) return;
    openIds.splice(to, 0, openIds.splice(from, 1)[0]);
    // The backend's reorder endpoint requires the full day (every status), so
    // splice the reordered open ids back into their original slots rather than
    // dropping the done/cancelled ids.
    let i = 0;
    const ids = items.map((t) => (t.status === "OPEN" ? openIds[i++] : t.id));
    onReorder(ids);
  };

  const rowProps = (item: TodoItem, draggable: boolean) => ({
    item,
    draggable,
    selected: selectedId === item.id,
    onSelect: () => onSelect(item.id),
    onToggleComplete: () => onToggleComplete(item),
    onRename: (title: string) => onRename(item.id, title),
    onEditNotes: (notes: string) => onEditNotes(item.id, notes),
    onCancel: () => onCancel(item.id),
    onDelete: () => onDelete(item.id),
  });

  return (
    <div className="space-y-4">
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        modifiers={[restrictToVerticalAxis]}
        onDragEnd={handleDragEnd}
      >
        <SortableContext items={open.map((t) => t.id)} strategy={verticalListSortingStrategy}>
          <ul className="space-y-1">
            {open.map((item) => (
              <TodoRow key={item.id} {...rowProps(item, true)} />
            ))}
          </ul>
        </SortableContext>
      </DndContext>

      {done.length > 0 && (
        <div className="space-y-1 border-t pt-3">
          <ul className="space-y-1">
            {done.map((item) => (
              <TodoRow key={item.id} {...rowProps(item, false)} />
            ))}
          </ul>
        </div>
      )}

      {cancelled.length > 0 && (
        <div className="space-y-1">
          <button
            type="button"
            className="text-xs text-muted-foreground hover:text-foreground"
            onClick={() => setShowCancelled((v) => !v)}
          >
            {showCancelled ? "Hide" : "Show"} {cancelled.length} cancelled
          </button>
          {showCancelled && (
            <ul className="space-y-1">
              {cancelled.map((item) => (
                <TodoRow key={item.id} {...rowProps(item, false)} />
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

