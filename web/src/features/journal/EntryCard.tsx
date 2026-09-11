import { Trash2 } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import type { JournalEntry } from "@/lib/api/types";
import { cn } from "@/lib/utils";

import { EntryEditor } from "./EntryEditor";

interface Props {
  entry: JournalEntry;
  startExpanded?: boolean;
  onSave: (patch: { title: string | null; content: string; mood: number | null }) => void;
  onDelete: () => void;
}

/** Strips the minimal markdown syntax the editor can produce, for a plain-text list preview. */
function previewText(content: string): string {
  return content
    .replace(/^#{1,6}\s+/gm, "")
    .replace(/\*\*(.*?)\*\*/g, "$1")
    .replace(/\*(.*?)\*/g, "$1")
    .replace(/\s+/g, " ")
    .trim();
}

export function EntryCard({ entry, startExpanded, onSave, onDelete }: Props) {
  const [expanded, setExpanded] = useState(!!startExpanded);
  const snippet = previewText(entry.content);

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
