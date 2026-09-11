import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { JournalEntry } from "@/lib/api/types";

import { EntryCard } from "./EntryCard";

interface Props {
  entries: JournalEntry[];
  newEntryId: string | null;
  onAdd: () => void;
  onSave: (id: string, patch: { title: string | null; content: string; mood: number | null }) => void;
  onDelete: (id: string) => void;
}

export function EntryList({ entries, newEntryId, onAdd, onSave, onDelete }: Props) {
  return (
    <div className="space-y-3">
      {entries.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nothing written for this day yet.</p>
      ) : (
        <ul className="space-y-2">
          {entries.map((entry) => (
            <EntryCard
              key={entry.id}
              entry={entry}
              startExpanded={entry.id === newEntryId}
              onSave={(patch) => onSave(entry.id, patch)}
              onDelete={() => onDelete(entry.id)}
            />
          ))}
        </ul>
      )}

      <Button variant="outline" size="sm" onClick={onAdd}>
        <Plus className="mr-1 h-4 w-4" />
        New entry
      </Button>
    </div>
  );
}
