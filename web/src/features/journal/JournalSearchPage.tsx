import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { formatShortDate } from "@/lib/date";

import { useJournalSearch } from "./useJournalSearch";

const PAGE_SIZE = 20;

/** Renders a `ts_headline` snippet's `<b>…</b>` markers as `<strong>`, without `dangerouslySetInnerHTML`. */
function Snippet({ text }: { text: string }) {
  const parts = text.split(/(<b>.*?<\/b>)/g);
  return (
    <>
      {parts.map((part, i) => {
        const match = /^<b>(.*)<\/b>$/.exec(part);
        return match ? <strong key={i}>{match[1]}</strong> : <span key={i}>{part}</span>;
      })}
    </>
  );
}

export function JournalSearchPage() {
  const navigate = useNavigate();
  const [input, setInput] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const id = setTimeout(() => {
      setQ(input);
      setPage(0);
    }, 300);
    return () => clearTimeout(id);
  }, [input]);

  const searchQuery = useJournalSearch(q, page);
  const data = searchQuery.data;
  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / PAGE_SIZE)) : 1;

  return (
    <div className="space-y-4">
      <h1 className="text-lg font-semibold tracking-tight">Search journal</h1>

      <Input
        value={input}
        onChange={(e) => setInput(e.target.value)}
        placeholder="Search your journal…"
        aria-label="Search journal"
        autoFocus
      />

      {searchQuery.isLoading && q && <p className="text-sm text-muted-foreground">Searching…</p>}

      {data && (
        <>
          {data.content.length === 0 ? (
            <p className="text-sm text-muted-foreground">No entries match “{q}”.</p>
          ) : (
            <ul className="space-y-2">
              {data.content.map((hit) => (
                <li key={hit.id}>
                  <button
                    type="button"
                    onClick={() => navigate(`/journal/${hit.day}`)}
                    className="flex w-full flex-col items-start gap-0.5 rounded-md border px-3 py-2 text-left hover:bg-accent"
                  >
                    <div className="flex w-full items-center gap-2">
                      <span className="text-sm font-semibold">{hit.title || "Untitled"}</span>
                      <span className="text-xs text-muted-foreground">{formatShortDate(hit.day)}</span>
                      {hit.mood ? (
                        <span className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                          Mood {hit.mood}
                        </span>
                      ) : null}
                    </div>
                    <p className="text-sm text-muted-foreground">
                      <Snippet text={hit.snippet} />
                    </p>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {totalPages > 1 && (
            <div className="flex items-center justify-between text-sm">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </Button>
              <span className="text-muted-foreground">
                Page {page + 1} of {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
