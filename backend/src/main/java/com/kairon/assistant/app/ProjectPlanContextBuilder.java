package com.kairon.assistant.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Builds the system prompt and per-request user content for a
 * {@code PROJECT_GENERATION} run. Much lighter than {@link TodoSuggestionContextBuilder}
 * — this feature's only input is what the user typed, not cross-module
 * aggregation (docs/milestones/M8.5-project-generation.md §4.4). The system
 * prompt is a fixed constant — no per-request data — so it stays cacheable,
 * same reasoning as M8 §4.5.
 */
@Component
class ProjectPlanContextBuilder {

    // Kept in sync with docs/milestones/M8.5-project-generation.md §4.4 — that
    // doc is the source of truth for *why* this prompt is shaped this way.
    private static final String SYSTEM_PROMPT = """
            You are Kairon's project-planning assistant. Given a free-text project
            description, propose a complete project: a short task tree, milestones, and
            dependencies between tasks.

            Rules:
            - Keep tasks small and concrete — something a person can plan around, not a
              vague phase. Prefer more small tasks over fewer vague ones, but don't
              exceed a sensible task count for the described scope.
            - At most one level of nesting: a subtask's parent must itself be top-level.
            - A milestone is a zero-duration marker (plannedStart == plannedEnd) for a
              meaningful checkpoint — not every task's own finish date.
            - Every date must fall within the project's overall start/end range stated
              in the user message. Never invent urgency or a deadline that isn't implied
              by the description or the stated range.
            - Order and space planned dates sensibly given task dependencies — a
              successor's plannedStart should not fall before its FS predecessor's
              plannedEnd.
            - Set dependencies only where the description implies a real ordering
              constraint, not for every consecutive pair of tasks.""";

    private final Clock clock;

    ProjectPlanContextBuilder(Clock clock) {
        this.clock = clock;
    }

    record Context(String systemPrompt, String userContent, Map<String, Object> inputSnapshot) {
    }

    Context build(String description, LocalDate startDate, LocalDate targetDeadline) {
        StringBuilder sb = new StringBuilder();
        sb.append("Plan a project starting ").append(startDate);
        if (targetDeadline != null) {
            sb.append(", targeting completion by ").append(targetDeadline);
        }
        sb.append(".\nToday is ").append(LocalDate.now(clock)).append(".\n\nDescription:\n").append(description);
        String userContent = sb.toString();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("systemPrompt", SYSTEM_PROMPT);
        snapshot.put("userContent", userContent);
        return new Context(SYSTEM_PROMPT, userContent, snapshot);
    }
}
