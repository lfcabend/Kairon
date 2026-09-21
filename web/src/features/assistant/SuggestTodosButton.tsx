import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import type { AssistantSuggestedTask, Horizon } from "@/lib/api/types";
import { formatShortDate } from "@/lib/date";

import { SuggestedTaskList } from "./SuggestedTaskList";
import { useSuggestTodos } from "./useAssistant";

/**
 * A two-step dialog: choose day-only vs. week-ahead, then review the
 * returned suggestions in place. Used on both `TodayPage` and `DayView`
 * (M8 D14) — `day` comes from whichever screen is showing, same as
 * `DueTasksPanel`'s `date` prop.
 */
export function SuggestTodosButton({ day }: { day: string }) {
  const [open, setOpen] = useState(false);
  const [horizon, setHorizon] = useState<Horizon>("DAY");
  const [suggestions, setSuggestions] = useState<AssistantSuggestedTask[] | null>(null);
  const suggest = useSuggestTodos();

  const handleOpenChange = (next: boolean) => {
    setOpen(next);
    if (!next) {
      suggest.reset();
      setSuggestions(null);
      setHorizon("DAY");
    }
  };

  const handleSuggest = () => {
    suggest.mutate(
      { day, horizon },
      { onSuccess: (run) => setSuggestions(run.suggestions) },
    );
  };

  const handleResolved = (id: string) => {
    setSuggestions((prev) => prev?.filter((s) => s.id !== id) ?? null);
  };

  return (
    <>
      <Button variant="outline" size="sm" onClick={() => handleOpenChange(true)}>
        Suggest todos
      </Button>
      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Suggest todos</DialogTitle>
            <DialogDescription>
              {suggestions === null
                ? "Kairon will look at your open project tasks, recent todo history, and recent journal entries."
                : "Accept adds a suggestion to your list; dismiss discards it."}
            </DialogDescription>
          </DialogHeader>

          {suggestions === null ? (
            <>
              <RadioGroup value={horizon} onValueChange={(v) => setHorizon(v as Horizon)}>
                <label className="flex items-center gap-2 text-sm">
                  <RadioGroupItem value="DAY" /> Just {formatShortDate(day)}
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <RadioGroupItem value="WEEK" /> This week
                </label>
              </RadioGroup>
              {suggest.isError && (
                <p className="text-sm text-destructive" role="alert">
                  {suggest.error.message || "Could not get suggestions."}
                </p>
              )}
              <DialogFooter>
                <Button variant="outline" onClick={() => handleOpenChange(false)}>
                  Cancel
                </Button>
                <Button disabled={suggest.isPending} onClick={handleSuggest}>
                  {suggest.isPending ? "Thinking…" : "Suggest"}
                </Button>
              </DialogFooter>
            </>
          ) : (
            <>
              <SuggestedTaskList suggestions={suggestions} onResolved={handleResolved} />
              <DialogFooter>
                <Button onClick={() => handleOpenChange(false)}>Done</Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
