import type { Project } from "@/lib/api/types";

/** Case-insensitive, local (no network round-trip) name match for the Projects page's quick filter. */
export function matchesFilter(project: Project, filter: string): boolean {
  const needle = filter.trim().toLowerCase();
  if (!needle) return true;
  return project.name.toLowerCase().includes(needle);
}
