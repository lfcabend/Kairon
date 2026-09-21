import { useMutation, useQueryClient } from "@tanstack/react-query";

import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { authApi } from "@/lib/api/auth";
import type { AssistantPreferences, Me } from "@/lib/api/types";

// Radix's Select.Item can't take value="" (it's reserved for "no selection"
// internally), so the "use the instance default" choice gets its own sentinel.
const INSTANCE_DEFAULT = "default";

const MODEL_OPTIONS = [
  { value: INSTANCE_DEFAULT, label: "Instance default" },
  { value: "claude-sonnet-5", label: "Claude Sonnet 5" },
  { value: "claude-opus-5", label: "Claude Opus 5" },
  { value: "claude-haiku-4-5", label: "Claude Haiku 4.5" },
];

function currentAssistantPrefs(me: Me | undefined): AssistantPreferences {
  const raw = (me?.preferences as { assistant?: Partial<AssistantPreferences> } | undefined)?.assistant;
  return {
    todoSuggestions: { enabled: raw?.todoSuggestions?.enabled ?? false },
    executionSummaries: { enabled: raw?.executionSummaries?.enabled ?? false },
    journalReflection: { enabled: raw?.journalReflection?.enabled ?? false },
    modelOverride: raw?.modelOverride ?? null,
    tone: raw?.tone ?? "balanced",
  };
}

/**
 * Only the todo-suggestions feature is implemented as of M8 — execution
 * summaries and journal reflection (M9/M10) don't exist yet, so this panel
 * deliberately doesn't offer toggles for them (a control for a feature that
 * does nothing would just be misleading). Same read-modify-write `PATCH /me`
 * pattern the existing rollover setting on this page already uses.
 */
export function AssistantSettings({ me }: { me: Me | undefined }) {
  const queryClient = useQueryClient();
  const prefs = currentAssistantPrefs(me);

  const mutation = useMutation({
    mutationFn: (patch: Partial<AssistantPreferences>) =>
      authApi.updateMe({
        preferences: {
          ...(me?.preferences ?? {}),
          assistant: { ...prefs, ...patch },
        },
      }),
    onSuccess: (updated) => queryClient.setQueryData(["me"], updated),
  });

  return (
    <section className="rounded-lg border bg-card p-6 shadow-sm">
      <h2 className="text-lg font-semibold tracking-tight">Assistant</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Optional, off by default. When enabled, your open project tasks, recent todo
        history, and recent journal entries are sent to Anthropic to generate
        suggestions — nothing is ever created without you explicitly accepting it.
      </p>

      <label className="mt-4 flex items-start gap-3 text-sm">
        <Checkbox
          className="mt-0.5"
          checked={prefs.todoSuggestions.enabled}
          disabled={mutation.isPending}
          onCheckedChange={(checked) =>
            mutation.mutate({ todoSuggestions: { enabled: checked === true } })
          }
        />
        <span>
          <span className="font-medium">Todo suggestions</span>
          <span className="block text-xs text-muted-foreground">
            Show a "Suggest todos" option on Today and the day view.
          </span>
        </span>
      </label>

      <div className="mt-4 space-y-1.5">
        <Label>Model</Label>
        <Select
          value={prefs.modelOverride ?? INSTANCE_DEFAULT}
          onValueChange={(value) =>
            mutation.mutate({ modelOverride: value === INSTANCE_DEFAULT ? null : value })
          }
        >
          <SelectTrigger aria-label="Model" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {MODEL_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {mutation.isError && (
        <p className="mt-2 text-sm text-destructive" role="alert">
          Could not save. Try again.
        </p>
      )}
    </section>
  );
}
