/** Curated, visually distinct accent colors for categories/projects (tag-style, not data-viz). */
export const COLOR_PALETTE: readonly string[] = [
  "#6366f1", // indigo
  "#ec4899", // pink
  "#f97316", // orange
  "#22c55e", // green
  "#06b6d4", // cyan
  "#eab308", // yellow
  "#8b5cf6", // violet
  "#ef4444", // red
  "#14b8a6", // teal
  "#3b82f6", // blue
  "#f43f5e", // rose
  "#84cc16", // lime
  "#a855f7", // purple
  "#0ea5e9", // sky
  "#d946ef", // fuchsia
  "#f59e0b", // amber
];

/**
 * A random palette color not already in `used` (case-insensitive), so new
 * categories/projects don't all default to the same color. Falls back to any
 * random palette color once every color in the palette is already in use.
 */
export function pickUniqueColor(used: readonly string[]): string {
  const usedSet = new Set(used.map((c) => c.toLowerCase()));
  const available = COLOR_PALETTE.filter((c) => !usedSet.has(c.toLowerCase()));
  const pool = available.length > 0 ? available : COLOR_PALETTE;
  return pool[Math.floor(Math.random() * pool.length)];
}
