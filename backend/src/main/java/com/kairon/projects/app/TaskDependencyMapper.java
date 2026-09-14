package com.kairon.projects.app;

import com.kairon.projects.domain.TaskDependency;

/** Hand-rolled entity -> view mapping (same convention as {@link ProjectTaskMapper}). */
final class TaskDependencyMapper {

    private TaskDependencyMapper() {
    }

    static TaskDependencyView toView(TaskDependency d, boolean violatesConstraint) {
        return new TaskDependencyView(
                d.getId(),
                d.getPredecessorId(),
                d.getSuccessorId(),
                d.getType().name(),
                d.getLagDays(),
                violatesConstraint,
                d.getCreatedAt());
    }
}
