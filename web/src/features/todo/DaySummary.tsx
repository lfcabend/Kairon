import type { TodoItem } from "@/lib/api/types";

/** "3 open · 2 done · ~90 min left", computed from the day's list. */
export function DaySummary({ items }: { items: TodoItem[] }) {
  const open = items.filter((t) => t.status === "OPEN");
  const done = items.filter((t) => t.status === "DONE");
  const minutesLeft = open.reduce((sum, t) => sum + (t.estimateMinutes ?? 0), 0);

  const parts = [`${open.length} open`, `${done.length} done`];
  if (minutesLeft > 0) parts.push(`~${minutesLeft} min left`);

  return <p className="text-sm text-muted-foreground">{parts.join(" · ")}</p>;
}
