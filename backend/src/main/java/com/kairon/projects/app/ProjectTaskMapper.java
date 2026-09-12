package com.kairon.projects.app;

import com.kairon.projects.api.ProjectTaskView;
import com.kairon.projects.domain.Project;
import com.kairon.projects.domain.ProjectTask;

/**
 * Hand-rolled entity -> view mapping (D7). Denormalizes the owning project's
 * name/color onto the view (Q2) — the caller always already has the project
 * in hand (resolved for authorization) except for the cross-project
 * {@code dueOrOverdue} query, which passes each task's own project.
 */
final class ProjectTaskMapper {

    private ProjectTaskMapper() {
    }

    static ProjectTaskView toView(ProjectTask task, Project project) {
        return new ProjectTaskView(
                task.getId(),
                task.getProjectId(),
                project.getName(),
                project.getColor(),
                task.getParentTaskId(),
                task.getName(),
                task.getDescription(),
                task.getStatus().name(),
                task.isMilestone(),
                task.getPlannedStart(),
                task.getPlannedEnd(),
                task.getEstimateHours(),
                task.getActualHours(),
                task.getProgressPercent(),
                task.getPosition(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getVersion());
    }
}
