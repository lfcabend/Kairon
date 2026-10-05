package com.kairon.assistant.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * One item of {@link ProjectEditPayload#dependencyOperations()}. There's no
 * {@code UPDATE} — change a dependency's type/lag by removing the old edge
 * and adding the new one (M9.5 D4).
 */
public record DependencyOperationPayload(
        @JsonPropertyDescription("ADD or REMOVE.")
        String op,
        @JsonPropertyDescription("The real dependency edge id — required for REMOVE, omitted for ADD.")
        String existingDependencyId,
        @JsonPropertyDescription("ADD only: the predecessor task's real id, or a key from a task "
                + "being added in this same response.")
        String predecessorRef,
        @JsonPropertyDescription("ADD only: the successor task's real id, or a key from a task "
                + "being added in this same response.")
        String successorRef,
        @JsonPropertyDescription("FS (default), SS, FF, or SF.")
        String type,
        Integer lagDays) {
}
