import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

import { kaironFetch } from "../kaironClient.js";
import type { AssistantRunView } from "../kaironTypes.js";

function textResult(value: unknown) {
  return { content: [{ type: "text" as const, text: JSON.stringify(value, null, 2) }] };
}

function errorResult(message: string) {
  return { content: [{ type: "text" as const, text: message }], isError: true };
}

const MODEL_FIELD = z
  .string()
  .optional()
  .describe("Free-text, e.g. the real model name driving this tool call. Never validated — purely audit metadata.");

const suggestedTaskSchema = z.object({
  title: z.string().describe("A short, concrete action completable in one sitting."),
  notes: z.string().optional(),
  rationale: z.string().describe("One sentence naming the specific project, task, or journal entry that prompted this."),
  suggestedForDay: z.string().describe("ISO date — must fall within the requested range."),
  estimateMinutes: z.number().int().optional(),
  sourceProjectTaskId: z.string().optional(),
});

const plannedTaskSchema = z.object({
  key: z.string().describe("A short unique key you choose, referenced by parentKey/predecessorKey/successorKey."),
  parentKey: z.string().optional().describe("Omit for a top-level task. At most one level of nesting."),
  name: z.string(),
  description: z.string().optional(),
  isMilestone: z.boolean().default(false),
  plannedStart: z.string().optional(),
  plannedEnd: z.string().optional(),
  estimateHours: z.number().optional(),
});

const plannedDependencySchema = z.object({
  predecessorKey: z.string(),
  successorKey: z.string(),
  type: z.enum(["FS", "SS", "FF", "SF"]).optional(),
  lagDays: z.number().int().optional(),
});

const projectPlanSchema = z.object({
  name: z.string(),
  description: z.string().optional(),
  size: z.enum(["XS", "S", "M", "L", "XL"]).optional(),
  categoryName: z.string().nullable().optional(),
  tasks: z.array(plannedTaskSchema),
  dependencies: z.array(plannedDependencySchema),
});

const projectFieldChangesSchema = z.object({
  name: z.string().optional(),
  description: z.string().optional(),
  size: z.enum(["XS", "S", "M", "L", "XL"]).optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  categoryName: z.string().nullable().optional(),
});

const taskOperationSchema = z.object({
  op: z.enum(["ADD", "UPDATE", "REMOVE"]),
  existingTaskId: z.string().optional().describe("Required for UPDATE/REMOVE."),
  key: z.string().optional().describe("Required for ADD only."),
  parentRef: z.string().optional(),
  name: z.string().optional(),
  description: z.string().optional(),
  isMilestone: z.boolean().default(false),
  plannedStart: z.string().optional(),
  plannedEnd: z.string().optional(),
  estimateHours: z.number().optional(),
});

const dependencyOperationSchema = z.object({
  op: z.enum(["ADD", "REMOVE"]),
  existingDependencyId: z.string().optional(),
  predecessorRef: z.string().optional(),
  successorRef: z.string().optional(),
  type: z.enum(["FS", "SS", "FF", "SF"]).optional(),
  lagDays: z.number().int().optional(),
});

const reorderOperationSchema = z.object({
  parentRef: z.string().optional(),
  orderedRefs: z.array(z.string()),
});

const projectEditSchema = z.object({
  projectChanges: projectFieldChangesSchema.nullable().optional(),
  taskOperations: z.array(taskOperationSchema),
  dependencyOperations: z.array(dependencyOperationSchema),
  reorderOperations: z.array(reorderOperationSchema),
});

/**
 * The five `submit_*` write-back tools — always creates a `PROPOSED`
 * suggestion row (or, for summary/reflection, a `SUCCEEDED` run) identical
 * in shape to what the Anthropic-API path creates (D4). Never a direct
 * accept: the normal web/Android review screen is the only path to real
 * data. Draws zero per-user token budget (D5) and is tagged `source: MCP`
 * (D10). You must have called the matching `get_*_context` tool first and
 * based your answer only on what it returned.
 */
export function registerAssistantSubmitTools(server: McpServer, bearerToken: string): void {
  server.tool(
    "submit_todo_suggestions",
    "Submit proposed todo items from get_todo_suggestion_context's output. Creates a PROPOSED " +
      "assistant run the user reviews and accepts/dismisses in the normal Kairon UI — nothing is " +
      "created directly.",
    {
      day: z.string(),
      horizon: z.enum(["DAY", "WEEK"]),
      model: MODEL_FIELD,
      suggestions: z.array(suggestedTaskSchema),
    },
    async ({ day, horizon, model, suggestions }) => {
      try {
        const run = await kaironFetch<AssistantRunView>(bearerToken, "/assistant/mcp/todo-suggestions", {
          method: "POST",
          body: { day, horizon, model, suggestions },
        });
        return textResult(run);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "submit_project_plan",
    "Submit a proposed whole project plan from get_project_plan_context's output. Creates a " +
      "PROPOSED suggested project the user reviews and accepts/dismisses in the normal Kairon UI.",
    {
      description: z.string(),
      startDate: z.string(),
      targetDeadline: z.string().optional(),
      model: MODEL_FIELD,
      plan: projectPlanSchema,
    },
    async ({ description, startDate, targetDeadline, model, plan }) => {
      try {
        const run = await kaironFetch<AssistantRunView>(bearerToken, "/assistant/mcp/project-plan", {
          method: "POST",
          body: { description, startDate, targetDeadline, model, plan },
        });
        return textResult(run);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "submit_project_edit",
    "Submit a proposed edit diff from get_project_edit_context's output — only operations that " +
      "represent an actual change. Creates a PROPOSED suggested edit the user reviews and " +
      "accepts/dismisses in the normal Kairon UI. 404s if the project isn't yours.",
    {
      projectId: z.string(),
      description: z.string(),
      model: MODEL_FIELD,
      diff: projectEditSchema,
    },
    async ({ projectId, description, model, diff }) => {
      try {
        const run = await kaironFetch<AssistantRunView>(bearerToken, "/assistant/mcp/project-edits", {
          method: "POST",
          body: { projectId, description, model, diff },
        });
        return textResult(run);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "submit_summary",
    "Submit a narrative for a weekly/monthly execution summary from get_summary_context's output. " +
      "The deterministic stats table is appended automatically after your narrative — never include " +
      "your own stats table or recomputed numbers.",
    {
      period: z.enum(["WEEK", "MONTH"]),
      date: z.string(),
      model: MODEL_FIELD,
      markdown: z.string().describe("Your narrative only — no stats table, no headline numbers table."),
    },
    async ({ period, date, model, markdown }) => {
      try {
        const run = await kaironFetch<AssistantRunView>(bearerToken, "/assistant/mcp/summaries", {
          method: "POST",
          body: { period, date, model, markdown },
        });
        return textResult(run);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );

  server.tool(
    "submit_journal_reflection",
    "Submit a narrative for a weekly journal reflection from get_journal_reflection_context's " +
      "output. 422s if the week has no journal entries.",
    {
      weekOf: z.string(),
      model: MODEL_FIELD,
      markdown: z.string(),
    },
    async ({ weekOf, model, markdown }) => {
      try {
        const run = await kaironFetch<AssistantRunView>(bearerToken, "/assistant/mcp/journal-reflection", {
          method: "POST",
          body: { weekOf, model, markdown },
        });
        return textResult(run);
      } catch (err) {
        return errorResult((err as Error).message);
      }
    },
  );
}
