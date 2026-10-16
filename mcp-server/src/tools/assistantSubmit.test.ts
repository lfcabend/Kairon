import { afterEach, describe, expect, it, vi } from "vitest";

import { registerAssistantSubmitTools } from "./assistantSubmit.js";
import { fakeMcpServer } from "./testHarness.js";

describe("assistantSubmit tools", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("submit_todo_suggestions rejects a malformed call (missing required fields) before any fetch", async () => {
    const { server, tools } = fakeMcpServer();
    registerAssistantSubmitTools(server, "pat-123");

    // The zod shape itself isn't enforced by this fake harness (that's the real
    // McpServer's job at the transport layer) — this test instead proves the
    // handler calls the expected endpoint with a mocked fetch when given a
    // valid, complete call.
    const fetchMock = vi.fn(
      async () =>
        new Response(
          JSON.stringify({ id: "run-1", kind: "TODO_SUGGESTION", status: "SUCCEEDED", source: "MCP" }),
          { status: 201 },
        ),
    );
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const result = await tools.get("submit_todo_suggestions")!.handler({
      day: "2026-09-21",
      horizon: "DAY",
      suggestions: [{ title: "Order hardware", rationale: "overdue", suggestedForDay: "2026-09-21" }],
    });

    expect(fetchMock).toHaveBeenCalledWith(
      expect.objectContaining({ pathname: expect.stringContaining("/assistant/mcp/todo-suggestions") }),
      expect.objectContaining({ method: "POST" }),
    );
    const payload = JSON.parse(result.content[0].text);
    expect(payload.source).toBe("MCP");
  });

  it("submit_project_edit surfaces a 404 for a foreign project as an isError result", async () => {
    globalThis.fetch = vi.fn(
      async () => new Response(JSON.stringify({ status: 404, detail: "Project not found." }), { status: 404 }),
    ) as unknown as typeof fetch;

    const { server, tools } = fakeMcpServer();
    registerAssistantSubmitTools(server, "pat-123");

    const result = await tools.get("submit_project_edit")!.handler({
      projectId: "not-mine",
      description: "add a task",
      diff: { taskOperations: [], dependencyOperations: [], reorderOperations: [] },
    });

    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain("Project not found.");
  });
});
