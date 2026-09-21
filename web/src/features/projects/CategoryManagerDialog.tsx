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
import { GripVertical, Trash2 } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import type { ProjectCategory } from "@/lib/api/types";
import { pickUniqueColor } from "@/lib/color";

import { useProjectsByPriority } from "./useProjects";
import {
  useCreateCategory,
  useDeleteCategory,
  usePatchCategory,
  useReorderCategories,
} from "./useProjectCategories";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  categories: ProjectCategory[];
}

export function CategoryManagerDialog({ open, onOpenChange, categories }: Props) {
  const reorder = useReorderCategories();
  const create = useCreateCategory();
  const deleteCategory = useDeleteCategory();
  const { data: projects = [] } = useProjectsByPriority();
  const [newName, setNewName] = useState("");
  const [newColor, setNewColor] = useState(() => pickUniqueColor(categories.map((c) => c.color)));
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

  useEffect(() => {
    if (open) setNewColor(pickUniqueColor(categories.map((c) => c.color)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const sorted = [...categories].sort((a, b) => a.position - b.position);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const ids = sorted.map((c) => c.id);
    const from = ids.indexOf(String(active.id));
    const to = ids.indexOf(String(over.id));
    if (from === -1 || to === -1) return;
    ids.splice(to, 0, ids.splice(from, 1)[0]);
    reorder.mutate(ids);
  };

  const affectedCount = confirmDeleteId
    ? projects.filter((p) => p.categoryId === confirmDeleteId).length
    : 0;
  const confirmDeleteName = categories.find((c) => c.id === confirmDeleteId)?.name;

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Manage categories</DialogTitle>
          </DialogHeader>

          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            modifiers={[restrictToVerticalAxis]}
            onDragEnd={handleDragEnd}
          >
            <SortableContext items={sorted.map((c) => c.id)} strategy={verticalListSortingStrategy}>
              <ul className="space-y-1">
                {sorted.map((category) => (
                  <CategoryRow
                    key={category.id}
                    category={category}
                    onDeleteRequested={() => setConfirmDeleteId(category.id)}
                  />
                ))}
              </ul>
            </SortableContext>
          </DndContext>

          {sorted.length === 0 && (
            <p className="text-sm text-muted-foreground">No categories yet.</p>
          )}

          <form
            className="flex items-center gap-2 border-t pt-3"
            onSubmit={(e) => {
              e.preventDefault();
              const name = newName.trim();
              if (!name) return;
              create.mutate({ name, color: newColor });
              setNewName("");
              setNewColor(pickUniqueColor([...categories.map((c) => c.color), newColor]));
            }}
          >
            <input
              type="color"
              aria-label="New category color"
              className="h-9 w-9 shrink-0 rounded border border-input bg-background"
              value={newColor}
              onChange={(e) => setNewColor(e.target.value)}
            />
            <Input
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              placeholder="New category name"
              aria-label="New category name"
            />
            <Button type="submit" size="sm" disabled={!newName.trim()}>
              Add
            </Button>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmDeleteId != null} onOpenChange={(v) => !v && setConfirmDeleteId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete "{confirmDeleteName}"?</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            {affectedCount > 0
              ? `${affectedCount} project${affectedCount === 1 ? "" : "s"} will become uncategorized.`
              : "No projects use this category."}
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setConfirmDeleteId(null)}>
              Cancel
            </Button>
            <Button
              variant="default"
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                if (confirmDeleteId) deleteCategory.mutate(confirmDeleteId);
                setConfirmDeleteId(null);
              }}
            >
              Delete
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

function CategoryRow({
  category,
  onDeleteRequested,
}: {
  category: ProjectCategory;
  onDeleteRequested: () => void;
}) {
  const patch = usePatchCategory();
  const sortable = useSortable({ id: category.id });
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(category.name);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => setName(category.name), [category.name]);
  useEffect(() => {
    if (editing) inputRef.current?.select();
  }, [editing]);

  const style = {
    transform: CSS.Transform.toString(sortable.transform),
    transition: sortable.transition,
  };

  const commit = () => {
    setEditing(false);
    const trimmed = name.trim();
    if (trimmed && trimmed !== category.name) {
      patch.mutate({ id: category.id, body: { name: trimmed } });
    } else {
      setName(category.name);
    }
  };

  return (
    <li
      ref={sortable.setNodeRef}
      style={style}
      className="flex items-center gap-2 rounded-md border px-2 py-1.5"
    >
      <button
        type="button"
        aria-label="Drag to reorder"
        className="cursor-grab text-muted-foreground"
        {...sortable.attributes}
        {...sortable.listeners}
      >
        <GripVertical className="h-4 w-4" />
      </button>

      <input
        type="color"
        aria-label={`${category.name} color`}
        className="h-6 w-6 rounded border border-input bg-background"
        value={category.color}
        onChange={(e) => patch.mutate({ id: category.id, body: { color: e.target.value } })}
      />

      {editing ? (
        <input
          ref={inputRef}
          className="flex-1 rounded border bg-background px-1 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          value={name}
          onChange={(e) => setName(e.target.value)}
          onBlur={commit}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              commit();
            } else if (e.key === "Escape") {
              setName(category.name);
              setEditing(false);
            }
          }}
        />
      ) : (
        <span className="flex-1 text-sm" onDoubleClick={() => setEditing(true)}>
          {category.name}
        </span>
      )}

      <Button variant="ghost" size="icon" aria-label={`Delete ${category.name}`} onClick={onDeleteRequested}>
        <Trash2 className="h-4 w-4 text-destructive" />
      </Button>
    </li>
  );
}
