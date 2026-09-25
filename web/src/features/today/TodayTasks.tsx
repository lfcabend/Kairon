import { useState } from "react";

import type { TodoItem } from "@/lib/api/types";

import { DaySummary } from "../todo/DaySummary";
import { QuickAdd } from "../todo/QuickAdd";
import { TodoList } from "../todo/TodoList";
import {
  useCompleteTodo,
  useCreateTodo,
  useDeleteTodo,
  usePatchTodo,
  useReorderTodo,
  useTodos,
} from "../todo/hooks";

/**
 * Today's interactive todo list — the same components/hooks `DayView` uses
 * (D4), bound to `date = today`. No date-nav or keyboard shortcuts here;
 * those stay `/day`'s job.
 */
export function TodayTasks({ date }: { date: string }) {
  const todosQuery = useTodos(date);
  const items = todosQuery.data ?? [];
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const create = useCreateTodo(date);
  const patch = usePatchTodo(date);
  const complete = useCompleteTodo(date);
  const reorder = useReorderTodo(date);
  const remove = useDeleteTodo(date);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="font-medium">Today's tasks</h2>
        <DaySummary items={items} />
      </div>

      <QuickAdd
        onAdd={(title, sourceProjectTaskId) => create.mutate({ day: date, title, sourceProjectTaskId })}
      />

      {create.isError && (
        <p className="text-sm text-destructive" role="alert">
          {create.error instanceof Error ? create.error.message : "Could not add the task."}
        </p>
      )}

      {todosQuery.isLoading ? (
        <ul className="space-y-1" aria-hidden>
          {[0, 1, 2].map((i) => (
            <li key={i} className="h-9 animate-pulse rounded-md bg-muted" />
          ))}
        </ul>
      ) : todosQuery.isError ? (
        <div className="space-y-2 text-sm">
          <p className="text-destructive">
            {todosQuery.error instanceof Error
              ? todosQuery.error.message
              : "Could not load today's tasks."}
          </p>
          <button
            type="button"
            className="text-primary hover:underline"
            onClick={() => void todosQuery.refetch()}
          >
            Retry
          </button>
        </div>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nothing planned for today yet.</p>
      ) : (
        <TodoList
          items={items}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onReorder={(ids) => reorder.mutate(ids)}
          onToggleComplete={(item: TodoItem) =>
            complete.mutate({ id: item.id, complete: item.status !== "DONE" })
          }
          onRename={(id, title) => patch.mutate({ id, body: { title } })}
          onEditNotes={(id, notes) => patch.mutate({ id, body: { notes } })}
          onCancel={(id) => patch.mutate({ id, body: { status: "CANCELLED" } })}
          onDelete={(id) => remove.mutate(id)}
        />
      )}
    </div>
  );
}
