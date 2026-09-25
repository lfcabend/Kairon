package com.kairon.assistant.llm;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** One item of {@link ProjectPlanPayload#tasks()}. See its Javadoc. */
public record PlannedTaskPayload(
        @JsonPropertyDescription("A short unique key you choose, e.g. 't1'. Referenced by other "
                + "tasks' parentKey and by dependencies — never reuse a key.")
        String key,
        @JsonPropertyDescription("Omit for a top-level task. At most one level of nesting — "
                + "never set parentKey to a task that itself has a parentKey.")
        String parentKey,
        String name,
        String description,
        @JsonPropertyDescription("True for a zero-duration marker (plannedStart must equal "
                + "plannedEnd). Use for real milestones only, not every task's finish.")
        boolean isMilestone,
        @JsonPropertyDescription("Must fall within the project's overall date range.")
        LocalDate plannedStart,
        LocalDate plannedEnd,
        @JsonPropertyDescription("Rough estimate in hours, only when reasonably inferable.")
        BigDecimal estimateHours) {
}
