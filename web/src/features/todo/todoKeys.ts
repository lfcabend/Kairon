/** Query-key factory for the todo feature. */
export const todoKeys = {
  all: ["todo"] as const,
  day: (date: string) => ["todo", "day", date] as const,
  rolloverPreview: (date: string) => ["todo", "rollover-preview", date] as const,
};
