import express from "express";
import type { Request, Response } from "express";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";

import { extractBearerToken } from "./auth.js";
import { config } from "./config.js";
import { registerAssistantContextTools } from "./tools/assistantContext.js";
import { registerAssistantSubmitTools } from "./tools/assistantSubmit.js";
import { registerGeneralReadTools } from "./tools/generalReads.js";

const SERVER_NAME = "kairon-mcp-server";
const SERVER_VERSION = "1.0.0";

/**
 * Builds a fresh `McpServer` whose every tool handler closes over this one
 * request's bearer token — never a server-wide credential (D1-auth). A new
 * instance per request, paired with a stateless `StreamableHTTPServerTransport`
 * (no `sessionIdGenerator`), is what keeps two concurrent users' requests —
 * or two devices for the same user — from ever touching each other's data:
 * there is no shared mutable state to leak between them, and a pod restart
 * or horizontal replica loses nothing because nothing stateful exists.
 */
function createServer(bearerToken: string): McpServer {
  const server = new McpServer({ name: SERVER_NAME, version: SERVER_VERSION });
  registerAssistantContextTools(server, bearerToken);
  registerAssistantSubmitTools(server, bearerToken);
  registerGeneralReadTools(server, bearerToken);
  return server;
}

const app = express();
app.use(express.json({ limit: "4mb" }));

app.post("/mcp", async (req: Request, res: Response) => {
  const bearerToken = extractBearerToken(req);
  if (!bearerToken) {
    // 401 before any tool handler runs (D1-auth) — no PAT validation happens
    // here at all; an invalid/expired/revoked token simply surfaces later as
    // whatever 401/403 Kairon's own backend returns on the first real call,
    // propagated back as a tool error.
    res.status(401).json({ error: "Missing or malformed Authorization header." });
    return;
  }

  try {
    const server = createServer(bearerToken);
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on("close", () => {
      transport.close().catch(() => {});
      server.close().catch(() => {});
    });
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
  } catch (err) {
    console.error("Error handling MCP request", err);
    if (!res.headersSent) {
      res.status(500).json({ error: "Internal server error." });
    }
  }
});

// Stateless mode (no sessionIdGenerator) never issues a session id, so there's
// no server-initiated push stream to open with GET and nothing to end with
// DELETE — both methods are simply not supported in this deployment shape.
app.get("/mcp", (_req: Request, res: Response) => {
  res.status(405).set("Allow", "POST").json({ error: "Method not allowed — this server runs in stateless mode." });
});
app.delete("/mcp", (_req: Request, res: Response) => {
  res.status(405).set("Allow", "POST").json({ error: "Method not allowed — this server runs in stateless mode." });
});

app.get("/healthz", (_req: Request, res: Response) => {
  res.status(200).json({ status: "ok" });
});

app.listen(config.port, () => {
  console.log(`${SERVER_NAME} listening on :${config.port}, forwarding to ${config.kaironBaseUrl}`);
});
