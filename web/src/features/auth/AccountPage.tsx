import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { z } from "zod";

import { PingCard } from "@/components/PingCard";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { authApi } from "@/lib/api/auth";
import type { Me } from "@/lib/api/types";

import { useAuthStore } from "./authStore";

const schema = z.object({
  displayName: z.string().min(1, "Display name is required").max(80),
  timezone: z.string().min(1, "Timezone is required").max(64),
});

type FormValues = z.infer<typeof schema>;

export default function AccountPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const clearSession = useAuthStore((s) => s.clearSession);

  const meQuery = useQuery<Me>({ queryKey: ["me"], queryFn: authApi.me });

  const form = useForm<FormValues>({ resolver: zodResolver(schema) });
  const { reset } = form;

  useEffect(() => {
    if (meQuery.data) {
      reset({ displayName: meQuery.data.displayName, timezone: meQuery.data.timezone });
    }
  }, [meQuery.data, reset]);

  const updateMutation = useMutation({
    mutationFn: authApi.updateMe,
    onSuccess: (updated) => queryClient.setQueryData(["me"], updated),
  });

  const signOut = async (everywhere: boolean) => {
    try {
      await (everywhere ? authApi.logoutAll() : authApi.logout());
    } finally {
      clearSession();
      queryClient.clear();
      navigate("/login", { replace: true });
    }
  };

  return (
    <main className="mx-auto max-w-lg space-y-6 p-6">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight">Your account</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {meQuery.data ? meQuery.data.email : "Loading…"}
        </p>
      </header>

      <section className="rounded-lg border bg-card p-6 shadow-sm">
        <form
          className="space-y-4"
          onSubmit={form.handleSubmit((values) => updateMutation.mutate(values))}
          noValidate
        >
          <div className="space-y-1.5">
            <Label htmlFor="displayName">Display name</Label>
            <Input id="displayName" {...form.register("displayName")} />
            {form.formState.errors.displayName && (
              <p className="text-xs text-destructive">
                {form.formState.errors.displayName.message}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="timezone">Timezone</Label>
            <Input id="timezone" {...form.register("timezone")} />
            {form.formState.errors.timezone && (
              <p className="text-xs text-destructive">
                {form.formState.errors.timezone.message}
              </p>
            )}
          </div>
          {updateMutation.isError && (
            <p className="text-sm text-destructive" role="alert">
              Could not save. Check the timezone and try again.
            </p>
          )}
          {updateMutation.isSuccess && !form.formState.isDirty && (
            <p className="text-sm text-muted-foreground" role="status">
              Saved.
            </p>
          )}
          <Button type="submit" disabled={updateMutation.isPending}>
            {updateMutation.isPending ? "Saving…" : "Save changes"}
          </Button>
        </form>
      </section>

      <section className="flex gap-3">
        <Button variant="outline" onClick={() => void signOut(false)}>
          Log out
        </Button>
        <Button variant="ghost" onClick={() => void signOut(true)}>
          Log out everywhere
        </Button>
      </section>

      <PingCard />
    </main>
  );
}
