package com.kairon.projects.app;

import java.util.Optional;
import java.util.UUID;

import com.kairon.common.error.ApiException;
import com.kairon.common.security.UserId;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectTask;
import com.kairon.projects.repo.ProjectRepository;
import com.kairon.projects.repo.ProjectTaskRepository;

/**
 * Resolves a task with no project context in the URL — several endpoints
 * (PATCH/DELETE /tasks/{taskId}, the dependency endpoints) carry no
 * {@code projectId}, so the task is looked up globally first and then
 * authorized via its own project. A foreign task's owning project won't
 * resolve either, so both cases surface the same "Task not found." (no
 * existence leak, docs/DESIGN.md §3.2). Shared by {@link ProjectTaskService}
 * and {@link TaskDependencyService} (docs/milestones/M5-gantt-dependencies.md D6).
 */
final class TaskResolution {

    private TaskResolution() {
    }

    record TaskAndProject(ProjectTask task, Project project) {
    }

    static TaskAndProject requireTaskWithProject(
            ProjectTaskRepository tasks, ProjectRepository projects, UserId userId, UUID taskId) {
        return findTaskWithProject(tasks, projects, userId, taskId)
                .orElseThrow(() -> ApiException.notFound("Task not found."));
    }

    /** Same resolution as {@link #requireTaskWithProject}, but empty instead of a 404. */
    static Optional<TaskAndProject> findTaskWithProject(
            ProjectTaskRepository tasks, ProjectRepository projects, UserId userId, UUID taskId) {
        return tasks.findByIdAndDeletedAtIsNull(taskId)
                .flatMap(task -> projects.findByIdAndUserIdAndDeletedAtIsNull(task.getProjectId(), userId.value())
                        .map(project -> new TaskAndProject(task, project)));
    }
}
