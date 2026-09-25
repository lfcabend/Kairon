import { useQuery } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { authApi } from "@/lib/api/auth";
import type { Me, TodoItem } from "@/lib/api/types";
import { addDays, isValidIsoDate, todayInZone } from "@/lib/date";

import { SuggestTodosButton } from "../assistant/SuggestTodosButton";
import { DateNav } from "./DateNav";
import { DaySummary } from "./DaySummary";
import { QuickAdd } from "./QuickAdd";
import { RolloverPrompt } from "./RolloverPrompt";
import { TodoList } from "./TodoList";
import {
  useCompleteTodo,
  useCreateTodo,
  useDeleteTodo,
  usePatchTodo,
  useReorderTodo,
  useTodos,
} from "./hooks";
import { getRolloverMode, useAutoRollover, useRolloverPreview } from "./useRollover";

function isTextTarget(el: EventTarget | null): boolean {
  return el instanceof HTMLElement && ["INPUT", "TEXTAREA"].includes(el.tagName);
}

export function DayView() {
  const navigate = useNavigate();
  const { date: dateParam } = useParams();
  const meQuery = useQuery<Me>({ queryKey: ["me"], queryFn: authApi.me });
  const quickAddRef = useRef<HTMLInputElement>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const tz = meQuery.data?.timezone;
  const today = tz ? todayInZone(tz) : undefined;
  const date = isValidIsoDate(dateParam) ? dateParam : today;

  const todosQuery = useTodos(date ?? "");
  const items = useMemo(() => todosQuery.data ?? [], [todosQuery.data]);
  const openItems = useMemo(() => items.filter((t) => t.status === "OPEN"), [items]);

  const mode = getRolloverMode(meQuery.data);
  const isToday = !!date && date === today;
  const previewQuery = useRolloverPreview(date ?? "", isToday && !!date);
  useAutoRollover({ mode, isToday, today: today ?? "", preview: previewQuery.data });

  const create = useCreateTodo(date ?? "");
  const patch = usePatchTodo(date ?? "");
  const complete = useCompleteTodo(date ?? "");
  const reorder = useReorderTodo(date ?? "");
  const remove = useDeleteTodo(date ?? "");

  const move = useCallback(
    (delta: number) => {
      if (openItems.length === 0) return;
      const idx = openItems.findIndex((t) => t.id === selectedId);
      const next = idx === -1 ? 0 : Math.min(openItems.length - 1, Math.max(0, idx + delta));
      setSelectedId(openItems[next].id);
    },
    [openItems, selectedId],
  );

  const reorderSelected = useCallback(
    (delta: number) => {
      const idx = openItems.findIndex((t) => t.id === selectedId);
      if (idx === -1) return;
      const target = idx + delta;
      if (target < 0 || target >= openItems.length) return;
      const openIds = openItems.map((t) => t.id);
      openIds.splice(target, 0, openIds.splice(idx, 1)[0]);
      // The backend's reorder endpoint requires the full day (every status), so
      // splice the reordered open ids back into their original slots rather than
      // dropping the done/cancelled ids.
      let i = 0;
      const ids = items.map((t) => (t.status === "OPEN" ? openIds[i++] : t.id));
      reorder.mutate(ids);
    },
    [items, openItems, selectedId, reorder],
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!date) return;
      if ((e.key === "/" || e.key === "n") && !isTextTarget(e.target)) {
        e.preventDefault();
        quickAddRef.current?.focus();
        return;
      }
      if (isTextTarget(e.target)) return;

      const selected = items.find((t) => t.id === selectedId);
      switch (e.key) {
        case "ArrowDown":
          e.preventDefault();
          e.altKey ? reorderSelected(1) : move(1);
          break;
        case "ArrowUp":
          e.preventDefault();
          e.altKey ? reorderSelected(-1) : move(-1);
          break;
        case "ArrowLeft":
          navigate(`/day/${addDays(date, -1)}`);
          break;
        case "ArrowRight":
          navigate(`/day/${addDays(date, 1)}`);
          break;
        case " ":
        case "x":
          if (selected) {
            e.preventDefault();
            complete.mutate({ id: selected.id, complete: selected.status !== "DONE" });
          }
          break;
        case "0":
        case "1":
        case "2":
        case "3":
          if (selected) patch.mutate({ id: selected.id, body: { priority: Number(e.key) } });
          break;
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [date, items, selectedId, move, reorderSelected, complete, patch, navigate]);

  if (!date || !today) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <DateNav date={date} today={today} onNavigate={(d) => navigate(`/day/${d}`)} />
        <div className="flex items-center gap-3">
          <DaySummary items={items} />
          <SuggestTodosButton day={date} />
        </div>
      </div>

      <QuickAdd
        ref={quickAddRef}
        onAdd={(title, sourceProjectTaskId) => create.mutate({ day: date, title, sourceProjectTaskId })}
      />

      {isToday && previewQuery.data && (
        <RolloverPrompt preview={previewQuery.data} mode={mode} toDay={today} />
      )}

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
              : "Could not load this day."}
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
        <p className="text-sm text-muted-foreground">Nothing planned for this day yet.</p>
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
