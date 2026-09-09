import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api/client";
import { authApi } from "@/lib/api/auth";

import { AuthLayout } from "./AuthLayout";
import { useAuthStore } from "./authStore";

const schema = z.object({
  displayName: z.string().min(1, "Display name is required").max(80),
  email: z.string().min(1, "Email is required").email("Enter a valid email"),
  password: z
    .string()
    .min(10, "Use at least 10 characters")
    .max(200, "That is too long"),
});

type FormValues = z.infer<typeof schema>;

const guessedTimezone = (): string | undefined => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined;
  } catch {
    return undefined;
  }
};

export default function RegisterPage() {
  const navigate = useNavigate();
  const { accessToken, setSession } = useAuthStore();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  if (accessToken) {
    return <Navigate to="/" replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      const result = await authApi.register({ ...values, timezone: guessedTimezone() });
      setSession(result.accessToken, result.user);
      navigate("/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError("email", { message: "An account with that email already exists." });
        return;
      }
      setError("root", { message: "Could not create the account. Please try again." });
    }
  });

  return (
    <AuthLayout
      title="Create account"
      subtitle="Start running your day with Kairon."
      footer={
        <>
          Already have an account?{" "}
          <Link className="font-medium text-primary hover:underline" to="/login">
            Sign in
          </Link>
        </>
      }
    >
      <form className="space-y-4" onSubmit={onSubmit} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="displayName">Display name</Label>
          <Input id="displayName" autoComplete="name" {...register("displayName")} />
          {errors.displayName && (
            <p className="text-xs text-destructive">{errors.displayName.message}</p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="email">Email</Label>
          <Input id="email" type="email" autoComplete="email" {...register("email")} />
          {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            {...register("password")}
          />
          {errors.password && (
            <p className="text-xs text-destructive">{errors.password.message}</p>
          )}
        </div>
        {errors.root && (
          <p className="text-sm text-destructive" role="alert">
            {errors.root.message}
          </p>
        )}
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? "Creating…" : "Create account"}
        </Button>
      </form>
    </AuthLayout>
  );
}
