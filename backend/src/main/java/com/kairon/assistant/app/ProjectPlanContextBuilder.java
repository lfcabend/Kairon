package com.kairon.assistant.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.api.ProjectsApi.ProjectCategorySummary;

import org.springframework.stereotype.Component;

/**
 * Builds the system prompt and per-request user content for a
 * {@code PROJECT_GENERATION} run. Lighter than {@link TodoSuggestionContextBuilder}
 * — the only cross-module aggregation is the user's existing project
 * categories (docs/milestones/M8.5-project-generation.md §4.4), fetched so
 * the model can match an existing one by name instead of always proposing a
 * new one. The system prompt is a fixed constant — no per-request data — so
 * it stays cacheable, same reasoning as M8 §4.5.
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
              constraint, not for every consecutive pair of tasks.
            - Suggest a categoryName: match one of the user's existing categories
              (listed below) exactly by name if one clearly fits; otherwise propose a
              short, reusable new category name; leave it null if nothing fits.""";

    private final Clock clock;
    private final ProjectsApi projectsApi;

    ProjectPlanContextBuilder(Clock clock, ProjectsApi projectsApi) {
        this.clock = clock;
        this.projectsApi = projectsApi;
    }

    record Context(String systemPrompt, String userContent, Map<String, Object> inputSnapshot,
            List<ProjectCategorySummary> categories) {
    }

    Context build(UserId userId, String description, LocalDate startDate, LocalDate targetDeadline) {
        List<ProjectCategorySummary> categories = projectsApi.categories(userId);

        StringBuilder sb = new StringBuilder();
        sb.append("Plan a project starting ").append(startDate);
        if (targetDeadline != null) {
            sb.append(", targeting completion by ").append(targetDeadline);
        }
        sb.append(".\nToday is ").append(LocalDate.now(clock)).append(".\n");
        if (categories.isEmpty()) {
            sb.append("\nThe user has no existing project categories yet.\n");
        } else {
            sb.append("\nThe user's existing project categories: ")
                    .append(categories.stream().map(ProjectCategorySummary::name)
                            .collect(Collectors.joining(", ")))
                    .append(".\n");
        }
        sb.append("\nDescription:\n").append(description);
        String userContent = sb.toString();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("systemPrompt", SYSTEM_PROMPT);
        snapshot.put("userContent", userContent);
        return new Context(SYSTEM_PROMPT, userContent, snapshot, categories);
    }
}
