/** Query-key factory for the planning/Today feature. */
export const planningKeys = {
  today: (date: string) => ["planning", "today", date] as const,
};
