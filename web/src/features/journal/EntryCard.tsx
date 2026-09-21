import { Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import type { JournalEntry } from "@/lib/api/types";
import { cn } from "@/lib/utils";

import { EntryEditor } from "./EntryEditor";
import { previewText } from "./textPreview";

interface Props {
  entry: JournalEntry;
  startExpanded?: boolean;
  onSave: (patch: { title: string | null; content: string; mood: number | null }) => void;
  onDelete: () => void;
}

export function EntryCard({ entry, startExpanded, onSave, onDelete }: Props) {
  const [expanded, setExpanded] = useState(!!startExpanded);
  const snippet = previewText(entry.content);

  // `startExpanded` can flip true a render *after* mount: the just-created
  // entry lands in the list with its real id (remounting this card, since
  // it's keyed by id) before the parent's `newEntryId` state catches up to
  // match it — two separate, unbatched updates from the create mutation's
  // hook-level cache write vs. its call-level onSuccess. The initial
  // `useState` above only catches the case where both already agree.
  useEffect(() => {
    if (startExpanded) setExpanded(true);
  }, [startExpanded]);

  return (
    <li className="rounded-md border px-3 py-2" data-testid="entry-card">
      {expanded ? (
        <div className="space-y-2">
          <EntryEditor entry={entry} autoFocus={startExpanded} onSave={onSave} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setExpanded(false)}>
              Done
            </Button>
            <Button
              variant="ghost"
              size="sm"
              className="text-destructive"
              onClick={onDelete}
              aria-label="Delete entry"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setExpanded(true)}
          className="flex w-full flex-col items-start gap-0.5 text-left"
        >
          <div className="flex w-full items-center gap-2">
            <span className={cn("text-sm font-semibold", !entry.title && "text-muted-foreground")}>
              {entry.title || "Untitled"}
            </span>
            {entry.mood ? (
              <span className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                Mood {entry.mood}
              </span>
            ) : null}
          </div>
          {snippet && <p className="line-clamp-2 text-sm text-muted-foreground">{snippet}</p>}
        </button>
      )}
    </li>
  );
}
