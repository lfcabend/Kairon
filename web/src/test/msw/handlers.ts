import { HttpResponse, http } from "msw";

import type { AuthResponse, Me } from "@/lib/api/types";

export const testUser = {
  id: "018f5b3e-0000-7000-8000-0000000000aa",
  email: "ada@example.com",
  displayName: "Ada",
  timezone: "UTC",
};

export const authResponse = (accessToken: string): AuthResponse => ({
  accessToken,
  tokenType: "Bearer",
  expiresInSeconds: 900,
  user: testUser,
});

export const meResponse: Me = {
  ...testUser,
  status: "ACTIVE",
  preferences: {},
};

const problem = (status: number, detail: string) =>
  HttpResponse.json({ status, detail }, { status });

/**
 * Baseline happy-path handlers. Individual tests narrow behaviour with
 * `server.use(...)`.
 */
export const handlers = [
  http.get("/api/v1/ping", () => HttpResponse.json({ pong: true, version: "test" })),

  http.post("/api/v1/auth/register", () =>
    HttpResponse.json(authResponse("access-registered"), { status: 201 }),
  ),

  http.post("/api/v1/auth/login", async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };
    if (body.password === "wrong-password") {
      return problem(401, "Invalid email or password.");
    }
    return HttpResponse.json(authResponse("access-login"));
  }),

  http.post("/api/v1/auth/refresh", () => problem(401, "Missing refresh token.")),

  http.post("/api/v1/auth/logout", () => new HttpResponse(null, { status: 204 })),
  http.post("/api/v1/auth/logout-all", () => new HttpResponse(null, { status: 204 })),

  http.get("/api/v1/me", ({ request }) => {
    if (request.headers.get("Authorization")?.startsWith("Bearer ")) {
      return HttpResponse.json(meResponse);
    }
    return problem(401, "Authentication required.");
  }),

  http.patch("/api/v1/me", async ({ request }) => {
    if (!request.headers.get("Authorization")?.startsWith("Bearer ")) {
      return problem(401, "Authentication required.");
    }
    const patch = (await request.json()) as Partial<Me>;
    return HttpResponse.json({ ...meResponse, ...patch });
  }),
];
