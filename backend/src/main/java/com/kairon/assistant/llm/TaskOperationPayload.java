package com.kairon.assistant.llm;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** One item of {@link ProjectEditPayload#taskOperations()}. See its Javadoc. */
public record TaskOperationPayload(
        @JsonPropertyDescription("ADD, UPDATE, or REMOVE.")
        String op,
        @JsonPropertyDescription("The real task id from the project context below — required for "
                + "UPDATE/REMOVE, omitted for ADD.")
        String existingTaskId,
        @JsonPropertyDescription("A short unique key you choose — required for ADD only, so other "
                + "operations in this same response can refer to this new task before it exists. "
                + "Never reuse a key, and never use a key for an existing task — use its real id.")
        String key,
        @JsonPropertyDescription("The new parent: either an existing task's real id, or (for a task "
                + "also being added in this response) its key. Omit for a top-level task. At most "
                + "one level of nesting.")
        String parentRef,
        String name,
        String description,
        @JsonPropertyDescription("True for a zero-duration marker (plannedStart must equal "
                + "plannedEnd).")
        boolean isMilestone,
        LocalDate plannedStart,
        LocalDate plannedEnd,
        BigDecimal estimateHours) {
}
