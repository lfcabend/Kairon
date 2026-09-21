package com.kairon.assistant.app;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The run itself plus its suggestions, if any (D12's response shape, §5). */
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
        List<AssistantSuggestedTaskView> suggestions) {
}
