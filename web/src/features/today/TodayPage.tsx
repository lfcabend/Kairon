import { useQuery } from "@tanstack/react-query";

import { authApi } from "@/lib/api/auth";
import type { Me } from "@/lib/api/types";
import { formatLongDate, todayInZone } from "@/lib/date";

import { SuggestTodosButton } from "../assistant/SuggestTodosButton";
import { RolloverPrompt } from "../todo/RolloverPrompt";
import { getRolloverMode, useAutoRollover, useRolloverPreview } from "../todo/useRollover";
import { DueTasksPanel } from "./DueTasksPanel";
import { JournalPrompt } from "./JournalPrompt";
import { TodayTasks } from "./TodayTasks";

/**
 * The app's default landing page (D5): always "today" (D8 — no date-nav),
 * combining the day's todo list, due/overdue project tasks, and a journal
 * quick-entry. Reuses `/day`'s rollover wiring (D10) — the sweep is
 * idempotent, so mounting it here too is safe even if the user also visits
 * `/day` on the same date.
 */
export function TodayPage() {
  const meQuery = useQuery<Me>({ queryKey: ["me"], queryFn: authApi.me });
  const tz = meQuery.data?.timezone;
  const date = tz ? todayInZone(tz) : undefined;

  const mode = getRolloverMode(meQuery.data);
  const previewQuery = useRolloverPreview(date ?? "", !!date);
  useAutoRollover({ mode, isToday: true, today: date ?? "", preview: previewQuery.data });

  if (!date) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold">{formatLongDate(date)}</h1>
        <SuggestTodosButton day={date} />
      </div>

      {previewQuery.data && <RolloverPrompt preview={previewQuery.data} mode={mode} toDay={date} />}

      <TodayTasks date={date} />

      <div className="space-y-2">
        <h2 className="font-medium">Due & overdue</h2>
        <DueTasksPanel date={date} />
      </div>

      <div className="space-y-2">
        <h2 className="font-medium">Journal</h2>
        <JournalPrompt date={date} />
      </div>
    </div>
  );
}
