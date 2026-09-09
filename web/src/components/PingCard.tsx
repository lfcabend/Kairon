import { useCallback, useEffect, useState } from "react";

import { Button } from "@/components/ui/button";

interface PingResult {
  pong: boolean;
  version: string;
}

type Status =
  | { state: "loading" }
  | { state: "ok"; data: PingResult }
  | { state: "error"; message: string };

async function fetchPing(): Promise<PingResult> {
  const res = await fetch("/api/v1/ping");
  if (!res.ok) {
    throw new Error(`ping failed: HTTP ${res.status}`);
  }
  return (await res.json()) as PingResult;
}

/** The M0 walking-skeleton probe, kept visible on the account screen. */
export function PingCard() {
  const [status, setStatus] = useState<Status>({ state: "loading" });

  const load = useCallback(() => {
    setStatus({ state: "loading" });
    fetchPing()
      .then((data) => setStatus({ state: "ok", data }))
      .catch((err: unknown) =>
        setStatus({
          state: "error",
          message: err instanceof Error ? err.message : String(err),
        }),
      );
  }, []);

  useEffect(load, [load]);

  return (
    <div className="rounded-md bg-muted p-4 text-sm" data-testid="ping-status">
      {status.state === "loading" && <span>Pinging the API…</span>}
      {status.state === "ok" && (
        <span>
          API is up: <strong>pong = {String(status.data.pong)}</strong>, version{" "}
          <strong>{status.data.version}</strong>
        </span>
      )}
      {status.state === "error" && (
        <span className="text-destructive">Could not reach the API: {status.message}</span>
      )}
      <Button variant="outline" size="sm" className="mt-3 w-full" onClick={load}>
        Ping again
      </Button>
    </div>
  );
}
