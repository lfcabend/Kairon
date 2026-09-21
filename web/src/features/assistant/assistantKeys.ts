/** Query-key factory for the assistant feature. */
export const assistantKeys = {
  run: (id: string) => ["assistant", "run", id] as const,
};
