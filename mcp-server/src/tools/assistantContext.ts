import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { kaironFetch } from "../kaironClient.js";
import type { McpContextResponse } from "../kaironTypes.js";

function textResult(value: unknown) {
  return { content: [{ type: "text" as const, text: JSON.stringify(value, null, 2) }] };
}

function errorResult(message: string) {
  return { content: [{ type: "text" as const, text: message }], isError: true };
}

const CONTEXT_USAGE_NOTE =
  "Base your answer only on this context — never invent a task, project, date, or " +
  "number that isn't given here. The matching submit_* tool must be called next, " +
  "with output that follows this systemPrompt's own rules.";

/**
 * The five `get_*_context` tools — read-only, create no `assistant_run` row
 * (D8). Each mirrors the matching `POST /assistant/mcp/*:context` endpoint
 * and returns the exact `{systemPrompt, userContent}` pair the backend's own
 * `*ContextBuilder` renders (D12) — never a parallel structured-JSON shape.
 * Every tool receives the current request's bearer token and forwards it
 * verbatim to every Kairon call it makes (D1-auth) — nothing here reads a
 * token from anywhere else.
 */
export function registerAssistantContextTools(server: McpServer, bearerToken: string): void {
  server.tool(
    "get_todo_suggestion_context",
    "Fetch the context for proposing new todo items for a day or week — recent todo history, " +
      "open project tasks, and recent journal entries. Call submit_todo_suggestions next with " +
      "your proposed suggestions.",
    {
      day: z.string().describe("ISO date (yyyy-MM-dd) the suggestions are for."),
      horizon: z.enum(["DAY", "WEEK"]).describe("DAY: only `day` itself. WEEK: day through day+6."),
    },
    async ({ day, horizon }) => {
      try {
        const context = await kaironFetch<McpContextResponse>(bearerToken, "/assistant/mcp/todo-suggestions:context", {
          method: "POST",
          body: { day, horizon },
        });
        return textResult({ ...context, note: CONTEXT_USAGE_NOTE });
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "get_project_plan_context",
    "Fetch the context for generating a whole new project (task tree, milestones, dependencies) " +
      "from a free-text description. Call submit_project_plan next with your proposed plan.",
    {
      description: z.string().describe("Free-text description of the project to plan."),
      startDate: z.string().describe("ISO date the project starts."),
      targetDeadline: z.string().optional().describe("Optional ISO date the project should finish by."),
    },
    async ({ description, startDate, targetDeadline }) => {
      try {
        const context = await kaironFetch<McpContextResponse>(bearerToken, "/assistant/mcp/project-plan:context", {
          method: "POST",
          body: { description, startDate, targetDeadline },
        });
        return textResult({ ...context, note: CONTEXT_USAGE_NOTE });
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "get_project_edit_context",
    "Fetch an existing project's current state (fields, full task tree with real ids, dependency " +
      "edges) plus a free-text description of a desired change. Call submit_project_edit next with " +
      "a diff — only the operations that represent an actual change.",
    {
      projectId: z.string().describe("The existing project's id."),
      description: z.string().describe("Free-text description of the desired change."),
    },
    async ({ projectId, description }) => {
      try {
        const context = await kaironFetch<McpContextResponse>(bearerToken, "/assistant/mcp/project-edits:context", {
          method: "POST",
          body: { projectId, description },
        });
        return textResult({ ...context, note: CONTEXT_USAGE_NOTE });
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "get_summary_context",
    "Fetch the server-computed numbers (todos, project completion, overdue work, category share) " +
      "for a weekly or monthly execution summary. Call submit_summary next with your narrative — " +
      "never recompute or second-guess a number you're given; the stats table itself is always " +
      "server-rendered, appended automatically after your narrative.",
    {
      period: z.enum(["WEEK", "MONTH"]),
      date: z.string().describe("ISO date inside the period to summarize."),
    },
    async ({ period, date }) => {
      try {
        const context = await kaironFetch<McpContextResponse>(bearerToken, "/assistant/mcp/summaries:context", {
          method: "POST",
          body: { period, date },
        });
        return textResult({ ...context, note: CONTEXT_USAGE_NOTE });
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "get_journal_reflection_context",
    "Fetch one week's journal entries in full, plus light todo/project grounding, for a journal " +
      "reflection. Call submit_journal_reflection next with your reflection narrative. Fails with " +
      "a clear error if the week has no journal entries.",
    {
      weekOf: z.string().describe("Any ISO date inside the week to reflect on."),
    },
    async ({ weekOf }) => {
      try {
        const context = await kaironFetch<McpContextResponse>(
          bearerToken,
          "/assistant/mcp/journal-reflection:context",
          { method: "POST", body: { weekOf } },
        );
        return textResult({ ...context, note: CONTEXT_USAGE_NOTE });
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );
}
