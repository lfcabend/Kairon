// Tiny date helpers for the day view. We only need ±N days on an ISO date and
// locale formatting, so a date library would be overkill (docs/milestones/M2 §6.1).

/** Today as an ISO `YYYY-MM-DD` string in the given IANA timezone. */
export function todayInZone(timeZone: string): string {
  // `en-CA` renders as YYYY-MM-DD; the timeZone option does the shift for us.
  try {
    return new Intl.DateTimeFormat("en-CA", { timeZone }).format(new Date());
  } catch {
    return new Intl.DateTimeFormat("en-CA").format(new Date());
  }
}

/** ISO date shifted by `n` whole days (n may be negative). Pure string math via UTC. */
export function addDays(iso: string, n: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  date.setUTCDate(date.getUTCDate() + n);
  return date.toISOString().slice(0, 10);
}

/** e.g. "Wednesday, 9 September 2026". */
export function formatLongDate(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Intl.DateTimeFormat(undefined, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(y, m - 1, d)));
}

/** e.g. "Fri 5 Sep" — compact form for rollover source-day labels. */
export function formatShortDate(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Intl.DateTimeFormat(undefined, {
    weekday: "short",
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  }).format(new Date(Date.UTC(y, m - 1, d)));
}

/** True when `iso` is a valid `YYYY-MM-DD` calendar date. */
export function isValidIsoDate(iso: string | undefined): iso is string {
  if (!iso || !/^\d{4}-\d{2}-\d{2}$/.test(iso)) return false;
  const [y, m, d] = iso.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return (
    date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d
  );
}
