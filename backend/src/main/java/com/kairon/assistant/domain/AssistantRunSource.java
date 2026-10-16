package com.kairon.assistant.domain;

/**
 * Which door produced an {@link AssistantRun}: the paid Anthropic-API path
 * ({@link #API}, the default — every run before M10.5 and every run from a
 * user without the MCP server) or the zero-marginal-cost MCP path
 * ({@link #MCP}, docs/milestones/M10.5-mcp-server.md D10). Purely
 * descriptive/audit — nothing branches on it except the (already-skipped,
 * D5) budget check's absence on the MCP path.
 */
public enum AssistantRunSource {
    API,
    MCP
}
