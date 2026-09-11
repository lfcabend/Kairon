import { useQuery } from "@tanstack/react-query";

import { journalApi } from "@/lib/api/journal";

import { journalKeys } from "./journalKeys";

/** Disabled while `q` is blank — the search endpoint itself rejects an empty query. */
export function useJournalSearch(q: string, page: number) {
  const query = q.trim();
  return useQuery({
    queryKey: journalKeys.search(query, page),
    queryFn: () => journalApi.search(query, page),
    enabled: query.length > 0,
  });
}
