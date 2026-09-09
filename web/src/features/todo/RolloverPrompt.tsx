import { useState } from "react";

import { Button } from "@/components/ui/button";
import type { RolloverMode, RolloverPreview } from "@/lib/api/types";
import { formatShortDate } from "@/lib/date";

import { RolloverPickerDialog } from "./RolloverPickerDialog";
import { useRollover } from "./useRollover";

interface Props {
  preview: RolloverPreview;
  mode: RolloverMode;
  toDay: string;
}

/** manual / pick banner. Hidden in auto mode (handled by useAutoRollover). */
export function RolloverPrompt({ preview, mode, toDay }: Props) {
  const rollover = useRollover();
  const [pickerOpen, setPickerOpen] = useState(false);

  if (mode === "auto" || preview.totalItems === 0) return null;

  const days = preview.sourceDays.length;
  const message =
    days === 1
      ? `From ${formatShortDate(preview.sourceDays[0].day)}: ${preview.totalItems} unfinished`
      : `${preview.totalItems} unfinished from ${days} earlier days`;

  return (
    <div className="flex items-center justify-between rounded-md border border-dashed bg-muted/40 px-3 py-2 text-sm">
      <span>{message}</span>
      <div className="flex gap-2">
        <Button
          size="sm"
          disabled={rollover.isPending}
          onClick={() => {
            if (mode === "pick") setPickerOpen(true);
            else rollover.mutate({ toDay });
          }}
        >
          {rollover.isPending ? "Rolling over…" : "Roll over"}
        </Button>
      </div>

      {mode === "pick" && (
        <RolloverPickerDialog
          open={pickerOpen}
          onOpenChange={setPickerOpen}
          preview={preview}
          pending={rollover.isPending}
          onConfirm={(ids) =>
            rollover.mutate({ toDay, ids }, { onSuccess: () => setPickerOpen(false) })
          }
        />
      )}
    </div>
  );
}
