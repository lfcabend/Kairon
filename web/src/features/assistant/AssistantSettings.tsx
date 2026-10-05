import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

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
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { authApi } from "@/lib/api/auth";
import type { AssistantPreferences, Me } from "@/lib/api/types";

const TONE_OPTIONS = [
  { value: "encouraging", label: "Encouraging" },
  { value: "balanced", label: "Balanced" },
  { value: "direct", label: "Direct" },
];

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
    projectGeneration: { enabled: raw?.projectGeneration?.enabled ?? false },
    modelOverride: raw?.modelOverride ?? null,
    tone: raw?.tone ?? "balanced",
  };
}

/**
 * Todo suggestions (M8), project generation (M8.5), execution summaries (M9),
 * and journal reflection (M10) are implemented. Same read-modify-write
 * `PATCH /me` pattern the existing rollover setting on this page already uses.
 * Journal reflection's checkbox is the one exception: turning it **on** opens
 * a confirmation dialog first (M10 D13) — it's the one feature that sends full
 * journal entry text, not a summary, off the instance — and only commits the
 * `PATCH` once the user explicitly confirms there. Turning it off, like every
 * other toggle here, needs no confirmation.
 */
export function AssistantSettings({ me }: { me: Me | undefined }) {
  const queryClient = useQueryClient();
  const prefs = currentAssistantPrefs(me);
  const [confirmingJournalReflection, setConfirmingJournalReflection] = useState(false);

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

      <label className="mt-4 flex items-start gap-3 text-sm">
        <Checkbox
          className="mt-0.5"
          checked={prefs.projectGeneration.enabled}
          disabled={mutation.isPending}
          onCheckedChange={(checked) =>
            mutation.mutate({ projectGeneration: { enabled: checked === true } })
          }
        />
        <span>
          <span className="font-medium">Project generation</span>
          <span className="block text-xs text-muted-foreground">
            Show a "New project from description" option on the project list. Kairon
            sends your project description and dates to Anthropic when you generate a plan.
          </span>
        </span>
      </label>

      <label className="mt-4 flex items-start gap-3 text-sm">
        <Checkbox
          className="mt-0.5"
          checked={prefs.executionSummaries.enabled}
          disabled={mutation.isPending}
          onCheckedChange={(checked) =>
            mutation.mutate({ executionSummaries: { enabled: checked === true } })
          }
        />
        <span>
          <span className="font-medium">Execution summaries</span>
          <span className="block text-xs text-muted-foreground">
            Auto-generate a weekly and monthly summary of your todo and project activity
            (also available on demand from the Summaries page).
          </span>
        </span>
      </label>

      <label className="mt-4 flex items-start gap-3 text-sm">
        <Checkbox
          className="mt-0.5"
          checked={prefs.journalReflection.enabled}
          disabled={mutation.isPending}
          onCheckedChange={(checked) => {
            if (checked === true) {
              setConfirmingJournalReflection(true);
            } else {
              mutation.mutate({ journalReflection: { enabled: false } });
            }
          }}
        />
        <span>
          <span className="font-medium">Journal reflection</span>
          <span className="block text-xs text-muted-foreground">
            Show a "Generate weekly reflection" option on the Summaries page. Sends your
            full journal entry text for the selected week to Anthropic.
          </span>
        </span>
      </label>

      <div className="mt-4 space-y-1.5">
        <Label>Tone</Label>
        <Select
          value={prefs.tone}
          onValueChange={(value) => mutation.mutate({ tone: value })}
        >
          <SelectTrigger aria-label="Tone" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {TONE_OPTIONS.map((opt) => (
              <SelectItem key={opt.value} value={opt.value}>
                {opt.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <p className="text-xs text-muted-foreground">Used by journal reflection's narrative voice.</p>
      </div>

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

      <Dialog open={confirmingJournalReflection} onOpenChange={setConfirmingJournalReflection}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Enable journal reflection?</DialogTitle>
            <DialogDescription>
              Journal reflection sends your journal entries' <strong>full text</strong> for
              the selected week to Anthropic, not just a summary. This is different from
              Kairon's other assistant features, which only send task and project details.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmingJournalReflection(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => {
                mutation.mutate({ journalReflection: { enabled: true } });
                setConfirmingJournalReflection(false);
              }}
            >
              Enable journal reflection
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </section>
  );
}
