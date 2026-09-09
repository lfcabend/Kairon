import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { CornerUpLeft, GripVertical, MoreHorizontal } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Checkbox } from "@/components/ui/checkbox";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { TodoItem } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const PRIORITY_LABEL = ["No priority", "Low", "Medium", "High"];
const PRIORITY_DOT = ["bg-transparent", "bg-sky-500", "bg-amber-500", "bg-red-500"];

interface Props {
  item: TodoItem;
  selected: boolean;
  draggable: boolean;
  onSelect: () => void;
  onToggleComplete: () => void;
  onRename: (title: string) => void;
  onEditNotes: (notes: string) => void;
  onCancel: () => void;
  onDelete: () => void;
}

export function TodoRow({
  item,
  selected,
  draggable,
  onSelect,
  onToggleComplete,
  onRename,
  onEditNotes,
  onCancel,
  onDelete,
}: Props) {
  const sortable = useSortable({ id: item.id, disabled: !draggable });
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(item.title);
  const [notesOpen, setNotesOpen] = useState(false);
  const [notes, setNotes] = useState(item.notes ?? "");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => setTitle(item.title), [item.title]);
  useEffect(() => setNotes(item.notes ?? ""), [item.notes]);
  useEffect(() => {
    if (editing) inputRef.current?.select();
  }, [editing]);

  const commitTitle = () => {
    setEditing(false);
    const next = title.trim();
    if (next && next !== item.title) onRename(next);
    else setTitle(item.title);
  };

  const style = {
    transform: CSS.Transform.toString(sortable.transform),
    transition: sortable.transition,
  };

  const done = item.status === "DONE";
  const cancelled = item.status === "CANCELLED";

  return (
    <li
      ref={sortable.setNodeRef}
      style={style}
      data-testid="todo-row"
      data-selected={selected}
      onClick={onSelect}
      className={cn(
        "group flex flex-col gap-1 rounded-md border px-2 py-1.5",
        selected && "ring-2 ring-ring",
        sortable.isDragging && "opacity-60",
      )}
    >
      <div className="flex items-center gap-2">
        {draggable ? (
          <button
            type="button"
            aria-label="Drag to reorder"
            className="cursor-grab text-muted-foreground opacity-0 group-hover:opacity-100"
            {...sortable.attributes}
            {...sortable.listeners}
          >
            <GripVertical className="h-4 w-4" />
          </button>
        ) : (
          <span className="w-4" />
        )}

        <Checkbox
          checked={done}
          aria-label={done ? "Mark as not done" : "Mark as done"}
          onClick={(e) => e.stopPropagation()}
          onCheckedChange={onToggleComplete}
        />

        {editing ? (
          <input
            ref={inputRef}
            className="flex-1 rounded border bg-background px-1 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onClick={(e) => e.stopPropagation()}
            onBlur={commitTitle}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                commitTitle();
              } else if (e.key === "Escape") {
                setTitle(item.title);
                setEditing(false);
              }
            }}
          />
        ) : (
          <span
            className={cn(
              "flex-1 text-sm",
              done && "text-muted-foreground line-through",
              cancelled && "text-muted-foreground line-through opacity-70",
            )}
            onDoubleClick={(e) => {
              e.stopPropagation();
              setEditing(true);
            }}
          >
            {item.title}
          </span>
        )}

        {item.priority > 0 && (
          <span
            title={PRIORITY_LABEL[item.priority]}
            aria-label={PRIORITY_LABEL[item.priority]}
            className={cn("h-2 w-2 rounded-full", PRIORITY_DOT[item.priority])}
          />
        )}

        {item.rolledOverFromId && (
          <CornerUpLeft
            className="h-3.5 w-3.5 text-muted-foreground"
            aria-label="Rolled over from an earlier day"
          />
        )}

        {item.sourceProjectTaskId && (
          <span className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">
            Project
          </span>
        )}

        {item.estimateMinutes ? (
          <span className="text-xs tabular-nums text-muted-foreground">{item.estimateMinutes}m</span>
        ) : null}

        <button
          type="button"
          className="text-xs text-muted-foreground hover:text-foreground"
          onClick={(e) => {
            e.stopPropagation();
            setNotesOpen((v) => !v);
          }}
          aria-label={notesOpen ? "Hide notes" : "Show notes"}
        >
          {notesOpen ? "–" : "+"}
        </button>

        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label="More actions"
            className="text-muted-foreground opacity-0 group-hover:opacity-100 data-[state=open]:opacity-100"
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal className="h-4 w-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {!cancelled && (
              <DropdownMenuItem onClick={onCancel}>Cancel task</DropdownMenuItem>
            )}
            <DropdownMenuItem onClick={onDelete} className="text-destructive">
              Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {notesOpen && (
        <textarea
          className="ml-6 mt-1 min-h-[3rem] w-[calc(100%-1.5rem)] rounded border bg-background p-1.5 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          value={notes}
          placeholder="Notes"
          onClick={(e) => e.stopPropagation()}
          onChange={(e) => setNotes(e.target.value)}
          onBlur={() => {
            if (notes !== (item.notes ?? "")) onEditNotes(notes);
          }}
        />
      )}
    </li>
  );
}
