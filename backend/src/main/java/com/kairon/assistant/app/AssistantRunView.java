package com.kairon.assistant.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The run itself plus its suggestions, if any (D12's response shape, §5).
 * {@code suggestedProject} is set only for a {@code PROJECT_GENERATION} run
 * (M8.5 §5); {@code suggestions} is set only for a {@code TODO_SUGGESTION}
 * run — a run only ever populates the field matching its own kind.
 */
public record AssistantRunView(
        UUID id,
        String kind,
        String status,
        String model,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer inputTokens,
        Integer outputTokens,
        String error,
        Instant createdAt,
        List<AssistantSuggestedTaskView> suggestions,
        AssistantSuggestedProjectView suggestedProject) {
}
