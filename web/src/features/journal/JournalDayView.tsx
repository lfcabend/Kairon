import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { authApi } from "@/lib/api/auth";
import type { Me } from "@/lib/api/types";
import { isValidIsoDate, todayInZone } from "@/lib/date";

import { EntryList } from "./EntryList";
import { JournalDateNav } from "./JournalDateNav";
import { useCreateEntry, useDeleteEntry, useJournalDay, usePatchEntry } from "./useJournal";

export function JournalDayView() {
  const navigate = useNavigate();
  const { date: dateParam } = useParams();
  const meQuery = useQuery<Me>({ queryKey: ["me"], queryFn: authApi.me });
  const [newEntryId, setNewEntryId] = useState<string | null>(null);

  const tz = meQuery.data?.timezone;
  const today = tz ? todayInZone(tz) : undefined;
  const date = isValidIsoDate(dateParam) ? dateParam : today;

  const entriesQuery = useJournalDay(date ?? "");
  const entries = useMemo(() => entriesQuery.data ?? [], [entriesQuery.data]);

  const create = useCreateEntry(date ?? "");
  const patch = usePatchEntry(date ?? "");
  const remove = useDeleteEntry(date ?? "");

  if (!date || !today) {
    return <p className="text-sm text-muted-foreground">Loading…</p>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <JournalDateNav date={date} today={today} onNavigate={(d) => navigate(`/journal/${d}`)} />
        <Link
          to="/journal/search"
          className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <Search className="h-4 w-4" />
          Search
        </Link>
      </div>

      {create.isError && (
        <p className="text-sm text-destructive" role="alert">
          {create.error instanceof Error ? create.error.message : "Could not add the entry."}
        </p>
      )}

      {entriesQuery.isLoading ? (
        <ul className="space-y-1" aria-hidden>
          {[0, 1].map((i) => (
            <li key={i} className="h-16 animate-pulse rounded-md bg-muted" />
          ))}
        </ul>
      ) : entriesQuery.isError ? (
        <div className="space-y-2 text-sm">
          <p className="text-destructive">
            {entriesQuery.error instanceof Error
              ? entriesQuery.error.message
              : "Could not load this day."}
          </p>
          <button
            type="button"
            className="text-primary hover:underline"
            onClick={() => void entriesQuery.refetch()}
          >
            Retry
          </button>
        </div>
      ) : (
        <EntryList
          entries={entries}
          newEntryId={newEntryId}
          onAdd={() =>
            create.mutate(
              { day: date },
              {
                onSuccess: (created) => setNewEntryId(created.id),
              },
            )
          }
          onSave={(id, body) => patch.mutate({ id, body })}
          onDelete={(id) => remove.mutate(id)}
        />
      )}
    </div>
  );
}
