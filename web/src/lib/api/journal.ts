import type {
  CreateJournalEntryBody,
  JournalEntry,
  JournalSearchPage,
  PatchJournalEntryBody,
} from "./types";

import { apiFetch } from "./client";

/** Thin wrapper over the `/journal` endpoints, mirroring `todoApi`. */
export const journalApi = {
  list: (day: string) => apiFetch<JournalEntry[]>(`/journal?day=${day}`),

  range: (from: string, to: string) => apiFetch<JournalEntry[]>(`/journal?from=${from}&to=${to}`),

  entryDays: (from: string, to: string) =>
    apiFetch<string[]>(`/journal/entry-days?from=${from}&to=${to}`),

  create: (body: CreateJournalEntryBody) =>
    apiFetch<JournalEntry>("/journal", { method: "POST", body }),

  patch: (id: string, body: PatchJournalEntryBody) =>
    apiFetch<JournalEntry>(`/journal/${id}`, { method: "PATCH", body }),

  remove: (id: string) => apiFetch<void>(`/journal/${id}`, { method: "DELETE" }),

  search: (q: string, page: number) =>
    apiFetch<JournalSearchPage>(`/journal:search?q=${encodeURIComponent(q)}&page=${page}`),
};
