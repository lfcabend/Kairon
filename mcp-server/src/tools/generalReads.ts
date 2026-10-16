import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { kaironFetch } from "../kaironClient.js";

function textResult(value: unknown) {
  return { content: [{ type: "text" as const, text: JSON.stringify(value, null, 2) }] };
}

function errorResult(message: string) {
  return { content: [{ type: "text" as const, text: message }], isError: true };
}

/**
 * General-purpose, read-only tools over `todo`/`journal`/`projects` for ad
 * hoc queries — thin wrappers over Kairon's *existing* REST endpoints (D6),
 * not a new LLM-shaped read surface. Every call forwards the current
 * request's bearer token verbatim (D1-auth), same as the five assistant
 * context/submit pairs.
 */
export function registerGeneralReadTools(server: McpServer, bearerToken: string): void {
  server.tool(
    "list_todos",
    "List todo items for a single day, or a date range (inclusive, at most ~92 days).",
    {
      day: z.string().optional().describe("ISO date — exactly one of `day` or `from`+`to` is required."),
      from: z.string().optional(),
      to: z.string().optional(),
    },
    async ({ day, from, to }) => {
      try {
        const data = await kaironFetch(bearerToken, "/todo", { query: { day, from, to } });
        return textResult(data);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "list_journal_entries",
    "List journal entries for a single day, or a date range (inclusive, at most ~92 days).",
    {
      day: z.string().optional().describe("ISO date — exactly one of `day` or `from`+`to` is required."),
      from: z.string().optional(),
      to: z.string().optional(),
    },
    async ({ day, from, to }) => {
      try {
        const data = await kaironFetch(bearerToken, "/journal", { query: { day, from, to } });
        return textResult(data);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "list_projects",
    "List the caller's projects, optionally filtered by status/category/size.",
    {
      status: z.string().optional(),
      categoryId: z.string().optional(),
      size: z.string().optional(),
      includeArchived: z.boolean().optional(),
      page: z.number().int().optional(),
      pageSize: z.number().int().optional(),
    },
    async ({ status, categoryId, size, includeArchived, page, pageSize }) => {
      try {
        const data = await kaironFetch(bearerToken, "/projects", {
          query: { status, categoryId, size, includeArchived, page, pageSize },
        });
        return textResult(data);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "get_project",
    "Fetch one project by id.",
    { projectId: z.string() },
    async ({ projectId }) => {
      try {
        const data = await kaironFetch(bearerToken, `/projects/${encodeURIComponent(projectId)}`);
        return textResult(data);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "list_project_tasks",
    "List a project's tasks.",
    {
      projectId: z.string(),
      page: z.number().int().optional(),
      size: z.number().int().optional(),
    },
    async ({ projectId, page, size }) => {
      try {
        const data = await kaironFetch(bearerToken, `/projects/${encodeURIComponent(projectId)}/tasks`, {
          query: { page, size },
        });
        return textResult(data);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "list_project_dependencies",
    "List a project's task dependency edges.",
    { projectId: z.string() },
    async ({ projectId }) => {
      try {
        const data = await kaironFetch(bearerToken, `/projects/${encodeURIComponent(projectId)}/dependencies`);
        return textResult(data);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );
}
