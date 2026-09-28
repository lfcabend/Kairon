import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, test } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";

import { seedAssistantRun } from "@/test/msw/handlers";

import { useAssistantRun } from "./useAssistantRuns";

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  useAuthStore.setState({
    accessToken: "access-1",
    user: { id: "u1", email: "ada@example.com", displayName: "Ada", timezone: "UTC" },
  });
});

test("polls while PENDING/RUNNING and stops once the run resolves to SUCCEEDED", async () => {
  const run = seedAssistantRun({ kind: "WEEKLY_SUMMARY", status: "PENDING" });

  const { result } = renderHook(() => useAssistantRun(run.id, { poll: true }), { wrapper });

  await waitFor(() => expect(result.current.data?.status).toBe("PENDING"));

  // The MSW handler flips this run to SUCCEEDED starting its 2nd poll — the
  // hook's own refetchInterval is what drives that 2nd request to happen.
  await waitFor(() => expect(result.current.data?.status).toBe("SUCCEEDED"), { timeout: 5000 });
  expect(result.current.data?.outputMarkdown).toBeTruthy();
}, 10000);

test("with poll:false, fetches once and never transitions past the first response", async () => {
  const run = seedAssistantRun({ kind: "WEEKLY_SUMMARY", status: "PENDING" });

  const { result } = renderHook(() => useAssistantRun(run.id, { poll: false }), { wrapper });

  await waitFor(() => expect(result.current.data?.status).toBe("PENDING"));
  await new Promise((resolve) => setTimeout(resolve, 500));
  expect(result.current.data?.status).toBe("PENDING");
});
