import { Calendar as CalendarIcon, ChevronLeft, ChevronRight } from "lucide-react";
import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { addDays, formatLongDate, isValidIsoDate } from "@/lib/date";

import { useEntryDays } from "./useJournal";

interface Props {
  date: string;
  today: string;
  onNavigate: (date: string) => void;
}

// `react-day-picker` reads/writes plain local-time `Date`s (it calls
// `.getFullYear()`/`.getMonth()`/`.getDate()` internally, not the UTC
// variants) — building these via `Date.UTC()` shifts the selected day by one
// in any timezone behind UTC, so this stays in local time throughout.
function isoToLocalDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

function localDateToIso(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

/** ‹ › + long date + "Today" + a calendar popover with has-entry dots (D2). */
export function JournalDateNav({ date, today, onNavigate }: Props) {
  const [open, setOpen] = useState(false);
  const [month, setMonth] = useState(() => isoToLocalDate(date));

  const monthStart = useMemo(
    () => localDateToIso(new Date(month.getFullYear(), month.getMonth(), 1)),
    [month],
  );
  const monthEnd = useMemo(
    () => localDateToIso(new Date(month.getFullYear(), month.getMonth() + 1, 0)),
    [month],
  );
  const entryDaysQuery = useEntryDays(monthStart, monthEnd);
  const entryDates = useMemo(
    () => (entryDaysQuery.data ?? []).map(isoToLocalDate),
    [entryDaysQuery.data],
  );

  return (
    <div className="flex items-center gap-2">
      <Button
        variant="outline"
        size="icon"
        aria-label="Previous day"
        onClick={() => onNavigate(addDays(date, -1))}
      >
        <ChevronLeft className="h-4 w-4" />
      </Button>
      <Button
        variant="outline"
        size="icon"
        aria-label="Next day"
        onClick={() => onNavigate(addDays(date, 1))}
      >
        <ChevronRight className="h-4 w-4" />
      </Button>
      <h1 className="text-lg font-semibold tracking-tight">{formatLongDate(date)}</h1>

      <Popover
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (next) setMonth(isoToLocalDate(date));
        }}
      >
        <PopoverTrigger asChild>
          <Button variant="outline" size="icon" aria-label="Open calendar">
            <CalendarIcon className="h-4 w-4" />
          </Button>
        </PopoverTrigger>
        <PopoverContent align="start">
          <Calendar
            mode="single"
            selected={isValidIsoDate(date) ? isoToLocalDate(date) : undefined}
            month={month}
            onMonthChange={setMonth}
            modifiers={{ hasEntry: entryDates }}
            onSelect={(day) => {
              if (!day) return;
              onNavigate(localDateToIso(day));
              setOpen(false);
            }}
          />
        </PopoverContent>
      </Popover>

      {date !== today && (
        <Button variant="ghost" size="sm" onClick={() => onNavigate(today)}>
          Today
        </Button>
      )}
    </div>
  );
}
