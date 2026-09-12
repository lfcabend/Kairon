package com.kairon.projects.app;

import com.kairon.projects.api.ProjectView;
import com.kairon.projects.domain.Project;

/** Hand-rolled entity -> view mapping (D7), following the {@code todo}/{@code journal} precedent. */
final class ProjectMapper {

    private ProjectMapper() {
    }

    static ProjectView toView(Project project) {
        return new ProjectView(
                project.getId(),
                project.getCategoryId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getSize() == null ? null : project.getSize().name(),
                project.getPriorityRank(),
                project.getColor(),
                project.getStartDate(),
                project.getEndDate(),
                project.getActualStart(),
                project.getActualEnd(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getVersion());
    }
}
