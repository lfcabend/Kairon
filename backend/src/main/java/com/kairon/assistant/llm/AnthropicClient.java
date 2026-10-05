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

    /**
     * Added for M9's execution summaries — plain markdown narration, not a
     * structured-output call (docs/milestones/M9-execution-summaries.md D6).
     */
    SummaryResult generateSummary(SummaryRequest request);

    /**
     * Added for M10's journal reflection — plain markdown narration, identical
     * shape to {@link #generateSummary}, no appended stats table on the caller
     * side (docs/milestones/M10-journal-reflection.md D11).
     */
    ReflectionResult generateReflection(ReflectionRequest request);

    /**
     * Submits one Anthropic Message Batch covering every given request — used
     * only by M9's scheduled summary sweep, one call per scheduler firing
     * (docs/milestones/M10-journal-reflection.md D19/D20).
     */
    BatchHandle submitBatch(List<BatchRequestItem> requests);

    /** Checks a submitted batch's current status (D22). */
    BatchPollResult pollBatch(String anthropicBatchId);

    /** Reads back every per-request result of an {@code ENDED} batch (D22/D24). */
    List<BatchResultItem> retrieveBatchResults(String anthropicBatchId);

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

    record SummaryRequest(String systemPrompt, String userContent, String model, String effort) {
    }

    record SummaryResult(String markdown, String model, long inputTokens, long outputTokens) {
    }

    record ReflectionRequest(String systemPrompt, String userContent, String model, String effort) {
    }

    record ReflectionResult(String markdown, String model, long inputTokens, long outputTokens) {
    }

    record BatchRequestItem(String customId, String systemPrompt, String userContent, String model) {
    }

    record BatchHandle(String anthropicBatchId) {
    }

    /** {@code status}: {@code IN_PROGRESS} | {@code CANCELING} | {@code ENDED}. */
    record BatchPollResult(boolean ended, String status) {
    }

    record BatchResultItem(
            String customId, boolean succeeded,
            String markdown, long inputTokens, long outputTokens, String errorDetail) {
    }
}
