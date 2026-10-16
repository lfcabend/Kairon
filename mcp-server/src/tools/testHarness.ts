import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

/**
 * A minimal stand-in for `McpServer` that just records every `.tool(...)`
 * registration, so a `register*Tools` function can be exercised without any
 * real transport/session machinery. Test-only.
 */
export interface RegisteredTool {
  description: string;
  schema: unknown;
  handler: (args: Record<string, unknown>) => Promise<{ content: { type: string; text: string }[]; isError?: boolean }>;
}

export function fakeMcpServer() {
  const tools = new Map<string, RegisteredTool>();
  const server = {
    tool: (name: string, description: string, schema: unknown, handler: RegisteredTool["handler"]) => {
      tools.set(name, { description, schema, handler });
    },
  };
  return { server: server as unknown as McpServer, tools };
}
