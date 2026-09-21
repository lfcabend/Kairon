import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { journalApi } from "@/lib/api/journal";
import type { CreateJournalEntryBody, JournalEntry, PatchJournalEntryBody } from "@/lib/api/types";
import { randomId } from "@/lib/id";

import { journalKeys } from "./journalKeys";

const byOrder = (a: JournalEntry, b: JournalEntry) =>
  a.position - b.position || a.createdAt.localeCompare(b.createdAt);

export function useJournalDay(date: string, enabled = true) {
  return useQuery({
    queryKey: journalKeys.day(date),
    queryFn: () => journalApi.list(date),
    enabled: !!date && enabled,
  });
}

/** A month (or other window) of entry-days, for `JournalDateNav`'s calendar dots. */
export function useEntryDays(from: string, to: string) {
  return useQuery({
    queryKey: journalKeys.entryDays(from, to),
    queryFn: () => journalApi.entryDays(from, to),
    enabled: !!from && !!to,
  });
}

/** Optimistically appends a temporary entry; reconciles on success, rolls back on error. */
export function useCreateEntry(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateJournalEntryBody) => journalApi.create(body),
    onMutate: async (body) => {
      await qc.cancelQueries({ queryKey: journalKeys.day(date) });
      const previous = qc.getQueryData<JournalEntry[]>(journalKeys.day(date)) ?? [];
      const tempId = `temp-${randomId()}`;
      const maxPos = previous.reduce((m, e) => Math.max(m, e.position), 0);
      const now = new Date().toISOString();
      const optimistic: JournalEntry = {
        id: tempId,
        day: body.day,
        position: maxPos + 100,
        title: body.title ?? null,
        content: body.content ?? "",
        mood: body.mood ?? null,
        createdAt: now,
        updatedAt: now,
        version: 0,
      };
      qc.setQueryData<JournalEntry[]>(journalKeys.day(date), [...previous, optimistic]);
      return { previous, tempId };
    },
    onError: (_err, _body, ctx) => {
      if (ctx) qc.setQueryData(journalKeys.day(date), ctx.previous);
    },
    onSuccess: (created, _body, ctx) => {
      qc.setQueryData<JournalEntry[]>(journalKeys.day(date), (list = []) =>
        list.map((e) => (e.id === ctx?.tempId ? created : e)).sort(byOrder),
      );
    },
  });
}

export function usePatchEntry(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: PatchJournalEntryBody }) => journalApi.patch(id, body),
    onMutate: async ({ id, body }) => {
      await qc.cancelQueries({ queryKey: journalKeys.day(date) });
      const previous = qc.getQueryData<JournalEntry[]>(journalKeys.day(date)) ?? [];
      qc.setQueryData<JournalEntry[]>(
        journalKeys.day(date),
        previous.map((e) => (e.id === id ? { ...e, ...stripUndefined(body) } : e)),
      );
      return { previous };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx) qc.setQueryData(journalKeys.day(date), ctx.previous);
    },
    onSuccess: (updated) => {
      qc.setQueryData<JournalEntry[]>(journalKeys.day(date), (list = []) =>
        list.map((e) => (e.id === updated.id ? updated : e)).sort(byOrder),
      );
    },
  });
}

export function useDeleteEntry(date: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => journalApi.remove(id),
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: journalKeys.day(date) });
      const previous = qc.getQueryData<JournalEntry[]>(journalKeys.day(date)) ?? [];
      qc.setQueryData<JournalEntry[]>(
        journalKeys.day(date),
        previous.filter((e) => e.id !== id),
      );
      return { previous };
    },
    onError: (_err, _id, ctx) => {
      if (ctx) qc.setQueryData(journalKeys.day(date), ctx.previous);
    },
  });
}

function stripUndefined<T extends object>(obj: T): Partial<T> {
  return Object.fromEntries(Object.entries(obj).filter(([, v]) => v !== undefined)) as Partial<T>;
}
