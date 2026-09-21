package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.UUID;

public record AssistantSuggestedTaskView(
        UUID id,
        UUID runId,
        String title,
        String notes,
        String rationale,
        LocalDate suggestedForDay,
        Integer estimateMinutes,
        UUID sourceProjectTaskId,
        String status,
        UUID acceptedTodoItemId,
        int position) {
}
