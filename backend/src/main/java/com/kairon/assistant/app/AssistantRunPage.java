package com.kairon.assistant.app;

import java.util.List;

/**
 * Paginated {@code GET /assistant/runs} result (M9 D12) — the same
 * {@code content}/{@code page}/{@code totalElements} shape {@code ProjectPage}
 * already established, so the web layer doesn't need a second paging
 * convention.
 */
public record AssistantRunPage(List<AssistantRunView> content, int page, long totalElements) {
}
