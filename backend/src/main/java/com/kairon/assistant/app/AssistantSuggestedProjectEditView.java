package com.kairon.assistant.app;

import java.util.List;
import java.util.UUID;

public record AssistantSuggestedProjectEditView(
        UUID id,
        UUID runId,
        String status,
        UUID projectId,
        ProjectFieldChangesView projectChanges,
        List<TaskOperationView> taskOperations,
        List<DependencyOperationView> dependencyOperations,
        List<ReorderOperationView> reorderOperations) {
}
