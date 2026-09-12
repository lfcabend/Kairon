/** Query-key factory for the projects feature (incl. categories and tasks). */
export const projectKeys = {
  categories: () => ["projects", "categories"] as const,
  list: (status: string | undefined, categoryId: string | undefined, size: string | undefined, page: number) =>
    ["projects", "list", status ?? null, categoryId ?? null, size ?? null, page] as const,
  priorityOrdered: () => ["projects", "priority-ordered"] as const,
  detail: (id: string) => ["projects", "detail", id] as const,
  tasks: (projectId: string) => ["projects", "tasks", projectId] as const,
};
