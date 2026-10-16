import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { afterEach, describe, expect, it, vi } from "vitest";

import { registerAssistantContextTools } from "./assistantContext.js";
import { registerAssistantSubmitTools } from "./assistantSubmit.js";
import { registerGeneralReadTools } from "./generalReads.js";

/**
 * Exercises every one of the 16 registered tools through the REAL
 * `@modelcontextprotocol/sdk` `McpServer`/`Client` pair (linked in-memory
 * transports, no network) rather than the lighter `testHarness.ts` fake used
 * by the per-group unit tests — this is the only place that proves the real
 * zod schema validation layer actually rejects a malformed call *before* any
 * handler (and so any `fetch`) runs, which a fake harness that just records
 * `.tool(...)` registrations cannot exercise on its own.
 */
async function connectedClient(bearerToken = "pat-test-token") {
  const server = new McpServer({ name: "kairon-mcp-server-test", version: "0.0.0" });
  registerAssistantContextTools(server, bearerToken);
  registerAssistantSubmitTools(server, bearerToken);
  registerGeneralReadTools(server, bearerToken);

  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: "test-client", version: "0.0.0" });
  await Promise.all([client.connect(clientTransport), server.connect(serverTransport)]);
  return { client, server };
}

interface Case {
  tool: string;
  valid: Record<string, unknown>;
  invalid: Record<string, unknown>;
}

const CASES: Case[] = [
  { tool: "get_todo_suggestion_context", valid: { day: "2026-09-21", horizon: "DAY" }, invalid: {} },
  {
    tool: "get_project_plan_context",
    valid: { description: "Kitchen remodel", startDate: "2026-09-21" },
    invalid: { description: "Kitchen remodel" }, // missing startDate
  },
  {
    tool: "get_project_edit_context",
    valid: { projectId: "p1", description: "add a task" },
    invalid: { projectId: "p1" }, // missing description
  },
  {
    tool: "get_summary_context",
    valid: { period: "WEEK", date: "2026-09-21" },
    invalid: { period: "NOT_A_PERIOD", date: "2026-09-21" }, // enum violation
  },
  { tool: "get_journal_reflection_context", valid: { weekOf: "2026-09-21" }, invalid: {} },
  {
    tool: "submit_todo_suggestions",
    valid: { day: "2026-09-21", horizon: "DAY", suggestions: [] },
    invalid: { day: "2026-09-21", horizon: "DAY" }, // missing suggestions
  },
  {
    tool: "submit_project_plan",
    valid: {
      description: "Kitchen remodel",
      startDate: "2026-09-21",
      plan: { name: "Kitchen remodel", tasks: [], dependencies: [] },
    },
    invalid: { description: "Kitchen remodel", startDate: "2026-09-21" }, // missing plan
  },
  {
    tool: "submit_project_edit",
    valid: {
      projectId: "p1",
      description: "add a task",
      diff: { taskOperations: [], dependencyOperations: [], reorderOperations: [] },
    },
    invalid: { projectId: "p1", description: "add a task" }, // missing diff
  },
  {
    tool: "submit_summary",
    valid: { period: "WEEK", date: "2026-09-21", markdown: "It went well." },
    invalid: { period: "WEEK", date: "2026-09-21" }, // missing markdown
  },
  {
    tool: "submit_journal_reflection",
    valid: { weekOf: "2026-09-21", markdown: "A quiet week." },
    invalid: { weekOf: "2026-09-21" }, // missing markdown
  },
  { tool: "list_todos", valid: { day: "2026-09-21" }, invalid: { day: 12345 } }, // wrong type
  { tool: "list_journal_entries", valid: { day: "2026-09-21" }, invalid: { from: true } }, // wrong type
  { tool: "list_projects", valid: {}, invalid: { page: "not-a-number" } }, // wrong type
  { tool: "get_project", valid: { projectId: "p1" }, invalid: {} }, // missing projectId
  { tool: "list_project_tasks", valid: { projectId: "p1" }, invalid: {} }, // missing projectId
  { tool: "list_project_dependencies", valid: { projectId: "p1" }, invalid: {} }, // missing projectId
];

describe("every registered tool, via the real MCP SDK validation layer", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("registers exactly the 16 tools this server is documented to expose", async () => {
    const { client } = await connectedClient();
    const { tools } = await client.listTools();
    expect(tools.map((t) => t.name).sort()).toEqual(CASES.map((c) => c.tool).sort());
  });

  it.each(CASES)("$tool: a valid call reaches the handler and calls fetch", async ({ tool, valid }) => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 }));
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    const { client } = await connectedClient();

    const result = await client.callTool({ name: tool, arguments: valid });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(result.isError).toBeFalsy();
  });

  it.each(CASES)(
    "$tool: a malformed call is rejected by schema validation before any fetch runs",
    async ({ tool, invalid }) => {
      const fetchMock = vi.fn(async () => new Response(JSON.stringify({ ok: true }), { status: 200 }));
      globalThis.fetch = fetchMock as unknown as typeof fetch;
      const { client } = await connectedClient();

      // The SDK's own schema check runs before our handler ever gets called — a
      // rejection happens at the JSON-RPC level (McpError, code -32602) but the
      // Client surfaces it as a resolved CallToolResult with isError: true,
      // same shape our own handlers use for a Kairon-side failure (never a
      // thrown/rejected promise either way).
      const result = await client.callTool({ name: tool, arguments: invalid });

      expect(result.isError).toBe(true);
      expect(String((result.content as { text: string }[])[0].text)).toMatch(/Input validation error/);
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );
});
