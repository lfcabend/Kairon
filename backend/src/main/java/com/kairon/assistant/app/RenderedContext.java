package com.kairon.assistant.app;

/**
 * The {@code {systemPrompt, userContent}} pair a {@code get_*_context} MCP
 * tool call returns (D12) — the public, cross-package-visible shape
 * {@link McpAssistantService}'s five {@code read*Context} methods return, so
 * {@code assistant.web.McpAssistantController} never needs to name any of
 * the package-private {@code *ContextBuilder.Context} types directly.
 */
public record RenderedContext(String systemPrompt, String userContent) {
}
