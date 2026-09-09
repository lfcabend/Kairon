import { useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { RolloverPreview } from "@/lib/api/types";
import { formatShortDate } from "@/lib/date";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  preview: RolloverPreview;
  pending: boolean;
  onConfirm: (ids: string[]) => void;
}

/** "pick" mode: a checkbox list grouped by source day, with select-all per day. */
export function RolloverPickerDialog({ open, onOpenChange, preview, pending, onConfirm }: Props) {
  const allIds = useMemo(
    () => preview.sourceDays.flatMap((d) => d.items.map((i) => i.id)),
    [preview],
  );
  const [checked, setChecked] = useState<Set<string>>(() => new Set(allIds));

  const toggle = (id: string) =>
    setChecked((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  const toggleDay = (ids: string[], all: boolean) =>
    setChecked((prev) => {
      const next = new Set(prev);
      ids.forEach((id) => (all ? next.delete(id) : next.add(id)));
      return next;
    });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Roll over unfinished tasks</DialogTitle>
          <DialogDescription>Pick which tasks to carry to today.</DialogDescription>
        </DialogHeader>

        <div className="max-h-[50vh] space-y-4 overflow-y-auto">
          {preview.sourceDays.map((group) => {
            const ids = group.items.map((i) => i.id);
            const allChecked = ids.every((id) => checked.has(id));
            return (
              <div key={group.day} className="space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">{formatShortDate(group.day)}</span>
                  <button
                    type="button"
                    className="text-xs text-muted-foreground hover:text-foreground"
                    onClick={() => toggleDay(ids, allChecked)}
                  >
                    {allChecked ? "Clear" : "Select all"}
                  </button>
                </div>
                {group.items.map((item) => (
                  <label key={item.id} className="flex items-center gap-2 text-sm">
                    <Checkbox
                      checked={checked.has(item.id)}
                      onCheckedChange={() => toggle(item.id)}
                    />
                    <span>{item.title}</span>
                  </label>
                ))}
              </div>
            );
          })}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            disabled={pending || checked.size === 0}
            onClick={() => onConfirm([...checked])}
          >
            Roll over {checked.size}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
