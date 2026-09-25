package com.kairon.assistant.app;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AssistantSuggestedProjectView(
        UUID id,
        UUID runId,
        String status,
        String name,
        String description,
        String size,
        LocalDate startDate,
        LocalDate endDate,
        List<PlannedTaskView> tasks,
        List<PlannedDependencyView> dependencies,
        UUID acceptedProjectId) {
}
