package com.kairon.assistant.app;

import java.util.List;

import com.kairon.assistant.llm.DependencyOperationPayload;
import com.kairon.assistant.llm.ReorderOperationPayload;
import com.kairon.assistant.llm.TaskOperationPayload;

/**
 * What's actually persisted as {@code assistant_suggested_project_edit.diff}
 * — the model's {@code ProjectEditPayload} (task/dependency/reorder
 * operations), plus {@link #projectChanges}, resolved against the user's
 * existing categories right after the model call ({@link AssistantRunService}),
 * the same way {@code PersistedProjectPlan} resolves its own category
 * (docs/milestones/M9.5-ai-project-editing.md D1, mirroring M8.5 D10):
 * {@code projectChanges.categoryId()} non-null means the model's
 * {@code categoryName} exactly matched an existing category; a null
 * {@code categoryId} with a non-null {@code categoryName} means no match, to
 * be created as a new category on accept; both null means the project's own
 * fields aren't touching category at all.
 */
record PersistedProjectEdit(
        PersistedProjectFieldChanges projectChanges,
        List<TaskOperationPayload> taskOperations,
        List<DependencyOperationPayload> dependencyOperations,
        List<ReorderOperationPayload> reorderOperations) {

    /** See {@link PersistedProjectEdit#projectChanges}. {@code null} when the project's own fields aren't changing. */
    record PersistedProjectFieldChanges(
            String name, String description, String size, java.time.LocalDate startDate,
            java.time.LocalDate endDate, java.util.UUID categoryId, String categoryName) {
    }
}
