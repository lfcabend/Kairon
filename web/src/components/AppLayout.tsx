import { useQuery } from "@tanstack/react-query";
import { NavLink, Outlet } from "react-router-dom";
import { Toaster } from "sonner";

import { authApi } from "@/lib/api/auth";
import type { Me } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const linkClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
    isActive ? "bg-accent text-accent-foreground" : "text-muted-foreground hover:text-foreground",
  );

/** The minimal app shell that wraps every protected route (docs/milestones/M2 D1). */
export function AppLayout() {
  const meQuery = useQuery<Me>({ queryKey: ["me"], queryFn: authApi.me });

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-1">
            <span className="mr-3 font-semibold tracking-tight">Kairon</span>
            <NavLink to="/day" className={linkClass}>
              Day
            </NavLink>
            <NavLink to="/journal" className={linkClass}>
              Journal
            </NavLink>
            <NavLink to="/projects" className={linkClass}>
              Projects
            </NavLink>
            <NavLink to="/account" className={linkClass}>
              Account
            </NavLink>
          </div>
          <span className="text-sm text-muted-foreground">{meQuery.data?.displayName ?? ""}</span>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-6">
        <Outlet />
      </main>
      <Toaster />
    </div>
  );
}
