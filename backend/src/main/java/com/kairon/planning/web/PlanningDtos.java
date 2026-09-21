package com.kairon.planning.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.planning.app.PlanningService;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.todo.api.TodoItemView;

import jakarta.validation.constraints.NotNull;

/**
 * Request/response bodies for {@code /api/v1/planning/today}. Embeds the
 * other modules' own {@code api} DTOs directly (docs/milestones/M6-today.md
 * D12) — {@code planning} has no domain of its own to translate them into.
 */
final class PlanningDtos {

    private PlanningDtos() {
    }

    record TodayResponse(
            LocalDate date,
            List<TodoItemView> todos,
            List<ProjectTaskView> dueProjectTasks,
            JournalPromptResponse journalPrompt) {

        static TodayResponse from(PlanningService.TodayView v) {
            return new TodayResponse(v.date(), v.todos(), v.dueProjectTasks(),
                    new JournalPromptResponse(v.hasJournalEntry()));
        }
    }

    record JournalPromptResponse(boolean hasEntry) {
    }

    record PromoteRequest(@NotNull UUID projectTaskId, @NotNull LocalDate day) {
    }
}
