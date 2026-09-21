import { Button } from "@/components/ui/button";
import type { AssistantSuggestedTask } from "@/lib/api/types";
import { formatShortDate } from "@/lib/date";

import { useAcceptSuggestedTask, useDismissSuggestedTask } from "./useAssistant";

interface Props {
  suggestions: AssistantSuggestedTask[];
  /** Called once a row's accept/dismiss succeeds, so the parent can drop it from the list shown. */
  onResolved: (id: string) => void;
}

/** The review-and-accept list for one run's suggestions (M8 §6.1). */
export function SuggestedTaskList({ suggestions, onResolved }: Props) {
  const accept = useAcceptSuggestedTask();
  const dismiss = useDismissSuggestedTask();

  if (suggestions.length === 0) {
    return <p className="text-sm text-muted-foreground">No suggestions right now.</p>;
  }

  return (
    <ul className="max-h-[50vh] space-y-2 overflow-y-auto">
      {suggestions.map((s) => (
        <SuggestedTaskRow
          key={s.id}
          suggestion={s}
          pending={
            (accept.isPending && accept.variables?.id === s.id) ||
            (dismiss.isPending && dismiss.variables === s.id)
          }
          error={
            (accept.isError && accept.variables?.id === s.id && accept.error) ||
            (dismiss.isError && dismiss.variables === s.id && dismiss.error) ||
            null
          }
          onAccept={() => accept.mutate({ id: s.id, day: s.suggestedForDay }, { onSuccess: () => onResolved(s.id) })}
          onDismiss={() => dismiss.mutate(s.id, { onSuccess: () => onResolved(s.id) })}
        />
      ))}
    </ul>
  );
}

function SuggestedTaskRow({
  suggestion,
  pending,
  error,
  onAccept,
  onDismiss,
}: {
  suggestion: AssistantSuggestedTask;
  pending: boolean;
  error: Error | null;
  onAccept: () => void;
  onDismiss: () => void;
}) {
  return (
    <li className="rounded-md border px-2.5 py-2 text-sm">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <p className="font-medium">{suggestion.title}</p>
          {suggestion.rationale && (
            <p className="text-xs text-muted-foreground">{suggestion.rationale}</p>
          )}
          <div className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
            <span>{formatShortDate(suggestion.suggestedForDay)}</span>
            {suggestion.estimateMinutes && <span>· ~{suggestion.estimateMinutes} min</span>}
          </div>
        </div>
        <div className="flex shrink-0 gap-1.5">
          <Button size="sm" variant="outline" disabled={pending} onClick={onDismiss}>
            Dismiss
          </Button>
          <Button size="sm" disabled={pending} onClick={onAccept}>
            {pending ? "…" : "Accept"}
          </Button>
        </div>
      </div>
      {error && (
        <p className="mt-1 text-xs text-destructive" role="alert">
          {error.message || "Could not update this suggestion."}
        </p>
      )}
    </li>
  );
}
