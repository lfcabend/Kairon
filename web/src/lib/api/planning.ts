import type { PromoteTaskBody, TodayResponse, TodoItem } from "./types";

import { apiFetch } from "./client";

/** Thin wrapper over `/planning/today`, mirroring `todoApi`/`journalApi`. */
export const planningApi = {
  today: (date: string) => apiFetch<TodayResponse>(`/planning/today?date=${date}`),

  promote: (body: PromoteTaskBody) =>
    apiFetch<TodoItem>("/planning/today:promote", { method: "POST", body }),
};
