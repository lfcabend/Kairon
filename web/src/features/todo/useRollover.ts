import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { toast } from "sonner";

import { todoApi } from "@/lib/api/todo";
import type { RolloverBody, RolloverMode, RolloverPreview } from "@/lib/api/types";

import { todoKeys } from "./todoKeys";

export function useRolloverPreview(date: string, enabled: boolean) {
  return useQuery({
    queryKey: todoKeys.rolloverPreview(date),
    queryFn: () => todoApi.rolloverPreview(date),
    enabled,
  });
}

function useInvalidateTodos() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: todoKeys.all });
}

export function useRollover() {
  const invalidate = useInvalidateTodos();
  return useMutation({
    mutationFn: (body: RolloverBody) => todoApi.rollover(body),
    onSuccess: invalidate,
  });
}

export function useRolloverUndo() {
  const invalidate = useInvalidateTodos();
  return useMutation({
    mutationFn: (createdIds: string[]) => todoApi.rolloverUndo({ createdIds }),
    onSuccess: invalidate,
  });
}

/**
 * `auto` mode: on the first mount of the day view for *today* with a non-empty
 * preview, sweep everything and offer an Undo toast. Idempotent — after the roll
 * the sources are CANCELLED so the next preview is empty (docs/milestones/M2 §6.6).
 */
export function useAutoRollover(params: {
  mode: RolloverMode;
  isToday: boolean;
  today: string;
  preview: RolloverPreview | undefined;
}) {
  const { mode, isToday, today, preview } = params;
  const rollover = useRollover();
  const undo = useRolloverUndo();
  const ranRef = useRef(false);

  useEffect(() => {
    if (ranRef.current) return;
    if (mode !== "auto" || !isToday) return;
    if (!preview || preview.totalItems === 0) return;

    ranRef.current = true;
    rollover.mutate(
      { toDay: today },
      {
        onSuccess: (res) => {
          const ids = res.rolledOver.map((t) => t.id);
          const days = preview.sourceDays.length;
          toast(
            `Rolled ${ids.length} item${ids.length === 1 ? "" : "s"} from ${days} earlier day${
              days === 1 ? "" : "s"
            }`,
            {
              action: {
                label: "Undo",
                onClick: () => undo.mutate(ids),
              },
            },
          );
        },
        // A failed sweep is left alone rather than retried on every re-render;
        // the user can switch to a manual mode or reload.
      },
    );
  }, [mode, isToday, today, preview, rollover, undo]);
}
