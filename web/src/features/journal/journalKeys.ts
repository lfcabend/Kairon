/** Query-key factory for the journal feature. */
export const journalKeys = {
  all: ["journal"] as const,
  day: (date: string) => ["journal", "day", date] as const,
  entryDays: (from: string, to: string) => ["journal", "entry-days", from, to] as const,
  search: (q: string, page: number) => ["journal", "search", q, page] as const,
};
