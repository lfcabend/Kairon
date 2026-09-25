package com.kairon.assistant.llm;

import java.util.List;

/**
 * The only wrapper around the Anthropic Java SDK — an ArchUnit rule asserts no
 * other package imports {@code com.anthropic..} (docs/adr/0002). Kept to
 * exactly the one call shape M8 needs; M9/M10 add their own methods here
 * rather than generalizing this one prematurely.
 */
public interface AnthropicClient {

    TodoSuggestionsResult suggestTodos(TodoSuggestionRequest request);

    /** Added for M8.5's project-generation feature. */
    ProjectPlanResult generateProjectPlan(ProjectPlanRequest request);

    record TodoSuggestionRequest(String systemPrompt, String userContent, String model, String effort) {
    }

    record TodoSuggestionsResult(
            List<SuggestedTaskPayload> suggestions,
            String model,
            long inputTokens,
            long outputTokens) {
    }

    record ProjectPlanRequest(String systemPrompt, String userContent, String model) {
    }

    record ProjectPlanResult(ProjectPlanPayload plan, String model, long inputTokens, long outputTokens) {
    }
}
