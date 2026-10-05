package com.kairon.projects.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kairon.common.security.UserId;

/**
 * The projects module's public port. Other modules depend only on this
 * interface and the DTOs in this package, never on {@code projects.domain} or
 * {@code projects.repo} (ArchUnit-enforced — docs/DESIGN.md §3.1). Implemented
 * by {@code com.kairon.projects.app.ProjectTaskService}.
 *
 * <p>Kept deliberately minimal (D6): sized to what M6's Today screen needs.
 */
public interface ProjectsApi {

    /**
     * Tasks whose {@code [plannedStart, plannedEnd]} window contains {@code day},
     * or that are overdue ({@code plannedEnd < day}) and not {@code DONE}.
     */
    List<ProjectTaskView> dueOrOverdue(UserId userId, LocalDate day);

    /** Resolves a task the caller owns (via its project); 404 if missing or foreign. */
    ProjectTaskView requireTask(UserId userId, UUID taskId);

    /**
     * Open ({@code status <> DONE}) tasks across the user's {@code ACTIVE}/
     * {@code ON_HOLD} projects, with their dates. Added for M8's assistant
     * module (todo-suggestion context — docs/milestones/M8-assistant-foundations.md D3).
     */
    List<ProjectTaskView> openTasksInActiveProjects(UserId userId);

    /**
     * Marks the task DONE, if it exists, isn't already DONE, and is owned by
     * {@code userId} — a no-op (no exception) if the task is missing, foreign,
     * or soft-deleted, since a todo's link may outlive the task it points to.
     * Added for the todo module's optional project-task link: completing a
     * linked todo item syncs the project task's status one-way.
     */
    void completeTaskIfPresent(UserId userId, UUID taskId);

    /**
     * Creates a project, its task tree, and its dependency edges together, in
     * one transaction. Tasks/dependencies reference each other by the
     * caller-supplied string {@code key} (no real ids exist yet);
     * {@code parentKey}/{@code predecessorKey}/{@code successorKey} must each
     * match some task's {@code key} in the same command. Added for M8.5's
     * project-generation feature (docs/milestones/M8.5-project-generation.md D3).
     */
    ProjectView createFromPlan(UserId userId, ProjectPlanCommand command);

    /**
     * Per-project task-completion stats for tasks marked {@code DONE} with
     * {@code updatedAt} in {@code [from, to]} — an approximation, not a true
     * completed-at timestamp (there is no {@code completed_at} column on
     * {@code project_task}). Added for M9's execution-summary aggregation
     * (docs/milestones/M9-execution-summaries.md D4/D5/D15).
     */
    List<ProjectPeriodStats> projectPeriodStats(UserId userId, LocalDate from, LocalDate to);

    /**
     * The user's categories, id + name only — ordered the same as the
     * category module's own list. Added so the assistant module can offer a
     * project-generation prompt the user's existing categories to choose
     * from, without depending on {@code projects.app}/{@code projects.domain}.
     */
    List<ProjectCategorySummary> categories(UserId userId);

    /**
     * Resolves a project the caller owns; 404 if missing, foreign, or
     * soft-deleted. Added for M9.5's AI project-editing feature, so the
     * assistant module can read "the project as it stands today" without
     * depending on {@code projects.app}/{@code projects.domain}.
     */
    ProjectView requireProject(UserId userId, UUID projectId);

    /**
     * All non-deleted tasks of a project the caller owns, unpaginated; 404 if
     * the project itself isn't visible. Added for M9.5's edit-context
     * building — the same reasoning as {@link #requireProject}.
     */
    List<ProjectTaskView> tasksForProject(UserId userId, UUID projectId);

    /**
     * All dependency edges of a project the caller owns; 404 if the project
     * itself isn't visible. Added for M9.5's edit-context building.
     */
    List<DependencyEdge> dependenciesForProject(UserId userId, UUID projectId);

    /**
     * Applies an accepted edit diff to an existing project in one
     * transaction — project field changes, task add/update/remove, sibling
     * reordering, and dependency add/remove. A reference that no longer
     * resolves (a stale task/dependency id, an unknown same-diff key) is
     * dropped and logged, never failing the whole apply. Added for M9.5's
     * AI project-editing feature (docs/milestones/M9.5-ai-project-editing.md
     * D1/D8).
     */
    ProjectView applyProjectEdit(UserId userId, UUID projectId, ProjectEditCommand command);

    record ProjectPeriodStats(
            UUID projectId, String projectName, UUID categoryId, String categoryName,
            long tasksCompleted, BigDecimal estimateHoursCompleted, BigDecimal actualHoursCompleted) {
    }

    record ProjectCategorySummary(UUID id, String name) {
    }

    /**
     * {@code categoryId} and {@code newCategoryName} are mutually exclusive
     * (resolved by the caller before this command is built): a non-null
     * {@code categoryId} assigns the project to that existing category;
     * otherwise a non-blank {@code newCategoryName} creates a new category
     * (falling back to an exact-name match if one was created concurrently)
     * and assigns that; both null leaves the project uncategorized.
     */
    record ProjectPlanCommand(
            String name, String description, String size, LocalDate startDate, LocalDate endDate,
            UUID categoryId, String newCategoryName,
            List<PlannedTask> tasks, List<PlannedDependency> dependencies) {
    }

    record PlannedTask(
            String key, String parentKey, String name, String description, boolean isMilestone,
            LocalDate plannedStart, LocalDate plannedEnd, BigDecimal estimateHours) {
    }

    record PlannedDependency(
            String predecessorKey, String successorKey, String type, Integer lagDays) {
    }

    /** A dependency edge, for cross-module reads (M9.5) — {@code projects.app}'s own {@code TaskDependencyView}
     * stays internal; this is the thin equivalent exposed on the port. */
    record DependencyEdge(UUID id, UUID predecessorTaskId, UUID successorTaskId, String type, int lagDays) {
    }

    /**
     * An edit diff to apply to an existing project (M9.5). {@code projectChanges}
     * is {@code null} when the project's own fields aren't changing;
     * operations reference a task/dependency either by its real existing id
     * (as a string) or, for a task being added in this same command, by a
     * caller-chosen {@code key} — resolved existing-id-first, same convention
     * as {@link ProjectPlanCommand}'s string keys.
     */
    record ProjectEditCommand(
            ProjectFieldChanges projectChanges,
            List<TaskOperation> taskOperations,
            List<DependencyOperation> dependencyOperations,
            List<ReorderOperation> reorderOperations) {
    }

    /** Present only when the project's own fields are changing; {@code categoryId}/{@code newCategoryName}
     * are mutually exclusive, same convention as {@link ProjectPlanCommand}. */
    record ProjectFieldChanges(
            String name, String description, String size, LocalDate startDate, LocalDate endDate,
            UUID categoryId, String newCategoryName) {
    }

    /** {@code op} is {@code ADD}, {@code UPDATE}, or {@code REMOVE}. An {@code UPDATE} carries the task's
     * complete new field set, not a partial patch. */
    record TaskOperation(
            String op, UUID existingTaskId, String key, String parentRef,
            String name, String description, boolean isMilestone,
            LocalDate plannedStart, LocalDate plannedEnd, BigDecimal estimateHours) {
    }

    /** {@code op} is {@code ADD} or {@code REMOVE} — there's no dependency update (M9.5 D4). */
    record DependencyOperation(
            String op, UUID existingDependencyId, String predecessorRef, String successorRef,
            String type, Integer lagDays) {
    }

    /** A full new sibling order for one parent group ({@code parentRef == null} is the top-level group). */
    record ReorderOperation(String parentRef, List<String> orderedRefs) {
    }
}
