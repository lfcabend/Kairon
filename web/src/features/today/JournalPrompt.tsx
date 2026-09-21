import { useState } from "react";
import { Link } from "react-router-dom";

import { Input } from "@/components/ui/input";

import { previewText } from "../journal/textPreview";
import { useCreateEntry, useJournalDay } from "../journal/useJournal";
import { useToday } from "./useToday";

/**
 * Inline one-line quick-add (D6): if today has no journal entry yet, Enter
 * creates a minimal one via the existing `useCreateEntry(date)` hook. Once an
 * entry exists, shows a preview of the latest entry (same cache key as
 * `useCreateEntry`, so the just-created one appears without an extra round
 * trip) so returning to Today later in the day jogs your memory, plus a link
 * to continue writing.
 */
export function JournalPrompt({ date }: { date: string }) {
  const todayQuery = useToday(date);
  const create = useCreateEntry(date);
  const [justCreated, setJustCreated] = useState(false);

  const hasEntry = justCreated || todayQuery.data?.journalPrompt.hasEntry === true;
  const entriesQuery = useJournalDay(date, hasEntry);
  const latest = entriesQuery.data?.at(-1);

  if (hasEntry) {
    const snippet = latest ? previewText(latest.content) : "";
    return (
      <div className="space-y-1.5">
        {latest && (
          <div className="rounded-md border px-3 py-2">
            <div className="flex items-center gap-2">
              <span
                className={`text-sm font-semibold ${latest.title ? "" : "text-muted-foreground"}`}
              >
                {latest.title || "Untitled"}
              </span>
              {latest.mood ? (
                <span className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                  Mood {latest.mood}
                </span>
              ) : null}
            </div>
            {snippet && <p className="line-clamp-3 text-sm text-muted-foreground">{snippet}</p>}
          </div>
        )}
        <Link to={`/journal/${date}`} className="text-sm text-primary hover:underline">
          Continue in Journal →
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-1">
      <Input
        placeholder="Jot a quick line and press Enter"
        aria-label="Quick journal entry"
        disabled={create.isPending}
        onKeyDown={(e) => {
          const target = e.currentTarget;
          if (e.key !== "Enter") return;
          const content = target.value.trim();
          if (!content) return;
          e.preventDefault();
          create.mutate(
            { day: date, content },
            { onSuccess: () => setJustCreated(true) },
          );
        }}
      />
      {create.isError && (
        <p className="text-sm text-destructive" role="alert">
          {create.error instanceof Error ? create.error.message : "Could not save the entry."}
        </p>
      )}
    </div>
  );
}
