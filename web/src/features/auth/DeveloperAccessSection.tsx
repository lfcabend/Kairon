import { useState } from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

import {
  useIssuePersonalAccessToken,
  usePersonalAccessTokens,
  useRevokePersonalAccessToken,
} from "./usePersonalAccessTokens";

interface FormValues {
  name: string;
}

/**
 * "Developer access" — generate, list, and revoke a personal access token
 * (PAT) for the MCP server to use (docs/milestones/M10.5-mcp-server.md §7).
 * A newly generated token's raw value is shown exactly once, in a monospace
 * block with copy-to-clipboard; it never appears again on any later read —
 * the list below only ever shows name/created/last-used/expires/revoked.
 */
export function DeveloperAccessSection() {
  const tokensQuery = usePersonalAccessTokens();
  const issueMutation = useIssuePersonalAccessToken();
  const revokeMutation = useRevokePersonalAccessToken();
  const [revealedToken, setRevealedToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [confirmingRevokeId, setConfirmingRevokeId] = useState<string | null>(null);

  const form = useForm<FormValues>({ defaultValues: { name: "" } });

  const onGenerate = form.handleSubmit(async (values) => {
    // mutateAsync rejects on failure — caught here (rather than left to the
    // mutation's own isError state alone) so a failed generate never surfaces
    // as an unhandled promise rejection; issueMutation.isError still drives
    // the inline error message below.
    let issued;
    try {
      issued = await issueMutation.mutateAsync({ name: values.name });
    } catch {
      return;
    }
    setRevealedToken(issued.token);
    setCopied(false);
    form.reset({ name: "" });
  });

  const copyToken = async () => {
    if (!revealedToken) return;
    try {
      await navigator.clipboard.writeText(revealedToken);
      setCopied(true);
    } catch {
      // Clipboard access can fail (permissions, insecure context) — the raw
      // value is still visible to select/copy by hand, so this is non-fatal.
    }
  };

  return (
    <section className="rounded-lg border bg-card p-6 shadow-sm">
      <h2 className="text-lg font-semibold tracking-tight">Developer access</h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Generate a personal access token to connect a remote MCP client (e.g. Claude Code) —
        it can read your todos, journal, and projects, and submit assistant suggestions for
        your review, but can't change anything else in your account.
      </p>

      {revealedToken && (
        <div className="mt-4 rounded-md border border-amber-300 bg-amber-50 p-4 dark:border-amber-800 dark:bg-amber-950">
          <p className="text-sm font-medium">Shown once — copy it now. You won't see it again.</p>
          <div className="mt-2 flex items-center gap-2">
            <code className="flex-1 overflow-x-auto rounded bg-background px-2 py-1.5 text-xs">
              {revealedToken}
            </code>
            <Button type="button" size="sm" variant="outline" onClick={() => void copyToken()}>
              {copied ? "Copied" : "Copy"}
            </Button>
          </div>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            className="mt-2"
            onClick={() => setRevealedToken(null)}
          >
            Done
          </Button>
        </div>
      )}

      <form onSubmit={(e) => void onGenerate(e)} className="mt-4 flex items-end gap-3" noValidate>
        <div className="flex-1 space-y-1.5">
          <Label htmlFor="pat-name">Name</Label>
          <Input
            id="pat-name"
            placeholder="e.g. Claude Code (phone)"
            {...form.register("name", { required: true, maxLength: 100 })}
          />
        </div>
        <Button type="submit" disabled={issueMutation.isPending}>
          {issueMutation.isPending ? "Generating…" : "Generate token"}
        </Button>
      </form>
      {issueMutation.isError && (
        <p className="mt-2 text-sm text-destructive" role="alert">
          Could not generate a token. Try again.
        </p>
      )}

      <ul className="mt-4 divide-y">
        {(tokensQuery.data ?? []).map((token) => (
          <li key={token.id} className="flex items-center justify-between gap-3 py-3 text-sm">
            <div>
              <p className="font-medium">{token.name}</p>
              <p className="text-xs text-muted-foreground">
                Created {new Date(token.createdAt).toLocaleDateString()}
                {token.lastUsedAt
                  ? ` · last used ${new Date(token.lastUsedAt).toLocaleDateString()}`
                  : " · never used"}
                {token.expiresAt && ` · expires ${new Date(token.expiresAt).toLocaleDateString()}`}
              </p>
            </div>
            {confirmingRevokeId === token.id ? (
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">Revoke?</span>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="text-destructive"
                  disabled={revokeMutation.isPending}
                  onClick={() => {
                    revokeMutation.mutate(token.id);
                    setConfirmingRevokeId(null);
                  }}
                >
                  Confirm
                </Button>
                <Button type="button" size="sm" variant="ghost" onClick={() => setConfirmingRevokeId(null)}>
                  Cancel
                </Button>
              </div>
            ) : (
              <Button type="button" size="sm" variant="outline" onClick={() => setConfirmingRevokeId(token.id)}>
                Revoke
              </Button>
            )}
          </li>
        ))}
        {tokensQuery.data?.length === 0 && (
          <li className="py-3 text-sm text-muted-foreground">No personal access tokens yet.</li>
        )}
      </ul>
    </section>
  );
}
