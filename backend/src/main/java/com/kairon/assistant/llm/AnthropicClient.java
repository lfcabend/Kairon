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

    record TodoSuggestionRequest(String systemPrompt, String userContent, String model, String effort) {
    }

    record TodoSuggestionsResult(
            List<SuggestedTaskPayload> suggestions,
            String model,
            long inputTokens,
            long outputTokens) {
    }
}
