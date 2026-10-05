package com.kairon.assistant.llm;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured-output shape for a {@code PROJECT_EDIT} run — the SDK
 * derives the response JSON schema from this record (docs/milestones/
 * M9.5-ai-project-editing.md §4.3). Unlike {@code ProjectPlanPayload}, this
 * is a diff against a project the model is given in context, not a whole new
 * tree: only operations representing an actual change are emitted (D2); an
 * {@code UPDATE} always carries its complete new field set, never a partial
 * patch (D3). Operation count is capped after the response comes back
 * (kairon.assistant.project-edit.max-operations), not here.
 */
public record ProjectEditPayload(
        @JsonPropertyDescription("Present only if the project's own fields (name/description/size/"
                + "dates/category) are changing. When present, every field must carry its complete "
                + "new value, including fields copied forward unchanged from the current state.")
        ProjectFieldChangesPayload projectChanges,
        @JsonPropertyDescription("Only tasks actually being added, updated, or removed — don't "
                + "restate untouched tasks.")
        List<TaskOperationPayload> taskOperations,
        @JsonPropertyDescription("Only dependency edges actually being added or removed.")
        List<DependencyOperationPayload> dependencyOperations,
        @JsonPropertyDescription("Only sibling groups the description actually implies reordering.")
        List<ReorderOperationPayload> reorderOperations) {
}
