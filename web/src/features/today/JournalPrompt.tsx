import { useState } from "react";
import { Link } from "react-router-dom";

import { Input } from "@/components/ui/input";

import { useCreateEntry } from "../journal/useJournal";
import { useToday } from "./useToday";

/**
 * Inline one-line quick-add (D6): if today has no journal entry yet, Enter
 * creates a minimal one via the existing `useCreateEntry(date)` hook, then
 * this panel switches to a "Continue in Journal →" link for the rest of the
 * session — tracked locally, no `/planning/today` refetch (D7).
 */
export function JournalPrompt({ date }: { date: string }) {
  const todayQuery = useToday(date);
  const create = useCreateEntry(date);
  const [justCreated, setJustCreated] = useState(false);

  const hasEntry = justCreated || todayQuery.data?.journalPrompt.hasEntry === true;

  if (hasEntry) {
    return (
      <Link to={`/journal/${date}`} className="text-sm text-primary hover:underline">
        {justCreated ? "Continue in Journal →" : "You wrote in today's journal — continue →"}
      </Link>
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
