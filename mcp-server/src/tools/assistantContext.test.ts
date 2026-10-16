import { afterEach, describe, expect, it, vi } from "vitest";

import { registerAssistantContextTools } from "./assistantContext.js";
import { fakeMcpServer } from "./testHarness.js";

describe("assistantContext tools", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("get_todo_suggestion_context calls the matching Kairon endpoint with a mocked fetch", async () => {
    const fetchMock = vi.fn(
      async () => new Response(JSON.stringify({ systemPrompt: "sys", userContent: "user" }), { status: 200 }),
    );
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const { server, tools } = fakeMcpServer();
    registerAssistantContextTools(server, "pat-123");

    const result = await tools.get("get_todo_suggestion_context")!.handler({ day: "2026-09-21", horizon: "DAY" });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: expect.stringContaining("/assistant/mcp/todo-suggestions:context") }),
      expect.objectContaining({ method: "POST" }),
    );
    const payload = JSON.parse(result.content[0].text);
    expect(payload.systemPrompt).toBe("sys");
    expect(result.isError).toBeUndefined();
  });

  it("surfaces a backend error as an isError tool result rather than throwing", async () => {
    globalThis.fetch = vi.fn(
      async () => new Response(JSON.stringify({ status: 403, detail: "Not opted in." }), { status: 403 }),
    ) as unknown as typeof fetch;

    const { server, tools } = fakeMcpServer();
    registerAssistantContextTools(server, "pat-123");

    const result = await tools.get("get_journal_reflection_context")!.handler({ weekOf: "2026-09-21" });

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain("Not opted in.");
  });
});
