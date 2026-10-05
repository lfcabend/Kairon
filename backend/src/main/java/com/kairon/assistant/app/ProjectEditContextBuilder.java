package com.kairon.assistant.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.kairon.common.security.UserId;
import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.api.ProjectView;
import com.kairon.projects.api.ProjectsApi;
import com.kairon.projects.api.ProjectsApi.DependencyEdge;
import com.kairon.projects.api.ProjectsApi.ProjectCategorySummary;

import org.springframework.stereotype.Component;

/**
 * Builds the system prompt and per-request user content for a
 * {@code PROJECT_EDIT} run: unlike {@link ProjectPlanContextBuilder}, this
 * feature's context is "the project as it stands today" — its fields, its
 * full task tree with real ids, and its dependency edges — so the model can
 * reference existing rows by id and propose a diff against them
 * (docs/milestones/M9.5-ai-project-editing.md §4.4).
 */
@Component
class ProjectEditContextBuilder {

    // Kept in sync with docs/milestones/M9.5-ai-project-editing.md §4.4.
    private static final String SYSTEM_PROMPT = """
            You are Kairon's project-editing assistant. You're given a project's current
            state — its fields, its task tree, and its dependency edges, each with its
            real id — plus a free-text description of a change the user wants. Propose a
            diff: only the operations that represent an actual change.

            Rules:
            - Only emit an operation for something that is actually changing. Don't
              restate tasks, dependencies, or project fields that aren't affected.
            - An UPDATE operation must carry that task's complete new field set,
              including fields copied forward unchanged from its current state — never a
              partial patch.
            - Reference an existing task by the real id shown in its current state.
              Reference a task you're adding in this same response by a short key you
              choose (e.g. "n1") — never invent an id for something that doesn't exist
              yet.
            - At most one level of nesting, same as the project's own existing tasks.
            - A milestone is a zero-duration marker (plannedStart == plannedEnd).
            - To change a dependency's type or lag, remove the old edge and add the new
              one — there's no update for a dependency.
            - Only reorder a sibling group the user's description actually implies
              reordering for.
            - Suggest a project categoryName the same way project generation does: match
              an existing category by name if one clearly fits, propose a short new one
              otherwise, or echo the project's current category name if it isn't
              changing.""";

    private final ProjectsApi projectsApi;

    ProjectEditContextBuilder(ProjectsApi projectsApi) {
        this.projectsApi = projectsApi;
    }

    record Context(String systemPrompt, String userContent, Map<String, Object> inputSnapshot,
            List<ProjectCategorySummary> categories) {
    }

    /** {@code projectsApi.requireProject} 404s here if {@code projectId} isn't the caller's (D10). */
    Context build(UserId userId, UUID projectId, String description) {
        ProjectView project = projectsApi.requireProject(userId, projectId);
        List<ProjectTaskView> tasks = projectsApi.tasksForProject(userId, projectId);
        List<DependencyEdge> dependencies = projectsApi.dependenciesForProject(userId, projectId);
        List<ProjectCategorySummary> categories = projectsApi.categories(userId);

        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(project.name()).append('\n');
        if (project.description() != null) {
            sb.append("Description: ").append(project.description()).append('\n');
        }
        sb.append("Dates: ").append(project.startDate()).append(" to ").append(project.endDate()).append('\n');
        if (project.size() != null) {
            sb.append("Size: ").append(project.size()).append('\n');
        }
        String currentCategoryName = categories.stream()
                .filter(c -> c.id().equals(project.categoryId()))
                .map(ProjectCategorySummary::name)
                .findFirst()
                .orElse(null);
        sb.append("Current category: ").append(currentCategoryName != null ? currentCategoryName : "(none)")
                .append('\n');

        sb.append("\nCurrent tasks:\n");
        if (tasks.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (ProjectTaskView t : tasks) {
                sb.append("- id=").append(t.id());
                if (t.parentTaskId() != null) {
                    sb.append(" parent=").append(t.parentTaskId());
                }
                sb.append(" name=\"").append(t.name()).append('"');
                if (t.isMilestone()) {
                    sb.append(" [milestone]");
                }
                sb.append(" start=").append(t.plannedStart()).append(" end=").append(t.plannedEnd());
                if (t.estimateHours() != null) {
                    sb.append(" estimateHours=").append(t.estimateHours());
                }
                sb.append('\n');
            }
        }

        sb.append("\nCurrent dependencies:\n");
        if (dependencies.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (DependencyEdge d : dependencies) {
                sb.append("- id=").append(d.id())
                        .append(" predecessor=").append(d.predecessorTaskId())
                        .append(" successor=").append(d.successorTaskId())
                        .append(" type=").append(d.type())
                        .append(" lagDays=").append(d.lagDays())
                        .append('\n');
            }
        }

        if (categories.isEmpty()) {
            sb.append("\nThe user has no existing project categories yet.\n");
        } else {
            sb.append("\nThe user's existing project categories: ")
                    .append(categories.stream().map(ProjectCategorySummary::name)
                            .collect(Collectors.joining(", ")))
                    .append(".\n");
        }

        sb.append("\nDesired change:\n").append(description);
        String userContent = sb.toString();

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("systemPrompt", SYSTEM_PROMPT);
        snapshot.put("userContent", userContent);
        return new Context(SYSTEM_PROMPT, userContent, snapshot, categories);
    }
}
