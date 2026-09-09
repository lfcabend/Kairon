import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { addDays, formatLongDate } from "@/lib/date";

interface Props {
  date: string;
  today: string;
  onNavigate: (date: string) => void;
}

/** ‹ › + long date + a "Today" button. Arrow-key handling lives in DayView. */
export function DateNav({ date, today, onNavigate }: Props) {
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
      {date !== today && (
        <Button variant="ghost" size="sm" onClick={() => onNavigate(today)}>
          Today
        </Button>
      )}
    </div>
  );
}
