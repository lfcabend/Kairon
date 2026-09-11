import { Button } from "@/components/ui/button";

interface Props {
  value: number | null;
  onChange: (mood: number | null) => void;
}

/** Five-button 1–5 rating strip; clicking the active button clears it (Q3: numbered, not emoji, for M3). */
export function MoodPicker({ value, onChange }: Props) {
  return (
    <div className="flex items-center gap-1" role="group" aria-label="Mood">
      <span className="mr-1 text-xs text-muted-foreground">Mood</span>
      {[1, 2, 3, 4, 5].map((n) => (
        <Button
          key={n}
          type="button"
          size="icon"
          variant={value === n ? "default" : "outline"}
          className="h-6 w-6 text-xs"
          aria-pressed={value === n}
          aria-label={`Mood ${n}`}
          onClick={() => onChange(value === n ? null : n)}
        >
          {n}
        </Button>
      ))}
    </div>
  );
}
