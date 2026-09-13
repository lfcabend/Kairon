import { HttpResponse, http } from "msw";
import { expect, test } from "vitest";

import { useAuthStore } from "@/features/auth/authStore";
import { authResponse, testUser } from "@/test/msw/handlers";
import { server } from "@/test/msw/server";

import { ApiError, apiFetch } from "./client";

test("a 401 triggers a refresh and one retry of the original request", async () => {
  useAuthStore.getState().setSession("stale-token", testUser);
  let meCalls = 0;

  server.use(
    http.get("/kairon/api/v1/me", ({ request }) => {
      meCalls += 1;
      const token = request.headers.get("Authorization");
      if (token === "Bearer fresh-token") {
        return HttpResponse.json({ email: "ada@example.com" });
      }
      return HttpResponse.json({ status: 401, detail: "expired" }, { status: 401 });
    }),
    http.post("/kairon/api/v1/auth/refresh", () => HttpResponse.json(authResponse("fresh-token"))),
  );

  const result = await apiFetch<{ email: string }>("/me");

  expect(result.email).toBe("ada@example.com");
  expect(meCalls).toBe(2);
  expect(useAuthStore.getState().accessToken).toBe("fresh-token");
});

test("a failed refresh clears the session and surfaces the 401", async () => {
  useAuthStore.getState().setSession("stale-token", testUser);

  server.use(
    http.get("/kairon/api/v1/me", () =>
      HttpResponse.json({ status: 401, detail: "expired" }, { status: 401 }),
    ),
    http.post("/kairon/api/v1/auth/refresh", () =>
      HttpResponse.json({ status: 401, detail: "no cookie" }, { status: 401 }),
    ),
  );

  await expect(apiFetch("/me")).rejects.toMatchObject({ status: 401 });
  expect(useAuthStore.getState().accessToken).toBeNull();
});

test("problem+json bodies become ApiError with the detail message", async () => {
  server.use(
    http.post("/kairon/api/v1/auth/register", () =>
      HttpResponse.json(
        { status: 409, detail: "An account with that email already exists." },
        { status: 409 },
      ),
    ),
  );

  const err = await apiFetch("/auth/register", {
    method: "POST",
    body: {},
    auth: false,
  }).catch((e: unknown) => e);

  expect(err).toBeInstanceOf(ApiError);
  expect((err as ApiError).status).toBe(409);
  expect((err as ApiError).message).toBe("An account with that email already exists.");
});
