import { type ReactNode, useEffect, useState } from "react";

import { tryRefreshSession } from "@/lib/api/client";

/**
 * Bootstraps the session on a full page load: the access token is gone (memory
 * only) but the refresh cookie may still be valid, so we try one silent refresh
 * before rendering the app. Children only mount once that has resolved, so
 * protected routes never flash the login screen for an already-signed-in user.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    let active = true;
    void tryRefreshSession().finally(() => {
      if (active) setInitializing(false);
    });
    return () => {
      active = false;
    };
  }, []);

  if (initializing) {
    return (
      <div
        className="flex min-h-screen items-center justify-center bg-background text-muted-foreground"
        data-testid="auth-bootstrapping"
      >
        Loading…
      </div>
    );
  }

  return <>{children}</>;
}
