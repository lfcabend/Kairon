import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { KaironApiError, kaironFetch } from "./kaironClient.js";

describe("kaironFetch", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("maps a problem+json error body to a thrown error with the backend's own detail text", async () => {
    globalThis.fetch = vi.fn(
      async () =>
        new Response(JSON.stringify({ status: 403, detail: "You haven't enabled todo suggestions in Settings." }), {
          status: 403,
        }),
    ) as unknown as typeof fetch;

    await expect(kaironFetch("tok", "/assistant/mcp/todo-suggestions:context")).rejects.toMatchObject({
      message: "You haven't enabled todo suggestions in Settings.",
      status: 403,
    });
  });

  it("includes field errors from a validation failure's errors array", async () => {
    globalThis.fetch = vi.fn(
      async () =>
        new Response(
          JSON.stringify({ status: 400, detail: "Validation failed", errors: [{ field: "day", message: "must not be null" }] }),
          { status: 400 },
        ),
    ) as unknown as typeof fetch;

    await expect(kaironFetch("tok", "/todo")).rejects.toThrow(/day: must not be null/);
  });

  it("maps a network failure to a distinct, clearly-worded error", async () => {
    globalThis.fetch = vi.fn(async () => {
      throw new Error("ECONNREFUSED");
    }) as unknown as typeof fetch;

    await expect(kaironFetch("tok", "/todo")).rejects.toBeInstanceOf(KaironApiError);
    await expect(kaironFetch("tok", "/todo")).rejects.toThrow(/Could not reach the Kairon backend/);
  });

  it("forwards the bearer token verbatim and never caches it across calls", async () => {
    const calls: string[] = [];
    globalThis.fetch = vi.fn(async (_url, init?: RequestInit) => {
      calls.push((init?.headers as Record<string, string>).Authorization);
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    }) as unknown as typeof fetch;

    await kaironFetch("token-a", "/todo");
    await kaironFetch("token-b", "/todo");

    expect(calls).toEqual(["Bearer token-a", "Bearer token-b"]);
  });

  it("returns undefined for a 204 No Content response", async () => {
    globalThis.fetch = vi.fn(async () => new Response(null, { status: 204 })) as unknown as typeof fetch;

    await expect(kaironFetch("tok", "/me/personal-access-tokens/abc", { method: "DELETE" })).resolves.toBeUndefined();
  });
});
