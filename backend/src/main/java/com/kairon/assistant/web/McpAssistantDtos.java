package com.kairon.assistant.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.assistant.app.Horizon;
import com.kairon.assistant.app.SummaryPeriod;
import com.kairon.assistant.llm.ProjectEditPayload;
import com.kairon.assistant.llm.ProjectPlanPayload;
import com.kairon.assistant.llm.SuggestedTaskPayload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request/response shapes for the ten {@code /api/v1/assistant/mcp/**}
 * endpoints (docs/milestones/M10.5-mcp-server.md §5). Every {@code *ContextRequest}
 * mirrors the matching existing {@code AssistantDtos}/{@code ProjectPlanDtos}/
 * {@code ProjectEditDtos} request shape; every {@code Submit*Request} adds
 * the model's own output (suggestions/plan/diff/markdown) plus an optional,
 * free-text, never-validated {@code model} (D11).
 */
final class McpAssistantDtos {

    private McpAssistantDtos() {
    }

    /** The same {@code {systemPrompt, userContent}} shape every {@code *ContextBuilder} already renders (D12). */
    record McpContextResponse(String systemPrompt, String userContent) {
    }

    record TodoSuggestionContextRequest(@NotNull LocalDate day, @NotNull Horizon horizon) {
    }

    record SubmitTodoSuggestionsRequest(@NotNull LocalDate day, @NotNull Horizon horizon, String model,
            @NotNull @Valid List<SuggestedTaskPayload> suggestions) {
    }

    record ProjectPlanContextRequest(@NotBlank String description, @NotNull LocalDate startDate,
            LocalDate targetDeadline) {
    }

    record SubmitProjectPlanRequest(@NotBlank String description, @NotNull LocalDate startDate,
            LocalDate targetDeadline, String model, @NotNull @Valid ProjectPlanPayload plan) {
    }

    record ProjectEditContextRequest(@NotNull UUID projectId, @NotBlank String description) {
    }

    record SubmitProjectEditRequest(@NotNull UUID projectId, @NotBlank String description, String model,
            @NotNull @Valid ProjectEditPayload diff) {
    }

    record SummaryContextRequest(@NotNull SummaryPeriod period, @NotNull LocalDate date) {
    }

    record SubmitSummaryRequest(@NotNull SummaryPeriod period, @NotNull LocalDate date, String model,
            @NotBlank String markdown) {
    }

    record JournalReflectionContextRequest(@NotNull LocalDate weekOf) {
    }

    record SubmitJournalReflectionRequest(@NotNull LocalDate weekOf, String model, @NotBlank String markdown) {
    }
}
