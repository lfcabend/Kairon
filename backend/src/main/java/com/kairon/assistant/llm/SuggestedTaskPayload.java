package com.kairon.assistant.llm;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** One item of {@link TodoSuggestionsPayload#suggestions()}. See its Javadoc. */
public record SuggestedTaskPayload(
        @JsonPropertyDescription("A short, concrete action completable in one sitting, "
                + "e.g. 'Order cabinet hardware', never a vague goal like 'Make progress on X'.")
        String title,
        @JsonPropertyDescription("Optional supporting detail. Omit if the title is self-explanatory.")
        String notes,
        @JsonPropertyDescription("One sentence naming the specific project, task, or journal "
                + "entry that prompted this suggestion — never a generic reason.")
        String rationale,
        @JsonPropertyDescription("Must fall within the date range stated in the user message.")
        LocalDate suggestedForDay,
        @JsonPropertyDescription("Rough estimate in minutes, only when reasonably inferable from context.")
        Integer estimateMinutes,
        @JsonPropertyDescription("Set only when this suggestion maps directly to an existing "
                + "open project task named in the context; omit otherwise.")
        UUID sourceProjectTaskId) {
}
