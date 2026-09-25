package com.kairon.projects.api;

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
}
