import { useQuery } from "@tanstack/react-query";

import { aboutApi } from "@/lib/api/about";
import type { AboutInfo } from "@/lib/api/types";

function formatInstant(value: string | undefined): string {
  if (!value || value === "unknown") return "Unknown";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

interface Row {
  label: string;
  value: string;
}

function rows(info: AboutInfo | undefined): Row[] {
  const build = info?.build;
  const deploy = info?.deploy;
  return [
    { label: "Version", value: build?.version ?? "Unknown" },
    { label: "Commit", value: build?.commit ?? "Unknown" },
    { label: "Built", value: formatInstant(build?.time) },
    { label: "Image", value: deploy?.image ?? "Unknown" },
    { label: "Deployed", value: formatInstant(deploy?.deployedAt) },
  ];
}

export default function AboutPage() {
  const infoQuery = useQuery<AboutInfo>({ queryKey: ["about"], queryFn: aboutApi.info });

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">About Kairon</h1>
        <p className="mt-1 text-sm text-muted-foreground">What's actually running.</p>
      </header>

      <section className="rounded-lg border bg-card p-6 shadow-sm" data-testid="about-info">
        {infoQuery.isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
        {infoQuery.isError && (
          <p className="text-sm text-destructive" role="alert">
            Could not load build info.
          </p>
        )}
        {infoQuery.data && (
          <dl className="divide-y">
            {rows(infoQuery.data).map((row) => (
              <div key={row.label} className="flex items-center justify-between gap-4 py-2 text-sm">
                <dt className="text-muted-foreground">{row.label}</dt>
                <dd className="break-all text-right font-mono">{row.value}</dd>
              </div>
            ))}
          </dl>
        )}
      </section>
    </div>
  );
}
