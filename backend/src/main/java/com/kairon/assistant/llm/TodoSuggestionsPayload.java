package com.kairon.assistant.llm;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured-output shape for a {@code TODO_SUGGESTION} run — the SDK
 * derives the response JSON schema from this record (M8 D5). There is no
 * schema-level cap on {@link #suggestions()}'s size in this SDK version
 * (no {@code @ArraySchema} annotation is available); the hard cap
 * (kairon.assistant.todo-suggestions.max-suggestions) is enforced in
 * {@code AssistantRunService} after the response comes back, not here.
 */
public record TodoSuggestionsPayload(
        @JsonPropertyDescription("Proposed todo items for the requested window. "
                + "Return an empty list if nothing in the context warrants a suggestion — "
                + "never pad the list to reach a target count.")
        List<SuggestedTaskPayload> suggestions) {
}
