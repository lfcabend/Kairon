package com.kairon.assistant.llm;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured-output shape for a {@code PROJECT_GENERATION} run — the SDK
 * derives the response JSON schema from this record (docs/milestones/M8.5-project-generation.md
 * §4.3). Task/dependency counts are capped after the response comes back
 * (kairon.assistant.project-plan.max-tasks), not here — no schema-level array
 * size constraint exists in this SDK version (same posture as M8's
 * {@code TodoSuggestionsPayload}).
 */
public record ProjectPlanPayload(
        @JsonPropertyDescription("A short, specific project name, e.g. 'Kitchen remodel', "
                + "not a restatement of the whole description.")
        String name,
        @JsonPropertyDescription("One or two sentences summarizing scope. Markdown ok.")
        String description,
        @JsonPropertyDescription("Rough t-shirt size for the whole project: XS, S, M, L, or XL.")
        String size,
        @JsonPropertyDescription("Every task's key referenced by parentKey/predecessorKey/"
                + "successorKey must appear in this list.")
        List<PlannedTaskPayload> tasks,
        List<PlannedDependencyPayload> dependencies) {
}
