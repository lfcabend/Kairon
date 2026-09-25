package com.kairon.assistant.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** One item of {@link ProjectPlanPayload#dependencies()}. See its Javadoc. */
public record PlannedDependencyPayload(
        String predecessorKey, String successorKey,
        @JsonPropertyDescription("FS (default), SS, FF, or SF.")
        String type,
        Integer lagDays) {
}
